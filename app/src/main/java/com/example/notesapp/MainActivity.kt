package com.example.notesapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.notesapp.data.ThemeMode
import com.example.notesapp.ui.navigation.NotesNavHost
import com.example.notesapp.ui.theme.AppBackground
import com.example.notesapp.ui.theme.NotesAppTheme
import com.example.notesapp.ui.viewmodel.NotesViewModel
import com.example.notesapp.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    /** 从通知 PendingIntent 中读出 noteId，>0 表示需要跳转到编辑页。 */
    private fun noteIdFromIntent(intent: Intent?): Long {
        if (intent == null) return 0L
        return intent.getLongExtra("noteId", 0L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate 之前安装，以便正确替换启动主题
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 复用 Application 中唯一的 Repository 实例：避免 Activity 自行 new 一个，
        // 造成同一进程内存在多条数据访问路径。
        // Repository 本身为 by lazy，真正的 SQLite 打开仍推迟到首次访问。
        val app = application as NotesApplication
        val dataStoreManager = app.dataStoreManager
        val repository = app.repository

        val initialNoteId = noteIdFromIntent(intent)
        // 初始化成员字段：onNewIntent 时更新此 State 触发 Compose 重组
        pendingNoteIdState = androidx.compose.runtime.mutableLongStateOf(initialNoteId)
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel {
                SettingsViewModel(dataStoreManager)
            }
            val notesViewModel: NotesViewModel = viewModel {
                NotesViewModel(repository, app)
            }

            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val themeColor by settingsViewModel.themeColor.collectAsStateWithLifecycle()
            val backgroundUri by settingsViewModel.backgroundUri.collectAsStateWithLifecycle()
            val backgroundDim by settingsViewModel.backgroundDim.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            NotesAppTheme(
                darkTheme = darkTheme,
                themeColor = themeColor,
                backgroundUri = backgroundUri,
                backgroundDim = backgroundDim
            ) {
                Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    // 自定义背景层：图片 + 遮罩，铺满屏幕
                    AppBackground(uri = backgroundUri, isDark = darkTheme, dim = backgroundDim)
                    val navController = rememberNavController()
                    NotesNavHost(
                        navController = navController,
                        notesViewModel = notesViewModel,
                        settingsViewModel = settingsViewModel,
                        initialNoteId = pendingNoteIdState.value,
                        onNoteIdConsumed = { pendingNoteIdState.value = 0L }
                    )
                }
            }
        }
    }

    // 可观察的 noteId：支持热启动时通知点击跳转（onNewIntent 更新此值触发重组）
    private lateinit var pendingNoteIdState: androidx.compose.runtime.MutableState<Long>

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 由通知热启动再次进入时，更新 pendingNoteId 触发重组，导航到对应笔记
        val noteId = noteIdFromIntent(intent)
        if (noteId > 0L && ::pendingNoteIdState.isInitialized) {
            pendingNoteIdState.value = noteId
        }
    }
}
