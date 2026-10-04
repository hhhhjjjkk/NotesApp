package com.example.notesapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "",
    val content: String = "", // 纯文本版本，用于搜索和分享
    val richContent: String = "", // 图文混排 JSON 序列化
    val color: Int = 0,
    val isPinned: Boolean = false,
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val type: Int = NoteType.NOTE,
    val isTrashed: Boolean = false,
    val trashedAt: Long = 0L,
    val reminderAt: Long = 0L
) {
    val tagList: List<String>
        get() = tags.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}

object NoteType {
    const val NOTE = 0
    const val TODO = 1
}
