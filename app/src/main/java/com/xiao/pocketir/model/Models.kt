package com.xiao.pocketir.model

enum class DeviceType {
    GENERIC,
    AC
}

enum class FlipperSignalType {
    PARSED,
    RAW
}

data class FlipperCommand(
    val name: String,
    val type: FlipperSignalType = FlipperSignalType.PARSED,
    val protocol: String? = null,
    val address: String? = null,
    val command: String? = null,
    val frequency: Int? = null,
    val dutyCycle: Float? = null,
    val rawData: List<Int> = emptyList()
) {
    val isParsed: Boolean
        get() = type == FlipperSignalType.PARSED

    val isRaw: Boolean
        get() = type == FlipperSignalType.RAW
}

data class DeviceMeta(
    val category: String,
    val brand: String,
    val model: String,
    val sourcePath: String,
    val type: DeviceType = DeviceType.GENERIC,
    val embeddedCommands: List<FlipperCommand> = emptyList()
) {
    val isCustom: Boolean
        get() = sourcePath.startsWith("bookmark://")
}
