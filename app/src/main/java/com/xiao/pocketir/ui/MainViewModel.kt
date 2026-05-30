package com.xiao.pocketir.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiao.pocketir.data.BookmarkManager
import com.xiao.pocketir.data.SettingsRepository
import com.xiao.pocketir.model.DeviceMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val settingsRepository = SettingsRepository(appContext)
    private val bookmarks = MutableStateFlow<List<DeviceMeta>>(emptyList())
    private val selectedDevice = MutableStateFlow<DeviceMeta?>(null)
    private val isBookmarkLoading = MutableStateFlow(true)

    val uiState = combine(
        settingsRepository.settings,
        bookmarks,
        selectedDevice,
        isBookmarkLoading
    ) { settings, savedRemotes, selected, loading ->
        MainUiState(
            currentPath = settings.currentPath,
            bookmarks = savedRemotes,
            selectedDevice = selected,
            isDarkMode = settings.isDarkMode,
            isBookmarkLoading = loading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState()
    )

    init {
        refreshBookmarks()
    }

    fun setDarkMode(isDarkMode: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDarkMode(isDarkMode)
        }
    }

    fun setCurrentPath(path: String) {
        viewModelScope.launch {
            settingsRepository.setCurrentPath(path)
        }
    }

    fun selectDevice(device: DeviceMeta) {
        selectedDevice.value = device
    }

    fun clearSelectedDevice() {
        selectedDevice.value = null
    }

    fun saveBookmark(device: DeviceMeta) {
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                BookmarkManager.upsert(appContext, device)
            }
            bookmarks.value = updated
        }
    }

    fun deleteBookmark(device: DeviceMeta) {
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                BookmarkManager.remove(appContext, device)
            }
            bookmarks.value = updated
            selectedDevice.update { selected ->
                if (selected != null && sameDevice(selected, device)) null else selected
            }
        }
    }

    private fun refreshBookmarks() {
        viewModelScope.launch {
            isBookmarkLoading.value = true
            bookmarks.value = withContext(Dispatchers.IO) {
                BookmarkManager.load(appContext)
            }
            isBookmarkLoading.value = false
        }
    }

    private fun sameDevice(left: DeviceMeta, right: DeviceMeta): Boolean {
        return left.category == right.category &&
            left.brand == right.brand &&
            left.model == right.model &&
            left.sourcePath == right.sourcePath
    }
}
