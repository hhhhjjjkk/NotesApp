package com.example.notesapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.notesapp.data.NoteDatabase
import com.example.notesapp.data.NoteRepository
import com.example.notesapp.data.ThemeMode
import com.example.notesapp.ui.navigation.NotesNavHost
import com.example.notesapp.ui.theme.AppBackground
import com.example.notesapp.ui.theme.NotesAppTheme
import com.example.notesapp.ui.viewmodel.NotesViewModel
import com.example.notesapp.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate 之前安装，以便正确替换启动主题
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 延迟创建数据库与仓库：使用 by lazy 避免主线程在 onCreate 阶段同步触发
        // Room 的代理初始化和 SQLite 打开操作，将真正的 IO 推迟到 ViewModel 使用时。
        val app = application as NotesApplication
        val dataStoreManager = app.dataStoreManager
        val repository by lazy { NoteRepository(NoteDatabase.getInstance(app).noteDao()) }

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel {
                SettingsViewModel(dataStoreManager)
            }
            val notesViewModel: NotesViewModel = viewModel {
                NotesViewModel(repository)
            }

            val themeMode by settingsViewModel.themeMode.collectAsState()
            val themeColor by settingsViewModel.themeColor.collectAsState()
            val backgroundUri by settingsViewModel.backgroundUri.collectAsState()
            val backgroundDim by settingsViewModel.backgroundDim.collectAsState()
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
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }
}
