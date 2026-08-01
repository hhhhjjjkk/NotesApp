package com.example.notesapp

import android.app.Application
import com.example.notesapp.data.DataStoreManager
import com.example.notesapp.data.NoteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotesApplication : Application() {

    // DataStoreManager 延迟初始化：避免 Application.onCreate 阶段同步构造，
    // 真正的 Preferences 文件读取发生在首次 collect 时（异步）。
    val dataStoreManager: DataStoreManager by lazy { DataStoreManager(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // 后台预热数据库：在 IO 线程打开 SQLite 文件并完成 Room 代理初始化，
        // 避免首屏查询时才触发，造成 UI 卡顿。
        appScope.launch {
            runCatching {
                NoteDatabase.getInstance(this@NotesApplication).openHelper.writableDatabase
            }
        }
    }
}
