package com.xiao.pocketir.data

import android.content.Context
import com.xiao.pocketir.model.DeviceMeta
import com.xiao.pocketir.model.DeviceType
import com.xiao.pocketir.model.FlipperCommand
import com.xiao.pocketir.model.FlipperSignalType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object BookmarkManager {
    private const val FILE_NAME = "bookmarks.json"
    private const val TEMP_FILE_NAME = "bookmarks.json.tmp"

    fun load(context: Context): List<DeviceMeta> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()

        return runCatching {
            val array = JSONArray(file.readText())
            List(array.length()) { index ->
                parseDevice(array.getJSONObject(index))
            }
        }.getOrElse { emptyList() }
    }

    fun save(context: Context, devices: List<DeviceMeta>) {
        val array = JSONArray()
        devices.forEach { device -> array.put(serializeDevice(device)) }

        val target = File(context.filesDir, FILE_NAME)
        val temp = File(context.filesDir, TEMP_FILE_NAME)
        temp.writeText(array.toString())
        if (target.exists()) {
            target.delete()
        }
        temp.renameTo(target)
    }

    fun upsert(context: Context, device: DeviceMeta): List<DeviceMeta> {
        val current = load(context)
        val next = current.filterNot { sameDevice(it, device) } + device
        save(context, next)
        return next
    }

    fun remove(context: Context, device: DeviceMeta): List<DeviceMeta> {
        val next = load(context).filterNot { sameDevice(it, device) }
        save(context, next)
        return next
    }

    private fun sameDevice(left: DeviceMeta, right: DeviceMeta): Boolean {
        return left.category == right.category &&
            left.brand == right.brand &&
            left.model == right.model &&
            left.sourcePath == right.sourcePath
    }

    private fun parseDevice(json: JSONObject): DeviceMeta {
        val commands = json.optJSONArray("embeddedCommands")?.let(::parseCommands).orEmpty()
        return DeviceMeta(
            category = json.getString("category"),
            brand = json.getString("brand"),
            model = json.getString("model"),
            sourcePath = json.getString("sourcePath"),
            type = DeviceType.valueOf(json.optString("type", DeviceType.GENERIC.name)),
            embeddedCommands = commands
        )
    }

    private fun serializeDevice(device: DeviceMeta): JSONObject {
        return JSONObject().apply {
            put("category", device.category)
            put("brand", device.brand)
            put("model", device.model)
            put("sourcePath", device.sourcePath)
            put("type", device.type.name)
            if (device.embeddedCommands.isNotEmpty()) {
                put("embeddedCommands", serializeCommands(device.embeddedCommands))
            }
        }
    }

    private fun parseCommands(array: JSONArray): List<FlipperCommand> {
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            FlipperCommand(
                name = item.getString("name"),
                type = FlipperSignalType.valueOf(item.optString("type", FlipperSignalType.PARSED.name)),
                protocol = item.optString("protocol").ifBlank { null },
                address = item.optString("address").ifBlank { null },
                command = item.optString("command").ifBlank { null },
                frequency = item.optInt("frequency").takeIf { it > 0 },
                dutyCycle = item.optDouble("dutyCycle", -1.0).takeIf { it >= 0.0 }?.toFloat(),
                rawData = item.optJSONArray("rawData")?.let { rawArray ->
                    List(rawArray.length()) { rawIndex -> rawArray.getInt(rawIndex) }
                }.orEmpty()
            )
        }
    }

    private fun serializeCommands(commands: List<FlipperCommand>): JSONArray {
        return JSONArray().apply {
            commands.forEach { command ->
                put(
                    JSONObject().apply {
                        put("name", command.name)
                        put("type", command.type.name)
                        command.protocol?.let { put("protocol", it) }
                        command.address?.let { put("address", it) }
                        command.command?.let { put("command", it) }
                        command.frequency?.let { put("frequency", it) }
                        command.dutyCycle?.let { put("dutyCycle", it) }
                        if (command.rawData.isNotEmpty()) {
                            put(
                                "rawData",
                                JSONArray().apply {
                                    command.rawData.forEach(::put)
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}
