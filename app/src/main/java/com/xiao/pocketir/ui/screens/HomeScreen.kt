package com.xiao.pocketir.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiao.pocketir.i18n.I18n
import com.xiao.pocketir.model.DeviceMeta
import com.xiao.pocketir.ui.theme.ConsoleBackground
import com.xiao.pocketir.ui.theme.ConsoleGreen
import com.xiao.pocketir.ui.theme.OrangeStatus

@Composable
fun HomeScreen(
    bookmarks: List<DeviceMeta>,
    onDeviceSelect: (DeviceMeta) -> Unit,
    onDelete: (DeviceMeta) -> Unit,
    currentPath: String,
    onPickPath: () -> Unit,
    onBrowseLibrary: () -> Unit,
    onOpenEditor: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            LibraryStatusCard(
                currentPath = currentPath,
                savedCount = bookmarks.size,
                onPickPath = onPickPath
            )
        }

        item {
            QuickActionsCard(
                onBrowseLibrary = onBrowseLibrary,
                onOpenEditor = onOpenEditor
            )
        }

        item {
            ConsoleCard(savedCount = bookmarks.size)
        }

        item {
            Text(
                text = I18n.t("ui_saved_remotes_title"),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
            )
        }

        if (bookmarks.isEmpty()) {
            item {
                EmptyRemoteState(
                    onBrowseLibrary = onBrowseLibrary,
                    onOpenEditor = onOpenEditor
                )
            }
        } else {
            items(bookmarks) { device ->
                RemoteCard(
                    device = device,
                    onDeviceSelect = onDeviceSelect,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun LibraryStatusCard(
    currentPath: String,
    savedCount: Int,
    onPickPath: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = I18n.t("ui_status_ready"),
                color = OrangeStatus,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = I18n.t("ui_status_summary", savedCount),
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )

            Text(
                text = I18n.t("ui_path_label"),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = currentPath,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onPickPath) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(I18n.t("action_folder"))
                }
            }
        }
    }
}

@Composable
private fun QuickActionsCard(
    onBrowseLibrary: () -> Unit,
    onOpenEditor: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = I18n.t("ui_advanced_title"), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onBrowseLibrary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(I18n.t("ui_action_scan_library"))
            }
            FilledTonalButton(
                onClick = onOpenEditor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Code, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(I18n.t("ui_action_nec_editor"))
            }
        }
    }
}

@Composable
private fun ConsoleCard(savedCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(containerColor = ConsoleBackground)
    ) {
        Text(
            text = I18n.t("ui_console_text", savedCount),
            color = ConsoleGreen,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        )
    }
}

@Composable
private fun EmptyRemoteState(
    onBrowseLibrary: () -> Unit,
    onOpenEditor: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = I18n.t("ui_bookmarks_empty"),
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.clickable(onClick = onBrowseLibrary),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = I18n.t("ui_add_hint"), fontSize = 14.sp)
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.clickable(onClick = onOpenEditor),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = I18n.t("ui_manual_hint"), fontSize = 14.sp)
            Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RemoteCard(
    device: DeviceMeta,
    onDeviceSelect: (DeviceMeta) -> Unit,
    onDelete: (DeviceMeta) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDeviceSelect(device) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.model,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${device.brand} / ${device.category}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            IconButton(onClick = { onDelete(device) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = I18n.t("action_delete"),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
