package com.xiao.pocketir.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiao.pocketir.i18n.I18n
import com.xiao.pocketir.ir.IrDecoder
import com.xiao.pocketir.model.DeviceMeta
import com.xiao.pocketir.model.DeviceType
import com.xiao.pocketir.model.FlipperCommand
import com.xiao.pocketir.model.FlipperSignalType

@Composable
fun NecEditor(
    onSave: (DeviceMeta) -> Unit
) {
    var remoteName by remember { mutableStateOf("") }
    var buttonName by remember { mutableStateOf(I18n.t("editor_default_custom")) }
    var protocol by remember { mutableStateOf("NEC") }
    var address by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }

    val normalizedAddress = remember(address) { IrDecoder.normalizeFlipperHex(address) }
    val normalizedCommand = remember(command) { IrDecoder.normalizeFlipperHex(command) }
    val canSave = protocol.isNotBlank() && normalizedAddress != null && normalizedCommand != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = I18n.t("ui_editor_intro"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = remoteName,
            onValueChange = { remoteName = it },
            label = { Text(I18n.t("ui_editor_remote_name")) },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = buttonName,
            onValueChange = { buttonName = it },
            label = { Text(I18n.t("ui_editor_button_name")) },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = protocol,
            onValueChange = { protocol = it.uppercase() },
            label = { Text(I18n.t("ui_editor_protocol")) },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text(I18n.t("ui_editor_address")) },
            isError = address.isNotBlank() && normalizedAddress == null,
            supportingText = {
                Text(
                    when {
                        normalizedAddress != null -> normalizedAddress
                        address.isNotBlank() -> I18n.t("error_invalid_hex")
                        else -> I18n.t("ui_editor_hex_hint")
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            label = { Text(I18n.t("ui_editor_command")) },
            isError = command.isNotBlank() && normalizedCommand == null,
            supportingText = {
                Text(
                    when {
                        normalizedCommand != null -> normalizedCommand
                        command.isNotBlank() -> I18n.t("error_invalid_hex")
                        else -> I18n.t("ui_editor_hex_hint")
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val finalAddress = normalizedAddress ?: return@Button
                val finalCommand = normalizedCommand ?: return@Button
                val finalButtonName = buttonName.trim().ifBlank { "Custom" }
                val finalRemoteName = remoteName.trim().ifBlank { finalButtonName }
                onSave(
                    DeviceMeta(
                        category = I18n.t("editor_default_custom"),
                        brand = I18n.t("editor_default_manual"),
                        model = finalRemoteName,
                        sourcePath = buildCustomSourcePath(finalRemoteName, finalButtonName),
                        type = DeviceType.GENERIC,
                        embeddedCommands = listOf(
                            FlipperCommand(
                                name = finalButtonName,
                                type = FlipperSignalType.PARSED,
                                protocol = protocol.trim().ifBlank { "NEC" },
                                address = finalAddress,
                                command = finalCommand
                            )
                        )
                    )
                )
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(I18n.t("btn_save"))
        }
    }
}

private fun buildCustomSourcePath(remoteName: String, buttonName: String): String {
    val seed = "$remoteName-$buttonName"
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "custom-nec" }
    val fingerprint = ("$remoteName|$buttonName").hashCode().toUInt().toString(16)
    return "bookmark://custom/$seed-$fingerprint"
}
