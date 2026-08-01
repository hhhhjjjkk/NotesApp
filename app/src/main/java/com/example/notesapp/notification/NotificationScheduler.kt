package com.example.notesapp.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.notesapp.data.Note

/**
 * 封装 AlarmManager 调度/取消提醒闹钟。
 *
 * 使用 setExactAndAllowWhileIdle 确保在 Doze 模式下也能准时触发。
 */
object NotificationScheduler {

    private const val ACTION_REMINDER = "com.example.notesapp.ACTION_REMINDER"

    /**
     * 调度一条笔记的提醒闹钟。
     * 若 reminderAt <= 0 或已过期则取消闹钟。
     */
    fun schedule(context: Context, note: Note) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, note.id)

        if (note.reminderAt <= 0L) {
            am.cancel(pendingIntent)
            return
        }

        // 确保通知渠道存在
        NotificationHelper.createChannel(context)

        // Android 12+ 需要检查精确闹钟权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!am.canScheduleExactAlarms()) {
                // 权限不足时退化为非精确闹钟，仍然可以触发（可能有数分钟延迟）
                am.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    note.reminderAt,
                    pendingIntent
                )
                return
            }
        }

        am.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            note.reminderAt,
            pendingIntent
        )
    }

    /**
     * 取消一条笔记的提醒闹钟。
     */
    fun cancel(context: Context, noteId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(buildPendingIntent(context, noteId))
    }

    private fun buildPendingIntent(context: Context, noteId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra("noteId", noteId)
        }
        return PendingIntent.getBroadcast(
            context,
            noteId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
