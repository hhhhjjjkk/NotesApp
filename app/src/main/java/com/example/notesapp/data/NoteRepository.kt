package com.example.notesapp.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {
    fun getAllNotes(): Flow<List<Note>> = noteDao.getAllNotes()

    fun getNotesByType(type: Int): Flow<List<Note>> = noteDao.getNotesByType(type)

    fun searchNotes(query: String): Flow<List<Note>> = noteDao.searchNotes(query.trim())

    fun getTrashedNotes(): Flow<List<Note>> = noteDao.getTrashedNotes()

    suspend fun getNoteById(id: Long): Note? = noteDao.getNoteById(id)

    // 始终用 insert(REPLACE)，既能新建也能更新；撤销删除时也用它恢复
    suspend fun saveNote(note: Note): Long = noteDao.insert(note)

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

    suspend fun clearTrashed() = noteDao.clearTrashed()

    // 清理在回收站中超过指定时长的笔记，返回前无需结果
    suspend fun clearTrashedBefore(beforeMillis: Long) = noteDao.clearTrashedBefore(beforeMillis)
}
