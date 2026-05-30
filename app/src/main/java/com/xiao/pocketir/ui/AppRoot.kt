package com.xiao.pocketir.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.ConsumerIrManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xiao.pocketir.i18n.I18n
import com.xiao.pocketir.ui.screens.HomeScreen
import com.xiao.pocketir.ui.screens.LibraryScreen
import com.xiao.pocketir.ui.screens.NecEditor
import com.xiao.pocketir.ui.screens.PanelScreen
import com.xiao.pocketir.ui.theme.PocketIrTheme
import org.json.JSONObject

private object AppRoute {
    const val Home = "home"
    const val Library = "library"
    const val Panel = "panel"
    const val Editor = "editor"
}

private data class LangOption(val displayName: String, val tag: String)

/**
 * 扫描 assets/langs/ 目录，读每个 JSON 里的 lang_name 字段构建选项列表。
 * "Auto" 选项永远排在第一位，其余按 tag 字母序排列。
 */
private fun loadLangOptions(context: Context): List<LangOption> {
    val fromAssets = runCatching {
        context.assets.list("langs")
            ?.filter { it.endsWith(".json") }
            ?.mapNotNull { filename ->
                val tag = filename.removeSuffix(".json")
                val displayName = runCatching {
                    val text = context.assets.open("langs/$filename")
                        .bufferedReader().use { it.readText() }
                    JSONObject(text).optString("lang_name", tag).ifBlank { tag }
                }.getOrDefault(tag)
                LangOption(displayName, tag)
            }
            ?.sortedBy { it.tag }
    }.getOrNull().orEmpty()

    return listOf(LangOption(I18n.t("lang_auto"), "auto")) + fromAssets
}

@Composable
fun AppRoot(
    irManager: ConsumerIrManager?,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route ?: AppRoute.Home
    val openLibraryPathPicker = rememberLibraryPathPicker(
        onPathResolved = viewModel::setCurrentPath,
        onPathError = {
            Toast.makeText(context, I18n.t("toast_path_error"), Toast.LENGTH_SHORT).show()
        }
    )

    var showHelpDialog by rememberSaveable { mutableStateOf(false) }
    var showLangDialog by rememberSaveable { mutableStateOf(false) }
    // 每次切换语言 +1，用来强制 key() 重建整棵 NavHost 子树，确保所有 I18n.t() 立即刷新
    var langVersion by remember { mutableIntStateOf(0) }

    PocketIrTheme(darkTheme = uiState.isDarkMode) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            StoragePermissionGate()

            if (showHelpDialog) {
                HelpDialog(onDismiss = { showHelpDialog = false })
            }

            if (showLangDialog) {
                LanguageDialog(
                    onDismiss = { showLangDialog = false },
                    onApply = { tag ->
                        context.getSharedPreferences("config", Context.MODE_PRIVATE)
                            .edit()
                            .putString("forced_language", tag)
                            .apply()
                        I18n.init(context)
                        langVersion++          // 触发 NavHost 整棵子树重建
                        showLangDialog = false
                        Toast.makeText(context, I18n.t("toast_lang_changed"), Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Scaffold(
                topBar = {
                    RootTopBar(
                        route = currentRoute,
                        title = titleForRoute(currentRoute, uiState),
                        isDarkMode = uiState.isDarkMode,
                        canNavigateBack = navController.previousBackStackEntry != null,
                        onThemeToggle = { viewModel.setDarkMode(!uiState.isDarkMode) },
                        onPickPath = openLibraryPathPicker,
                        onInfo = { showHelpDialog = true },
                        onLang = { showLangDialog = true },
                        onBack = { navController.popBackStack() }
                    )
                }
            ) { padding ->
                // key(langVersion) 确保切语言时整棵导航树重建，所有 I18n.t() 立即生效
                key(langVersion) {
                NavHost(
                    navController = navController,
                    startDestination = AppRoute.Home,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    composable(AppRoute.Home) {
                        HomeScreen(
                            bookmarks = uiState.bookmarks,
                            onDeviceSelect = { device ->
                                viewModel.selectDevice(device)
                                navController.navigateSingleTop(AppRoute.Panel)
                            },
                            onDelete = viewModel::deleteBookmark,
                            currentPath = uiState.currentPath,
                            onPickPath = openLibraryPathPicker,
                            onBrowseLibrary = { navController.navigateSingleTop(AppRoute.Library) },
                            onOpenEditor = { navController.navigateSingleTop(AppRoute.Editor) }
                        )
                    }

                    composable(AppRoute.Library) {
                        LibraryScreen(
                            rootPath = uiState.currentPath,
                            onDevicePicked = { device ->
                                viewModel.saveBookmark(device)
                                viewModel.selectDevice(device)
                                navController.navigateSingleTop(AppRoute.Panel)
                            }
                        )
                    }

                    composable(AppRoute.Panel) {
                        val selectedDevice = uiState.selectedDevice
                        if (selectedDevice == null) {
                            MissingSelection(
                                onBackHome = {
                                    navController.navigate(AppRoute.Home) {
                                        popUpTo(AppRoute.Home) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        } else {
                            PanelScreen(irManager = irManager, device = selectedDevice)
                        }
                    }

                    composable(AppRoute.Editor) {
                        NecEditor(
                            onSave = { device ->
                                viewModel.saveBookmark(device)
                                viewModel.selectDevice(device)
                                navController.navigateSingleTop(AppRoute.Panel)
                            }
                        )
                    }
                }
                } // end key(langVersion)
            }
        }
    }
}

private fun titleForRoute(route: String, uiState: MainUiState): String {
    return when (route) {
        AppRoute.Library -> I18n.t("ui_library_title")
        AppRoute.Panel -> uiState.selectedDevice?.model ?: I18n.t("app_name")
        AppRoute.Editor -> I18n.t("ui_editor_title")
        else -> I18n.t("app_name")
    }
}

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun rememberLibraryPathPicker(
    onPathResolved: (String) -> Unit,
    onPathError: () -> Unit
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        resolveUriToPath(uri)?.let { path ->
            onPathResolved(path)
            Toast.makeText(context, I18n.t("toast_path_updated"), Toast.LENGTH_SHORT).show()
        } ?: onPathError()
    }

    return remember(launcher) {
        { launcher.launch(arrayOf("*/*")) }
    }
}

private fun resolveUriToPath(uri: Uri): String? {
    return runCatching {
        val docId = DocumentsContract.getDocumentId(uri)
        val split = docId.split(":")
        if (split.size < 2 || !split[0].equals("primary", ignoreCase = true)) {
            null
        } else {
            Environment.getExternalStorageDirectory().absolutePath + "/" + split[1]
        }
    }.getOrNull()
}

@Composable
private fun StoragePermissionGate() {
    val context = LocalContext.current
    var showPermissionDialog by rememberSaveable {
        mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager())
    }

    if (showPermissionDialog) {
        PermissionDialog(
            context = context,
            onDismiss = { showPermissionDialog = false }
        )
    }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(I18n.t("dialog_help_title")) },
        text = { Text(I18n.t("dialog_help_msg")) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(I18n.t("btn_i_know"))
            }
        }
    )
}

// ---------------------------------------------------------------------------
// 语言选择弹窗 — 动态枚举 assets/langs/ 下的所有 JSON
// ---------------------------------------------------------------------------
@Composable
private fun LanguageDialog(
    onDismiss: () -> Unit,
    onApply: (tag: String) -> Unit
) {
    val context = LocalContext.current

    val currentTag = remember {
        context.getSharedPreferences("config", Context.MODE_PRIVATE)
            .getString("forced_language", "auto") ?: "auto"
    }

    val options = remember { loadLangOptions(context) }
    var selected by remember { mutableStateOf(currentTag) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(I18n.t("dialog_lang_title")) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = (option.tag == selected),
                                onClick = { selected = option.tag },
                                role = Role.RadioButton
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (option.tag == selected),
                            onClick = null  // 由 Row 的 selectable 统一处理
                        )
                        Text(
                            text = option.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(selected) }) {
                Text(I18n.t("btn_apply"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(I18n.t("btn_cancel"))
            }
        }
    )
}

// ---------------------------------------------------------------------------
// TopBar
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootTopBar(
    route: String,
    title: String,
    isDarkMode: Boolean,
    canNavigateBack: Boolean,
    onThemeToggle: () -> Unit,
    onPickPath: () -> Unit,
    onInfo: () -> Unit,
    onLang: () -> Unit,
    onBack: () -> Unit
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (canNavigateBack && route != AppRoute.Home) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = I18n.t("btn_back"))
                }
            } else {
                IconButton(onClick = onPickPath) {
                    Icon(Icons.Default.FolderOpen, contentDescription = I18n.t("action_folder"))
                }
            }
        },
        actions = {
            if (route == AppRoute.Home) {
                IconButton(onClick = onLang) {
                    Icon(Icons.Default.Language, contentDescription = I18n.t("dialog_lang_title"))
                }
                IconButton(onClick = onInfo) {
                    Icon(Icons.Default.Info, contentDescription = I18n.t("action_info"))
                }
            }
            IconButton(onClick = onThemeToggle) {
                Icon(
                    imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = I18n.t("ui_switch_theme")
                )
            }
        }
    )
}

@Composable
private fun MissingSelection(onBackHome: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onBackHome) {
            Text(I18n.t("btn_back"))
        }
    }
}

@Composable
private fun PermissionDialog(context: Context, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(I18n.t("dialog_perm_title")) },
        text = { Text(I18n.t("dialog_perm_msg")) },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    } catch (_: Exception) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                }
            ) {
                Text(I18n.t("btn_grant"))
            }
        },
        dismissButton = {
            TextButton(onClick = { (context as? Activity)?.finish() }) {
                Text(I18n.t("btn_exit"))
            }
        }
    )
}
