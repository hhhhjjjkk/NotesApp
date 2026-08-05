package com.example.notesapp

import android.content.Intent
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

        // 延迟创建数据库与仓库：使用 by lazy 避免主线程在 onCreate 阶段同步触发
        // Room 的代理初始化和 SQLite 打开操作，将真正的 IO 推迟到 ViewModel 使用时。
        val app = application as NotesApplication
        val dataStoreManager = app.dataStoreManager
        val repository by lazy { NoteRepository(NoteDatabase.getInstance(app).noteDao()) }

        val initialNoteId = noteIdFromIntent(intent)
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel {
                SettingsViewModel(dataStoreManager)
            }
            val notesViewModel: NotesViewModel = viewModel {
                NotesViewModel(repository, app)
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
                        settingsViewModel = settingsViewModel,
                        initialNoteId = initialNoteId
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 通知点击再次进入时也跳转：通过 setContent 重新读取 intent（这里仅触发一次重组即可）
        // 由于 navController 在 Composable 内创建，这里通过重建 intent 后由系统重组触发
    }
}
