package com.example.notesapp.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notesapp.data.Note
import com.example.notesapp.data.NoteRepository
import com.example.notesapp.data.NoteType
import com.example.notesapp.notification.NotificationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(
    private val repository: NoteRepository,
    private val app: Application
) : ViewModel() {

    companion object {
        const val ACTION_SEND = "android.intent.action.SEND"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
    }

    private val searchQuery = MutableStateFlow("")

    // 当前主视图类型：备忘录 / 代办，由首页滑块切换
    private val currentType = MutableStateFlow(NoteType.NOTE)
    val noteType: StateFlow<Int> = currentType

    // 首页列表：按当前 type 过滤，叠加搜索
    val notes: StateFlow<List<Note>> = combine(
        searchQuery,
        currentType,
        repository.getAllNotes()
    ) { query, type, allNotes ->
        val filtered = allNotes.filter { it.type == type }
        if (query.isBlank()) {
            filtered
        } else {
            filtered.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.content.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 全部未回收笔记（不按 type 过滤），供编辑页跨类型查找使用
    val allActiveNotes: StateFlow<List<Note>> = repository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 回收站列表
    val trashedNotes: StateFlow<List<Note>> = repository.getTrashedNotes()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var currentSearchQuery by mutableStateOf("")
        private set

    fun onSearchQueryChange(query: String) {
        currentSearchQuery = query
        searchQuery.value = query
    }

    fun setNoteType(type: Int) {
        currentType.value = type
    }

    fun saveNote(note: Note, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.saveNote(note)
            // 保存后调度或取消提醒闹钟
            val savedNote = note.copy(id = id)
            NotificationScheduler.schedule(app, savedNote)
            onSaved(id)
        }
    }

    // 软删除：移入回收站，可通过 undo 立即恢复
    fun deleteNote(note: Note, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.moveToTrash(note)
            // 移入回收站时取消提醒
            NotificationScheduler.cancel(app, note.id)
            onDeleted()
        }
    }

    // 撤销删除：把笔记恢复回主列表
    fun undoDelete(note: Note) {
        viewModelScope.launch {
            repository.restoreFromTrash(note)
            // 恢复时如果有未过期的提醒则重新调度
            if (note.reminderAt > System.currentTimeMillis()) {
                NotificationScheduler.schedule(app, note)
            }
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            // 原子翻转：避免基于陈旧 note 对象翻转导致快速双击结果错误
            repository.togglePin(note.id)
        }
    }

    // ===== 回收站操作 =====
    fun restoreFromTrash(note: Note) {
        viewModelScope.launch {
            repository.restoreFromTrash(note)
            if (note.reminderAt > System.currentTimeMillis()) {
                NotificationScheduler.schedule(app, note)
            }
        }
    }

    fun permanentlyDelete(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
            NotificationScheduler.cancel(app, note.id)
        }
    }

    fun clearTrashed() {
        viewModelScope.launch {
            // 取消所有回收站笔记的提醒
            trashedNotes.value.forEach { NotificationScheduler.cancel(app, it.id) }
            repository.clearTrashed()
        }
    }

    fun shareText(content: String, sendIntent: (android.content.Intent) -> Unit) {
        val intent = android.content.Intent(ACTION_SEND).apply {
            type = "text/plain"
            putExtra(EXTRA_TEXT, content)
        }
        sendIntent(intent)
    }
}
