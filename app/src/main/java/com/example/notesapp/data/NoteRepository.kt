package com.example.notesapp.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {
    fun getAllNotes(): Flow<List<Note>> = noteDao.getAllNotes()

    fun searchNotes(query: String): Flow<List<Note>> = noteDao.searchNotes(query.trim())

    suspend fun getNoteById(id: Long): Note? = noteDao.getNoteById(id)

    // 始终用 insert(REPLACE)，既能新建也能恢复已删除的笔记（撤销删除）
    suspend fun saveNote(note: Note): Long = noteDao.insert(note)

    suspend fun deleteNote(note: Note) = noteDao.delete(note)
}
