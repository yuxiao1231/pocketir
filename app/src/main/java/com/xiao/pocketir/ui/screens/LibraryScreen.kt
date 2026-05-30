package com.xiao.pocketir.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiao.pocketir.data.IrRepository
import com.xiao.pocketir.i18n.I18n
import com.xiao.pocketir.model.DeviceMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(
    rootPath: String,
    onDevicePicked: (DeviceMeta) -> Unit
) {
    val library = remember(rootPath) { IrRepository.open(rootPath) }
    var searchText by remember { mutableStateOf("") }
    var deviceList by remember(rootPath) { mutableStateOf<List<DeviceMeta>>(emptyList()) }
    var isLoading by remember(rootPath) { mutableStateOf(true) }

    LaunchedEffect(rootPath) {
        isLoading = true
        library.ensureIndexed()
        deviceList = withContext(Dispatchers.IO) { library.getDeviceList() }
        isLoading = false
    }

    LaunchedEffect(rootPath, searchText, isLoading) {
        if (isLoading) return@LaunchedEffect
        delay(300)
        deviceList = withContext(Dispatchers.IO) { library.search(searchText) }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text(I18n.t("ui_search_hint")) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        if (deviceList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = I18n.t("ui_empty_library"),
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(deviceList) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDevicePicked(device) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = device.model, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${device.brand} / ${device.category}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
