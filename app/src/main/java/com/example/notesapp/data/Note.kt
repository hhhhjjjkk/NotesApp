package com.example.notesapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val color: Int = 0,
    val isPinned: Boolean = false,
    // 标签：逗号分隔字符串，如 "工作,灵感"。空串表示无标签。
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // 笔记类型：0=备忘录（NOTE），1=代办（TODO）
    val type: Int = NoteType.NOTE,
    // 是否在回收站中（软删除标记）
    val isTrashed: Boolean = false,
    // 移入回收站的时间戳，用于过期自动清理
    val trashedAt: Long = 0L,
    // 提醒时间戳（0L 表示无提醒）
    val reminderAt: Long = 0L
) {
    // 解析为标签列表（去空、去重，保持顺序）
    val tagList: List<String>
        get() = tags.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}

object NoteType {
    const val NOTE = 0
    const val TODO = 1
}
