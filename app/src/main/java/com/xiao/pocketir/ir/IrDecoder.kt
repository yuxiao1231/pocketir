package com.xiao.pocketir.ir

import android.util.Log
import com.xiao.pocketir.model.FlipperCommand
import com.xiao.pocketir.model.FlipperSignalType

object IrDecoder {
    private const val TAG = "IrDecoder"
    private const val DEFAULT_RAW_FREQUENCY = 38_000
    private const val FREQUENCY_NEC = 38_000
    private const val FREQUENCY_SIRC = 40_000 // 🌟 Sony 专属 40kHz 载波

    fun normalizeFlipperHex(input: String, requiredBytes: Int = 4): String? {
        val stripped = input.trim()
        if (stripped.isEmpty()) return null

        val tokens = stripped.split(Regex("\\s+")).filter { it.isNotBlank() }
        val bytes = when {
            tokens.size == requiredBytes && tokens.all { it.matches(Regex("[0-9A-Fa-f]{2}")) } -> {
                tokens.map { it.uppercase() }
            }
            stripped.matches(Regex("[0-9A-Fa-f]{${requiredBytes * 2}}")) -> {
                stripped.chunked(2).map { it.uppercase() }
            }
            else -> return null
        }
        return bytes.joinToString(" ")
    }

    fun buildPattern(command: FlipperCommand): IntArray? {
        return when (command.type) {
            FlipperSignalType.RAW -> command.rawData.takeIf { it.isNotEmpty() }?.toIntArray()
            FlipperSignalType.PARSED -> buildParsedPattern(command)
        }
    }

    fun resolveFrequency(command: FlipperCommand): Int {
        return when (command.type) {
            FlipperSignalType.RAW -> command.frequency ?: DEFAULT_RAW_FREQUENCY
            FlipperSignalType.PARSED -> {
                // 🌟 识别 Sony 协议并切换频率
                if (command.protocol?.uppercase()?.startsWith("SIRC") == true) FREQUENCY_SIRC else FREQUENCY_NEC
            }
        }
    }

    private fun buildParsedPattern(command: FlipperCommand): IntArray? {
        val protocol = command.protocol?.uppercase() ?: return null
        val address = normalizeFlipperHex(command.address ?: return null) ?: return null
        val payload = normalizeFlipperHex(command.command ?: return null) ?: return null

        return when (protocol) {
            "NEC" -> parseNecBytes(address, payload)?.let(::buildNecPattern)
            "NECEXT" -> parseNecExtBytes(address, payload)?.let(::buildNecPattern)
            // 🌟 加入 Sony SIRC 家族支持
            "SIRC" -> buildSircPattern(address, payload, 12)
            "SIRC15" -> buildSircPattern(address, payload, 15)
            "SIRC20" -> buildSircPattern(address, payload, 20)
            else -> {
                Log.w(TAG, "Unsupported parsed protocol: $protocol")
                null
            }
        }
    }

    // ==========================================
    // NEC 协议组
    // ==========================================
    private fun parseNecBytes(addressStr: String, commandStr: String): IntArray? {
        return try {
            val addressByte = parseBytes(addressStr).first().toInt() and 0xFF
            val commandByte = parseBytes(commandStr).first().toInt() and 0xFF
            intArrayOf(addressByte, addressByte.inv() and 0xFF, commandByte, commandByte.inv() and 0xFF)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseNecExtBytes(addressStr: String, commandStr: String): IntArray? {
        return try {
            val addressBytes = parseBytes(addressStr)
            val commandBytes = parseBytes(commandStr)
            intArrayOf(
                addressBytes[0].toInt() and 0xFF,
                addressBytes[1].toInt() and 0xFF,
                commandBytes[0].toInt() and 0xFF,
                commandBytes[1].toInt() and 0xFF
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun buildNecPattern(bytes: IntArray): IntArray {
        val frame = mutableListOf<Int>()
        // 1. 发送完整的初始帧 (9000 mark + 4500 space)
        frame += 9000
        frame += 4500

        bytes.forEach { byte ->
            repeat(8) { bitIndex ->
                val bit = (byte shr bitIndex) and 0x01
                frame += 560
                frame += if (bit == 1) 1690 else 560
            }
        }
        frame += 560
        frame += 40_000 // 帧与帧之间的静默等待期

        // 🌟 2. 构造标准 NEC 重复码 (Repeat Code: 9000 mark + 2250 space + 560 mark)
        val repeatCode = listOf(9000, 2250, 560, 40_000)

        // 组装发射序列：1次完整帧 + 1次重复码（模拟真实遥控器按压时长）
        val pattern = mutableListOf<Int>()
        pattern.addAll(frame)
        pattern.addAll(repeatCode)
        
        return pattern.toIntArray()
    }
    
    // ==========================================
    // 🌟 SIRC 协议组 (Sony)
    // ==========================================
    private fun buildSircPattern(addressStr: String, commandStr: String, bitLength: Int): IntArray? {
        return try {
            val addressBytes = parseBytes(addressStr)
            val commandBytes = parseBytes(commandStr)

            // Flipper 是小端序存储的，拼凑出完整的 Int
            var addrInt = 0
            addressBytes.forEachIndexed { i, b -> addrInt = addrInt or ((b.toInt() and 0xFF) shl (8 * i)) }
            var cmdInt = 0
            commandBytes.forEachIndexed { i, b -> cmdInt = cmdInt or ((b.toInt() and 0xFF) shl (8 * i)) }

            val frame = mutableListOf<Int>()
            frame += 2400
            frame += 600

            // SIRC 规定先发 7 bit 的 Command
            repeat(7) { bitIndex ->
                val bit = (cmdInt shr bitIndex) and 0x01
                frame += if (bit == 1) 1200 else 600
                frame += 600
            }

            // 再发剩下的 Address bits
            val addrBits = bitLength - 7
            repeat(addrBits) { bitIndex ->
                val bit = (addrInt shr bitIndex) and 0x01
                frame += if (bit == 1) 1200 else 600
                frame += 600
            }

            frame[frame.lastIndex] = 40_000 // 补足帧尾静默期

            // 🌟 Sony 电视铁律：必须至少连续收到 3 帧才会响应！
            val pattern = mutableListOf<Int>()
            repeat(3) { pattern.addAll(frame) }
            pattern.toIntArray()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to build SIRC pattern", e)
            null
        }
    }

    private fun parseBytes(value: String): ByteArray {
        return normalizeFlipperHex(value)
            ?.split(" ")
            ?.map { it.toInt(16).toByte() }
            ?.toByteArray()
            ?: error("Invalid hex payload")
    }
}