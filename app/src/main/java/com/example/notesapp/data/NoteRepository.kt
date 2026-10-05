package com.example.notesapp.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {
    fun getAllNotes(): Flow<List<Note>> = noteDao.getAllNotes()

    fun getNotesByType(type: Int): Flow<List<Note>> = noteDao.getNotesByType(type)

    fun searchNotes(query: String): Flow<List<Note>> = noteDao.searchNotes(query.trim())

    /**
     * 首页列表：类型过滤 + 可选搜索，全部下推到 SQL 执行。
     * query 为空时退化为仅按类型过滤，避免全表读入内存。
     */
    fun getNotesByTypeAndQuery(type: Int, query: String): Flow<List<Note>> =
        noteDao.getNotesByTypeAndQuery(type, query.trim())

    fun getTrashedNotes(): Flow<List<Note>> = noteDao.getTrashedNotes()

    suspend fun getNoteById(id: Long): Note? = noteDao.getNoteById(id)

    /**
     * 保存笔记，区分「新建」与「更新」：
     * - id == 0：INSERT 新行
     * - id != 0：UPDATE；若该 id 已不存在则返回 null（而不是像 REPLACE 那样凭空造出行来覆盖）。
     *
     * 之前一律 INSERT(REPLACE)，在「通知深链指向已删除/已回收笔记」时，编辑页以同 id 保存
     * 会凭空重建一行、把回收站状态与图文内容一并覆盖，造成数据丢失。
     */
    suspend fun saveNote(note: Note): Long? {
        if (note.id == 0L) {
            return noteDao.insert(note)
        }
        val existing = noteDao.getNoteById(note.id) ?: return null
        noteDao.update(existing.copy(
            title = note.title,
            content = note.content,
            richContent = note.richContent,
            color = note.color,
            isPinned = note.isPinned,
            type = note.type,
            reminderAt = note.reminderAt,
            updatedAt = note.updatedAt
            // 刻意不覆盖 isTrashed/trashedAt/createdAt：这些是生命周期状态，
            // 编辑保存不应改变「是否在回收站」或创建时间
        ))
        return note.id
    }

    // 物理删除（仅回收站永久删除使用）
    suspend fun deleteNote(note: Note) = noteDao.delete(note)

    // 软删除：移入回收站，不真正删除，可恢复
    suspend fun moveToTrash(note: Note) {
        noteDao.insert(note.copy(isTrashed = true, trashedAt = System.currentTimeMillis()))
    }

    // 从回收站恢复
    suspend fun restoreFromTrash(note: Note) {
        noteDao.insert(note.copy(isTrashed = false, trashedAt = 0L))
    }

    // 原子翻转置顶状态
    suspend fun togglePin(id: Long) = noteDao.togglePin(id)

    suspend fun clearTrashed() = noteDao.clearTrashed()

    // 清理在回收站中超过指定时长的笔记，返回前无需结果
    suspend fun clearTrashedBefore(beforeMillis: Long) = noteDao.clearTrashedBefore(beforeMillis)

    // ===== 提醒调度相关（供 Application / BroadcastReceiver 使用） =====

    /** 查询所有尚未到期的提醒，用于启动或开机后重新排布闹钟。 */
    suspend fun getNotesWithPendingReminders(now: Long): List<Note> =
        noteDao.getNotesWithPendingReminders(now)

    /** 查询已到期但未触发过的提醒，用于开机后补发关机期间漏掉的通知。 */
    suspend fun getNotesWithMissedReminders(now: Long): List<Note> =
        noteDao.getNotesWithMissedReminders(now)

    /** 提醒触发后清零 reminderAt，避免开机补发时重复通知。 */
    suspend fun clearReminder(id: Long) = noteDao.clearReminder(id)
}
