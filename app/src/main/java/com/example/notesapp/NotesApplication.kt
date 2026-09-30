package com.example.notesapp

import android.app.Application
import com.example.notesapp.data.DataStoreManager
import com.example.notesapp.data.NoteDatabase
import com.example.notesapp.data.NoteRepository
import com.example.notesapp.notification.NotificationHelper
import com.example.notesapp.notification.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotesApplication : Application() {

    // DataStoreManager 延迟初始化：避免 Application.onCreate 阶段同步构造，
    // 真正的 Preferences 文件读取发生在首次 collect 时（异步）。
    val dataStoreManager: DataStoreManager by lazy { DataStoreManager(this) }

    // 统一经由 Repository 访问数据，避免各处直接持有 DAO 造成数据访问逻辑分散。
    val repository: NoteRepository by lazy {
        NoteRepository(NoteDatabase.getInstance(this).noteDao())
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // 创建通知渠道
        NotificationHelper.createChannel(this)

        // 后台预热数据库：在 IO 线程打开 SQLite 文件并完成 Room 代理初始化，
        // 避免首屏查询时才触发，造成 UI 卡顿。
        appScope.launch {
            try {
                NoteDatabase.getInstance(this@NotesApplication).openHelper.writableDatabase
                // 恢复所有未触发的提醒闹钟
                val now = System.currentTimeMillis()
                val pendingNotes = repository.getNotesWithPendingReminders(now)
                pendingNotes.forEach { note ->
                    NotificationScheduler.schedule(this@NotesApplication, note)
                }
                // 自动清理：物理删除移入回收站超过 30 天的笔记，避免无限堆积
                val thirtyDaysAgo = now - TRASH_RETENTION_MILLIS
                repository.clearTrashedBefore(thirtyDaysAgo)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    private companion object {
        /** 回收站保留时长：30 天。 */
        const val TRASH_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
