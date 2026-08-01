package com.example.notesapp.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.notesapp.data.NoteDatabase

/**
 * 闹钟触发时接收广播，从数据库读取笔记内容并发送通知。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra("noteId", -1L)
        if (noteId == -1L) return

        // 确保通知渠道已创建
        NotificationHelper.createChannel(context)

        // 从数据库读取笔记信息（runBlocking 适配 suspend DAO 调用）
        val dao = NoteDatabase.getInstance(context).noteDao()
        Thread {
            kotlinx.coroutines.runBlocking {
                val note = dao.getNoteById(noteId) ?: return@runBlocking
                // 已被移入回收站的笔记不提醒
                if (note.isTrashed) return@runBlocking

                val title = note.title.ifBlank { "笔记提醒" }
                val preview = note.content.ifBlank { "点击查看详情" }
                NotificationHelper.showReminder(context, noteId, title, preview)
            }
        }.start()
    }
}
