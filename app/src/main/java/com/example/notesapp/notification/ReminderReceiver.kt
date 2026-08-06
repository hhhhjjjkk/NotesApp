package com.example.notesapp.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.notesapp.data.NoteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 闹钟触发时接收广播，从数据库读取笔记内容并发送通知。
 *
 * 使用 goAsync() 延长 onReceive 生命周期，避免在异步任务完成前进程被杀。
 * 触发成功后清零 reminderAt，避免开机补发时重复通知。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra("noteId", -1L)
        if (noteId <= 0L) return

        // 确保通知渠道已创建
        NotificationHelper.createChannel(context)

        // goAsync 拿到 PendingResult，让系统保留进程直到我们 finish()
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val dao = NoteDatabase.getInstance(context).noteDao()
                val note = dao.getNoteById(noteId)
                // 已被移入回收站或已删除的笔记不提醒
                if (note == null || note.isTrashed) return@launch

                val title = note.title.ifBlank { "笔记提醒" }
                val preview = note.content.ifBlank { "点击查看详情" }
                NotificationHelper.showReminder(context, noteId, title, preview)
                // 触发成功后清零 reminderAt，避免开机补发时重复通知
                dao.clearReminder(note.id)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("ReminderReceiver", "触发提醒失败 noteId=$noteId", e)
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }
}
