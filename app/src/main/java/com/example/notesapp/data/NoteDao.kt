package com.example.notesapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isTrashed = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isTrashed = 0 AND type = :type ORDER BY isPinned DESC, updatedAt DESC")
    fun getNotesByType(type: Int): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isTrashed = 0 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY isPinned DESC, updatedAt DESC")
    fun searchNotes(query: String): Flow<List<Note>>

    /**
     * 首页列表查询：在 SQL 层同时完成「类型过滤 + 可选关键字搜索」，避免把全表读进内存再过滤。
     *
     * 用法约定：不需要搜索时传入空串 ""，此时 LIKE '%%' 恒为真，等价于只按类型过滤，
     * 因此无需再用两个 Flow 做 combine。
     * 注意 LIKE 对 ASCII 默认大小写不敏感；中文无大小写概念，行为与原先 [String.contains] 一致。
     */
    @Query(
        """
        SELECT * FROM notes
        WHERE isTrashed = 0
          AND type = :type
          AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')
        ORDER BY isPinned DESC, updatedAt DESC
        """
    )
    fun getNotesByTypeAndQuery(type: Int, query: String): Flow<List<Note>>

    // 回收站：按移入时间倒序
    @Query("SELECT * FROM notes WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    fun getTrashedNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    // 清空回收站：物理删除所有已回收笔记
    @Query("DELETE FROM notes WHERE isTrashed = 1")
    suspend fun clearTrashed()

    // 过期自动清理：删除移入回收站超过指定毫秒的笔记
    @Query("DELETE FROM notes WHERE isTrashed = 1 AND trashedAt > 0 AND trashedAt < :before")
    suspend fun clearTrashedBefore(before: Long)

    // 查询所有未触发提醒的笔记（用于开机/启动后恢复闹钟调度）
    @Query("SELECT * FROM notes WHERE isTrashed = 0 AND reminderAt > :now")
    suspend fun getNotesWithPendingReminders(now: Long): List<Note>

    // 查询所有过期但未触发提醒的笔记（用于开机后补发漏掉的提醒）
    @Query("SELECT * FROM notes WHERE isTrashed = 0 AND reminderAt > 0 AND reminderAt <= :now")
    suspend fun getNotesWithMissedReminders(now: Long): List<Note>

    // 清零已触发的提醒，避免下次保存时又被重新调度
    @Query("UPDATE notes SET reminderAt = 0 WHERE id = :id")
    suspend fun clearReminder(id: Long)

    // 原子翻转置顶状态，避免基于陈旧 note 对象翻转导致快速双击结果错误
    @Query("UPDATE notes SET isPinned = NOT isPinned WHERE id = :id")
    suspend fun togglePin(id: Long)
}
