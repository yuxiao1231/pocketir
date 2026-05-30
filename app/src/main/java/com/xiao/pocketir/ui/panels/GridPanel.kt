package com.xiao.pocketir.ui.panels

import android.content.Context
import android.hardware.ConsumerIrManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiao.pocketir.data.IrRepository
import com.xiao.pocketir.i18n.I18n
import com.xiao.pocketir.ir.HapticEngine
import com.xiao.pocketir.ir.IrTransmitter
import com.xiao.pocketir.model.DeviceMeta
import com.xiao.pocketir.model.FlipperCommand
import com.xiao.pocketir.ui.components.SkeuoButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun GridPanel(
    irManager: ConsumerIrManager?,
    device: DeviceMeta
) {
    val context = LocalContext.current
    var commands by remember(device) { mutableStateOf<List<FlipperCommand>>(emptyList()) }
    var isLoading by remember(device) { mutableStateOf(true) }

    LaunchedEffect(device) {
        isLoading = true
        commands = withContext(Dispatchers.IO) { IrRepository.getCommands(device) }
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (commands.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = I18n.t("panel_no_commands"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val layout = remember(commands) { PanelCommandLayout.from(commands) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (layout.powerCommands.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                layout.powerCommands.forEach { indexed ->
                    PowerCommandButton(
                        context = context,
                        irManager = irManager,
                        command = indexed.command
                    )
                }
            }
        }

        if (layout.hasControlRow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                layout.leftRocker?.let { rocker ->
                    Rocker(
                        context = context,
                        irManager = irManager,
                        label = rocker.label(),
                        upCommand = rocker.up?.command,
                        downCommand = rocker.down?.command
                    )
                } ?: Spacer(modifier = Modifier.width(72.dp))

                if (layout.hasDPad) {
                    DPad(
                        context = context,
                        irManager = irManager,
                        upCommand = layout.upCommand?.command,
                        downCommand = layout.downCommand?.command,
                        leftCommand = layout.leftCommand?.command,
                        rightCommand = layout.rightCommand?.command,
                        enterCommand = layout.enterCommand?.command
                    )
                } else {
                    Spacer(modifier = Modifier.width(176.dp))
                }

                layout.channelRocker?.let { rocker ->
                    Rocker(
                        context = context,
                        irManager = irManager,
                        label = rocker.label(),
                        upCommand = rocker.up?.command,
                        downCommand = rocker.down?.command
                    )
                } ?: Spacer(modifier = Modifier.width(72.dp))
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(layout.gridCommands, key = { it.index }) { indexed ->
                SkeuoButton(
                    label = indexed.command.displayLabel(),
                    onClick = {
                        HapticEngine.lightTap(context)
                        IrTransmitter.transmit(context, irManager, indexed.command)
                    }
                )
            }
        }
    }
}

@Composable
private fun PowerCommandButton(
    context: Context,
    irManager: ConsumerIrManager?,
    command: FlipperCommand
) {
    if (command.usesPowerIcon()) {
        FilledIconButton(
            onClick = {
                HapticEngine.heavyTap(context)
                IrTransmitter.transmit(context, irManager, command)
            },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = I18n.t("label_power"),
                modifier = Modifier.size(30.dp)
            )
        }
    } else {
        SkeuoButton(
            label = command.powerLabel(),
            onClick = {
                HapticEngine.heavyTap(context)
                IrTransmitter.transmit(context, irManager, command)
            },
            isPower = true,
            modifier = Modifier.size(72.dp)
        )
    }
}

@Composable
private fun Rocker(
    context: Context,
    irManager: ConsumerIrManager?,
    label: String,
    upCommand: FlipperCommand?,
    downCommand: FlipperCommand?
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .width(72.dp)
            .height(144.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { upCommand?.transmitLight(context, irManager) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                enabled = upCommand != null
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = I18n.t("panel_cd_increase", label)
                )
            }

            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            IconButton(
                onClick = { downCommand?.transmitLight(context, irManager) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                enabled = downCommand != null
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = I18n.t("panel_cd_decrease", label)
                )
            }
        }
    }
}

@Composable
private fun DPad(
    context: Context,
    irManager: ConsumerIrManager?,
    upCommand: FlipperCommand?,
    downCommand: FlipperCommand?,
    leftCommand: FlipperCommand?,
    rightCommand: FlipperCommand?,
    enterCommand: FlipperCommand?
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(176.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            upCommand?.let { command ->
                IconButton(
                    onClick = { command.transmitLight(context, irManager) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(4.dp)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = I18n.t("panel_cd_up"),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            downCommand?.let { command ->
                IconButton(
                    onClick = { command.transmitLight(context, irManager) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(4.dp)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = I18n.t("panel_cd_down"),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            leftCommand?.let { command ->
                IconButton(
                    onClick = { command.transmitLight(context, irManager) },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(4.dp)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = I18n.t("panel_cd_left"),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            rightCommand?.let { command ->
                IconButton(
                    onClick = { command.transmitLight(context, irManager) },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(4.dp)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = I18n.t("panel_cd_right"),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            enterCommand?.let { command ->
                FilledIconButton(
                    onClick = {
                        HapticEngine.heavyTap(context)
                        IrTransmitter.transmit(context, irManager, command)
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                ) {
                    Text(I18n.t("panel_btn_ok"))
                }
            }
        }
    }
}

private data class IndexedCommand(
    val index: Int,
    val command: FlipperCommand
)

private data class ControlRocker(
    val kind: RockerKind,
    val up: IndexedCommand?,
    val down: IndexedCommand?
) {
    fun label(): String {
        return when (kind) {
            RockerKind.VOLUME -> I18n.t("panel_rocker_vol")
            RockerKind.TEMPERATURE -> I18n.t("panel_rocker_temp")
            RockerKind.CHANNEL -> I18n.t("panel_rocker_ch")
        }
    }
}

private enum class RockerKind {
    VOLUME,
    TEMPERATURE,
    CHANNEL
}

private data class PanelCommandLayout(
    val powerCommands: List<IndexedCommand>,
    val leftRocker: ControlRocker?,
    val channelRocker: ControlRocker?,
    val upCommand: IndexedCommand?,
    val downCommand: IndexedCommand?,
    val leftCommand: IndexedCommand?,
    val rightCommand: IndexedCommand?,
    val enterCommand: IndexedCommand?,
    val gridCommands: List<IndexedCommand>
) {
    val hasDPad: Boolean
        get() = upCommand != null ||
            downCommand != null ||
            leftCommand != null ||
            rightCommand != null ||
            enterCommand != null

    val hasControlRow: Boolean
        get() = leftRocker != null || channelRocker != null || hasDPad

    companion object {
        fun from(commands: List<FlipperCommand>): PanelCommandLayout {
            val indexed = commands.mapIndexed { index, command -> IndexedCommand(index, command) }
            val bySlot = indexed.groupBy { classifyCommand(it.command.name) }

            val volumeRocker = rocker(
                kind = RockerKind.VOLUME,
                up = bySlot[CommandSlot.VOLUME_UP]?.firstOrNull(),
                down = bySlot[CommandSlot.VOLUME_DOWN]?.firstOrNull()
            )
            val temperatureRocker = rocker(
                kind = RockerKind.TEMPERATURE,
                up = bySlot[CommandSlot.TEMP_UP]?.firstOrNull(),
                down = bySlot[CommandSlot.TEMP_DOWN]?.firstOrNull()
            )
            val channelRocker = rocker(
                kind = RockerKind.CHANNEL,
                up = bySlot[CommandSlot.CHANNEL_UP]?.firstOrNull(),
                down = bySlot[CommandSlot.CHANNEL_DOWN]?.firstOrNull()
            )

            val absorbed = linkedSetOf<Int>()
            val leftRocker = volumeRocker ?: temperatureRocker

            bySlot[CommandSlot.POWER].orEmpty().forEach { absorbed += it.index }
            leftRocker?.up?.let { absorbed += it.index }
            leftRocker?.down?.let { absorbed += it.index }
            channelRocker?.up?.let { absorbed += it.index }
            channelRocker?.down?.let { absorbed += it.index }

            val up = bySlot[CommandSlot.UP]?.firstOrNull()
            val down = bySlot[CommandSlot.DOWN]?.firstOrNull()
            val left = bySlot[CommandSlot.LEFT]?.firstOrNull()
            val right = bySlot[CommandSlot.RIGHT]?.firstOrNull()
            val enter = bySlot[CommandSlot.ENTER]?.firstOrNull()
            listOfNotNull(up, down, left, right, enter).forEach { absorbed += it.index }

            return PanelCommandLayout(
                powerCommands = bySlot[CommandSlot.POWER].orEmpty(),
                leftRocker = leftRocker,
                channelRocker = channelRocker,
                upCommand = up,
                downCommand = down,
                leftCommand = left,
                rightCommand = right,
                enterCommand = enter,
                gridCommands = indexed.filterNot { it.index in absorbed }
            )
        }

        private fun rocker(
            kind: RockerKind,
            up: IndexedCommand?,
            down: IndexedCommand?
        ): ControlRocker? {
            return if (up != null || down != null) ControlRocker(kind, up, down) else null
        }
    }
}

private enum class CommandSlot {
    POWER,
    UP,
    DOWN,
    LEFT,
    RIGHT,
    ENTER,
    VOLUME_UP,
    VOLUME_DOWN,
    CHANNEL_UP,
    CHANNEL_DOWN,
    TEMP_UP,
    TEMP_DOWN
}

private fun classifyCommand(name: String): CommandSlot? {
    val key = name.commandKey()
    return when {
        key in POWER_KEYS -> CommandSlot.POWER
        key in VOLUME_UP_KEYS -> CommandSlot.VOLUME_UP
        key in VOLUME_DOWN_KEYS -> CommandSlot.VOLUME_DOWN
        key in CHANNEL_UP_KEYS -> CommandSlot.CHANNEL_UP
        key in CHANNEL_DOWN_KEYS -> CommandSlot.CHANNEL_DOWN
        key in TEMP_UP_KEYS -> CommandSlot.TEMP_UP
        key in TEMP_DOWN_KEYS -> CommandSlot.TEMP_DOWN
        key in UP_KEYS -> CommandSlot.UP
        key in DOWN_KEYS -> CommandSlot.DOWN
        key in LEFT_KEYS -> CommandSlot.LEFT
        key in RIGHT_KEYS -> CommandSlot.RIGHT
        key in ENTER_KEYS -> CommandSlot.ENTER
        else -> null
    }
}

private fun FlipperCommand.powerLabel(): String {
    return when (name.commandKey()) {
        "on", "poweron", "turnon" -> I18n.t("label_power_on")
        "off", "poweroff", "turnoff" -> I18n.t("label_power_off")
        else -> I18n.t("label_power")
    }
}

private fun FlipperCommand.usesPowerIcon(): Boolean {
    return name.commandKey() !in setOf("on", "poweron", "turnon", "off", "poweroff", "turnoff")
}

private fun FlipperCommand.displayLabel(): String {
    return name
        .replace("_", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
            }
        }
        .ifBlank { name }
}

private fun FlipperCommand.transmitLight(context: Context, irManager: ConsumerIrManager?) {
    HapticEngine.lightTap(context)
    IrTransmitter.transmit(context, irManager, this)
}

private fun String.commandKey(): String {
    val trimmed = trim()
    if (trimmed == "⏻") return "power"

    return trimmed
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9+\\-]+"), "")
}

private val POWER_KEYS = setOf(
    "power",
    "pwr",
    "standby",
    "onoff",
    "poweronoff",
    "togglepower",
    "on",
    "off",
    "poweron",
    "poweroff",
    "turnon",
    "turnoff"
)

private val VOLUME_UP_KEYS = setOf("volumeup", "volume+", "volup", "vol+", "vup", "v+")
private val VOLUME_DOWN_KEYS = setOf("volumedown", "volume-", "voldown", "voldn", "vol-", "vdown", "v-")
private val CHANNEL_UP_KEYS = setOf("channelup", "channel+", "chup", "chnext", "ch+", "programup", "prgup")
private val CHANNEL_DOWN_KEYS = setOf("channeldown", "channel-", "chdown", "chprev", "ch-", "programdown", "prgdown")
private val TEMP_UP_KEYS = setOf("tempup", "temperatureup", "temp+", "temperature+", "t+", "warmer")
private val TEMP_DOWN_KEYS = setOf("tempdown", "temperaturedown", "temp-", "temperature-", "t-", "cooler")
private val UP_KEYS = setOf("up", "uparrow", "top")
private val DOWN_KEYS = setOf("down", "downarrow", "bottom", "dwn")
private val LEFT_KEYS = setOf("left", "leftarrow", "lft")
private val RIGHT_KEYS = setOf("right", "rightarrow", "rgt")
private val ENTER_KEYS = setOf("ok", "enter", "select", "confirm")
