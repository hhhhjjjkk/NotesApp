package com.example.notesapp.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    // 索引服务于 NoteDao 的两类高频查询：
    // 1. 首页列表：WHERE isTrashed=0 AND type=? ORDER BY isPinned DESC, updatedAt DESC
    //    —— 复合索引 (isTrashed, type, isPinned, updatedAt) 让过滤 + 排序全走索引，
    //       避免「先全表扫、再在内存排序」。
    // 2. 回收站：WHERE isTrashed=1 ORDER BY trashedAt DESC —— 前缀列复用 isTrashed。
    // 列顺序原则：等值过滤列在前（isTrashed、type），排序列在后（isPinned、updatedAt）。
    indices = [
        Index(value = ["isTrashed", "type", "isPinned", "updatedAt"]),
        Index(value = ["isTrashed", "trashedAt"])
    ]
)
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
