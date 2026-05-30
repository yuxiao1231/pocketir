package com.xiao.pocketir.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.xiao.pocketir.i18n.I18n
import com.xiao.pocketir.model.DeviceMeta
import com.xiao.pocketir.model.DeviceType
import com.xiao.pocketir.model.FlipperCommand
import com.xiao.pocketir.model.FlipperSignalType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object IrRepository {
    private const val TAG = "IrRepository"
    private val libraryCache = linkedMapOf<String, Library>()

    @Synchronized
    fun open(rootPath: String): Library {
        return libraryCache.getOrPut(rootPath) { Library(rootPath) }
    }

    fun getCommands(device: DeviceMeta): List<FlipperCommand> {
        if (device.embeddedCommands.isNotEmpty()) {
            return device.embeddedCommands
        }

        val file = File(device.sourcePath)
        if (!file.exists()) return emptyList()

        return when {
            file.isFile && file.extension.equals("db", ignoreCase = true) -> getCommandsFromDb(device)
            file.isFile && file.extension.equals("ir", ignoreCase = true) -> parseFlipperFile(device.sourcePath)
            else -> emptyList()
        }
    }

    class Library internal constructor(private val rootPath: String) {
        private var searchIndex: SearchIndex? = null

        // 异步初始化，防止 HyperOS 等环境在 UI 线程建表导致闪退
        suspend fun ensureIndexed() = withContext(Dispatchers.IO) {
            if (searchIndex == null) {
                searchIndex = buildSearchIndex(rootPath)
            }
        }

        fun getDeviceList(): List<DeviceMeta> = searchIndex?.allDevices ?: emptyList()

        fun search(query: String): List<DeviceMeta> = searchIndex?.search(query) ?: emptyList()
    }

    private data class SearchIndex(
        val db: SQLiteDatabase,
        val devicesByKey: LinkedHashMap<String, DeviceMeta>
    ) {
        val allDevices: List<DeviceMeta> = devicesByKey.values.sortedWith(
            compareBy(DeviceMeta::category, DeviceMeta::brand, DeviceMeta::model)
        )

        fun search(query: String): List<DeviceMeta> {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return allDevices

            val deviceKeys = linkedSetOf<String>()
            buildFtsQuery(trimmed)?.let { matchQuery ->
                runCatching {
                    db.rawQuery(
                        """
                        SELECT device_key, MIN(rank)
                        FROM (
                            SELECT device_key, bm25(ir_search) AS rank
                            FROM ir_search
                            WHERE ir_search MATCH ?
                        )
                        GROUP BY device_key
                        ORDER BY MIN(rank)
                        """.trimIndent(),
                        arrayOf(matchQuery)
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            deviceKeys += cursor.getString(0)
                        }
                    }
                }.onFailure {
                    Log.w(TAG, "FTS query failed for '$trimmed'", it)
                }
            }

            val likeQuery = "%${escapeLike(trimmed)}%"
            db.rawQuery(
                """
                SELECT DISTINCT device_key
                FROM ir_search
                WHERE brand LIKE ? ESCAPE '\'
                   OR model LIKE ? ESCAPE '\'
                   OR category LIKE ? ESCAPE '\'
                   OR btn_name LIKE ? ESCAPE '\'
                ORDER BY brand, model
                """.trimIndent(),
                arrayOf(likeQuery, likeQuery, likeQuery, likeQuery)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    deviceKeys += cursor.getString(0)
                }
            }

            if (deviceKeys.isEmpty()) {
                return allDevices.filter { device ->
                    listOf(device.category, device.brand, device.model).any {
                        it.contains(trimmed, ignoreCase = true)
                    }
                }
            }

            return deviceKeys.mapNotNull(devicesByKey::get)
        }
    }

    private fun buildSearchIndex(rootPath: String): SearchIndex {
        val file = File(rootPath)
        val searchDb = SQLiteDatabase.create(null)
        val devices = linkedMapOf<String, DeviceMeta>()

        createSearchTable(searchDb)

        if (!file.exists()) {
            return SearchIndex(searchDb, devices)
        }

        when {
            file.isFile && file.extension.equals("db", ignoreCase = true) -> {
                indexDbSource(file.absolutePath, searchDb, devices)
            }

            file.isDirectory -> {
                indexDirectorySource(file, searchDb, devices)
            }
        }

        return SearchIndex(searchDb, devices)
    }

    private fun createSearchTable(db: SQLiteDatabase) {
        runCatching {
            // 第一梯队：尝试功能最全的 FTS5
            db.execSQL(
                """
                CREATE VIRTUAL TABLE ir_search USING fts5(
                    device_key UNINDEXED,
                    category,
                    brand,
                    model,
                    btn_name,
                    tokenize='unicode61'
                )
                """.trimIndent()
            )
        }.onFailure { e1 ->
            Log.w(TAG, "System doesn't support FTS5 or unicode61, fallback to FTS4", e1)
            
            runCatching {
                // 第二梯队：降级尝试 FTS4
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE ir_search USING fts4(
                        device_key,
                        category,
                        brand,
                        model,
                        btn_name
                    )
                    """.trimIndent()
                )
            }.onFailure { e2 ->
                Log.e(TAG, "FTS entirely unsupported, fallback to standard table", e2)
                
                // 第三梯队：终极保底，建普通表
                db.execSQL(
                    """
                    CREATE TABLE ir_search(
                        device_key TEXT,
                        category TEXT,
                        brand TEXT,
                        model TEXT,
                        btn_name TEXT
                    )
                    """.trimIndent()
                )
            }
        }
    }

    private fun indexDbSource(
        dbPath: String,
        searchDb: SQLiteDatabase,
        devices: LinkedHashMap<String, DeviceMeta>
    ) {
        try {
            SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(
                    """
                    SELECT category, brand, model, btn_name
                    FROM ir_commands
                    ORDER BY category, brand, model, btn_name
                    """.trimIndent(),
                    null
                ).use { cursor ->
                    searchDb.beginTransaction()
                    try {
                        while (cursor.moveToNext()) {
                            val meta = buildDeviceMeta(
                                category = cursor.getString(0),
                                brand = cursor.getString(1),
                                model = cursor.getString(2),
                                sourcePath = dbPath
                            )
                            val key = keyOf(meta)
                            devices.putIfAbsent(key, meta)
                            insertSearchRow(searchDb, key, meta, cursor.getString(3))
                        }
                        searchDb.setTransactionSuccessful()
                    } finally {
                        searchDb.endTransaction()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to index IR database", e)
        }
    }

    private fun indexDirectorySource(
        dir: File,
        searchDb: SQLiteDatabase,
        devices: LinkedHashMap<String, DeviceMeta>
    ) {
        searchDb.beginTransaction()
        try {
            dir.walkTopDown()
                .filter { it.isFile && it.extension.equals("ir", ignoreCase = true) }
                .forEach { file ->
                    val meta = buildDeviceMetaFromFile(dir, file)
                    val key = keyOf(meta)
                    devices.putIfAbsent(key, meta)

                    val commands = parseFlipperFile(file.absolutePath)
                    if (commands.isEmpty()) {
                        insertSearchRow(searchDb, key, meta, "")
                    } else {
                        commands.forEach { command ->
                            insertSearchRow(searchDb, key, meta, command.name)
                        }
                    }
                }
            searchDb.setTransactionSuccessful()
        } finally {
            searchDb.endTransaction()
        }
    }

    private fun insertSearchRow(
        db: SQLiteDatabase,
        deviceKey: String,
        meta: DeviceMeta,
        buttonName: String
    ) {
        val values = ContentValues().apply {
            put("device_key", deviceKey)
            put("category", meta.category)
            put("brand", meta.brand)
            put("model", meta.model)
            put("btn_name", buttonName)
        }
        db.insert("ir_search", null, values)
    }

    private fun getCommandsFromDb(device: DeviceMeta): List<FlipperCommand> {
        val commands = mutableListOf<FlipperCommand>()
        try {
            SQLiteDatabase.openDatabase(device.sourcePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(
                    """
                    SELECT btn_name, type, protocol, address, command, frequency, raw_data
                    FROM ir_commands
                    WHERE category = ? AND brand = ? AND model = ?
                    ORDER BY btn_name
                    """.trimIndent(),
                    arrayOf(device.category, device.brand, device.model)
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val typeStr = cursor.getString(1) ?: "parsed"
                        val type = if (typeStr.equals("raw", ignoreCase = true)) FlipperSignalType.RAW else FlipperSignalType.PARSED
                        
                        val rawDataList = if (type == FlipperSignalType.RAW) {
                            cursor.getString(6)
                                ?.split(Regex("\\s+"))
                                ?.mapNotNull { it.toIntOrNull() }
                                .orEmpty()
                        } else {
                            emptyList()
                        }

                        commands += FlipperCommand(
                            name = cursor.getString(0),
                            type = type,
                            protocol = cursor.getString(2),
                            address = cursor.getString(3),
                            command = cursor.getString(4),
                            frequency = if (cursor.isNull(5)) null else cursor.getInt(5),
                            rawData = rawDataList
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract commands from DB", e)
        }
        return commands
    }

    private fun parseFlipperFile(path: String): List<FlipperCommand> {
        val commands = mutableListOf<FlipperCommand>()
        val fields = linkedMapOf<String, String>()

        fun flush() {
            toFlipperCommand(fields)?.let(commands::add)
            fields.clear()
        }

        File(path).forEachLine { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit
                line == "#" -> flush()
                line.startsWith("#") -> Unit
                !line.contains(':') -> Unit
                else -> {
                    val key = line.substringBefore(':').trim()
                    val value = line.substringAfter(':').trim()
                    fields[key] = value
                    if (key == "command" || key == "data") {
                        flush()
                    }
                }
            }
        }

        flush()
        return commands
    }

    private fun toFlipperCommand(fields: Map<String, String>): FlipperCommand? {
        val name = fields["name"] ?: return null
        val type = when (fields["type"]?.lowercase()) {
            "raw" -> FlipperSignalType.RAW
            else -> FlipperSignalType.PARSED
        }

        return when (type) {
            FlipperSignalType.PARSED -> {
                val protocol = fields["protocol"] ?: return null
                val address = fields["address"] ?: return null
                val command = fields["command"] ?: return null
                FlipperCommand(
                    name = name,
                    type = type,
                    protocol = protocol,
                    address = address,
                    command = command
                )
            }

            FlipperSignalType.RAW -> {
                val data = fields["data"]
                    ?.split(Regex("\\s+"))
                    ?.mapNotNull { it.toIntOrNull() }
                    .orEmpty()
                if (data.isEmpty()) return null

                FlipperCommand(
                    name = name,
                    type = type,
                    frequency = fields["frequency"]?.toIntOrNull(),
                    dutyCycle = fields["duty_cycle"]?.toFloatOrNull(),
                    rawData = data
                )
            }
        }
    }

    private fun buildDeviceMetaFromFile(rootDir: File, file: File): DeviceMeta {
        val parent = file.parentFile
        val grandParent = parent?.parentFile
        val brand = parent?.name ?: I18n.t("device_brand_unknown")
        val category = if (grandParent != null && grandParent.absolutePath != rootDir.absolutePath) {
            grandParent.name
        } else {
            I18n.t("device_category_general")
        }

        return buildDeviceMeta(
            category = category,
            brand = brand,
            model = file.nameWithoutExtension,
            sourcePath = file.absolutePath
        )
    }

    private fun buildDeviceMeta(
        category: String,
        brand: String,
        model: String,
        sourcePath: String,
        embeddedCommands: List<FlipperCommand> = emptyList()
    ): DeviceMeta {
        return DeviceMeta(
            category = category,
            brand = brand,
            model = model,
            sourcePath = sourcePath,
            type = inferDeviceType(category, brand, model),
            embeddedCommands = embeddedCommands
        )
    }

    private fun keyOf(device: DeviceMeta): String {
        return "${device.sourcePath}|${device.category}|${device.brand}|${device.model}"
    }

    private fun buildFtsQuery(query: String): String? {
        val tokens = Regex("[\\p{L}\\p{N}_]+")
            .findAll(query)
            .map { it.value.lowercase() }
            .filter { it.isNotBlank() }
            .toList()

        if (tokens.isEmpty()) return null
        return tokens.joinToString(" AND ") { "$it*" }
    }

    private fun escapeLike(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }

    private fun inferDeviceType(category: String, brand: String, model: String): DeviceType {
        val haystack = "$category $brand $model".lowercase()
        return if (
            haystack.contains("ac") ||
            haystack.contains("air") ||
            haystack.contains("conditioner") ||
            haystack.contains("hvac") ||
            haystack.contains("空调")
        ) {
            DeviceType.AC
        } else {
            DeviceType.GENERIC
        }
    }
}
