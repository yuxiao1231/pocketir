package com.xiao.pocketir.ui

import com.xiao.pocketir.data.SettingsRepository
import com.xiao.pocketir.model.DeviceMeta

data class MainUiState(
    val currentPath: String = SettingsRepository.defaultLibraryPath(),
    val bookmarks: List<DeviceMeta> = emptyList(),
    val selectedDevice: DeviceMeta? = null,
    val isDarkMode: Boolean = false,
    val isBookmarkLoading: Boolean = true
)
