package com.example.notesapp.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.notesapp.data.NoteDatabase

/**
 * 开机后重新调度所有未触发的提醒闹钟。
 * AlarmManager 在重启后会被清空，需要重新设置。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Thread {
            kotlinx.coroutines.runBlocking {
                val dao = NoteDatabase.getInstance(context).noteDao()
                val now = System.currentTimeMillis()
                val pendingNotes = dao.getNotesWithPendingReminders(now)
                for (note in pendingNotes) {
                    NotificationScheduler.schedule(context, note)
                }
            }
        }.start()
    }
}
