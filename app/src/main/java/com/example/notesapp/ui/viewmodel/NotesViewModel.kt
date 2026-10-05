package com.example.notesapp.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notesapp.NotesApplication
import com.example.notesapp.data.Note
import com.example.notesapp.data.NoteRepository
import com.example.notesapp.data.RichDocumentCodec
import com.example.notesapp.data.NoteType
import com.example.notesapp.notification.NotificationScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(
    private val repository: NoteRepository,
    private val app: NotesApplication
) : ViewModel() {

    companion object {
        const val ACTION_SEND = "android.intent.action.SEND"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"

        /** 搜索输入防抖时长：避免每次按键都触发一次数据库查询。 */
        private const val SEARCH_DEBOUNCE_MS = 250L

        /**
         * 孤儿清理的宽限期：文件创建后 10 分钟内不清理。
         * 覆盖「已导入文件、但笔记尚未保存（自动保存 1.5s + 网络盘延迟）」的窗口期。
         */
        const val GRACE_PERIOD_MS = 10L * 60 * 1000
    }

    private val searchQuery = MutableStateFlow("")

    // 当前主视图类型：备忘录 / 代办，由首页滑块切换
    private val currentType = MutableStateFlow(NoteType.NOTE)
    val noteType: StateFlow<Int> = currentType

    // 首页列表：类型过滤与搜索全部下推到 SQL 执行，避免把整表读入内存再逐条过滤。
    //
    // 关键点：防抖不能直接作用在 searchQuery 上——那会让初始的空查询也延迟 250ms，
    // 导致冷启动首页短暂空白（明明有笔记）。这里用「先取首帧、再对后续变化防抖」的
    // 组合方式：初始值立即通过，只有用户真正输入时才进入防抖。
    // 类型切换（滑块）必须是即时的，不参与防抖。
    private val debouncedQuery: Flow<String> = searchQuery
        .debounce { query -> if (query.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }

    val notes: StateFlow<List<Note>> = combine(
        currentType,
        debouncedQuery
    ) { type, query -> type to query }
        .distinctUntilChanged()
        .flatMapLatest { (type, query) ->
            repository.getNotesByTypeAndQuery(type, query)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    /** 按 id 查询笔记（含回收站），供通知深链进入编辑页时兜底加载。 */
    suspend fun getNoteById(id: Long): Note? = repository.getNoteById(id)

    /** 从编辑页把回收站笔记恢复为正常笔记。 */
    fun restoreNote(note: Note, onRestored: () -> Unit = {}) {
        viewModelScope.launch {
            repository.restoreFromTrash(note)
            NotificationScheduler.schedule(app, note)
            onRestored()
        }
    }

    /**
     * 保存笔记。
     *
     * @param onSaved 保存成功后回调（id）
     * @param onFailed 目标笔记已不存在（被删除，返回 null）时回调；
     *                 不回调 [onSaved]，避免把「不存在的笔记」当成保存成功。
     */
    fun saveNote(note: Note, onSaved: (Long) -> Unit = {}, onFailed: () -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.saveNote(note)
            if (id == null) {
                onFailed()
                return@launch
            }
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
            cleanupImageOrphans()
        }
    }

    fun clearTrashed() {
        viewModelScope.launch {
            // 取消所有回收站笔记的提醒
            trashedNotes.value.forEach { NotificationScheduler.cancel(app, it.id) }
            repository.clearTrashed()
            cleanupImageOrphans()
        }
    }

    /**
     * 清理不再被任何笔记引用的图片文件。
     *
     * 安全约束（防止误删）：
     * - 引用集合来自**全部**笔记（活动 + 回收站）。回收站里的笔记仍可能被恢复，其图片必须保留
     * - 保留最近 [GRACE_PERIOD_MS] 内新增的文件：导入刚完成、笔记还没保存时，
     *   文件短暂处于「未被引用」状态，立即清理会把刚导入的图删掉
     * - 只在明确的删除动作后触发（永久删除 / 清空回收站 / 应用启动），
     *   不在每次保存后触发——撤销与自动保存并存时，短暂未引用不代表真的没人用
     */
    fun cleanupImageOrphans() {
        viewModelScope.launch {
            val referenced = mutableSetOf<String>()
            (allActiveNotes.value + trashedNotes.value).forEach { note ->
                referenced.addAll(
                    RichDocumentCodec.referencedImageFiles(
                        RichDocumentCodec.decode(note.richContent, note.content)
                    )
                )
            }
            app.imageStore.cleanupOrphans(referenced, gracePeriodMs = GRACE_PERIOD_MS)
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
