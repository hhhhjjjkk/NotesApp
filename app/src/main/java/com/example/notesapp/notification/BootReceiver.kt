package com.example.notesapp.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.notesapp.data.NoteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 开机后重新调度所有未触发的提醒闹钟。
 * AlarmManager 在重启后会被清空，需要重新设置。
 *
 * 同时补发关机期间已过期但未触发的提醒（避免静默丢失）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // goAsync 延长生命周期，避免在恢复/补发完成前进程被杀
                val pendingResult = goAsync()
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

                scope.launch {
                    try {
                        val dao = NoteDatabase.getInstance(context).noteDao()
                        val now = System.currentTimeMillis()

                        // 1) 重新调度未来的提醒
                        val pendingNotes = dao.getNotesWithPendingReminders(now)
                        for (note in pendingNotes) {
                            NotificationScheduler.schedule(context, note)
                        }

                        // 2) 补发关机期间已过期但未触发的提醒，然后清零 reminderAt
                        val missedNotes = dao.getNotesWithMissedReminders(now)
                        NotificationHelper.createChannel(context)
                        for (note in missedNotes) {
                            val title = note.title.ifBlank { "笔记提醒" }
                            val preview = note.content.ifBlank { "点击查看详情" }
                            NotificationHelper.showReminder(context, note.id, title, preview)
                            dao.clearReminder(note.id)
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        android.util.Log.e("BootReceiver", "开机恢复提醒失败", e)
                    } finally {
                        pendingResult.finish()
                        scope.cancel()
                    }
                }
            }
        }
    }
}
