package com.example.notesapp.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.notesapp.MainActivity
import com.example.notesapp.R

object NotificationHelper {

    const val CHANNEL_ID = "note_reminder"
    const val CHANNEL_NAME = "笔记提醒"

    /**
     * 创建通知渠道（Android 8.0+ 必需），重复调用安全。
     */
    fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "笔记到期提醒通知"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 发送一条提醒通知。
     *
     * @param noteId 笔记 ID，用于 PendingIntent 跳转和通知 ID
     * @param title  通知标题
     * @param content 通知正文
     */
    fun showReminder(context: Context, noteId: Long, title: String, content: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 点击通知跳转到 MainActivity，附带 noteId
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("noteId", noteId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            noteId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(noteId.toInt(), notification)
    }
}
