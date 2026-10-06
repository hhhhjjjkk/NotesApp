package com.example.notesapp.ui.screens.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.example.notesapp.R
import com.example.notesapp.data.Note
import com.example.notesapp.ui.viewmodel.NotesViewModel
import kotlinx.coroutines.delay

/**
 * 编辑页的保存链路：构建 Note、返回时保存、自动保存。
 *
 * ## 自动保存为什么必须带 noteLoaded 守卫
 * 冷启动经通知深链直达编辑页时，数据库首帧还是空的（allActiveNotes 未发射），
 * existingNote 为 null。若此时自动保存抢先执行，会把**空白内容写入并覆盖**已有笔记。
 * 因此 effect 里必须确认 noteLoaded == true（数据已真正加载进状态）才允许保存。
 *
 * ## 退出标志为什么先置位
 * 删除按钮触发时若不先置 isExiting，挂起的自动保存（1500ms 后写入）
 * 可能在删除完成后落地，把笔记重新写回——用户会看到笔记「复活」。
 */
class EditorSaveController(
    private val state: EditorState,
    private val viewModel: NotesViewModel,
    private val existingNote: Note?,
    private val noteId: Long,
    private val onBack: () -> Unit
) {
    /** 构建要保存的 Note。 */
    fun buildNote(currentType: Int): Note {
        // 新建笔记时按首页当前选中的类型（备忘录/代办）创建
        // 即使 existingNote 在编辑期间被外部回收，也保留 noteId，避免退化为新建
        val base = existingNote ?: Note(id = noteId, type = currentType)
        return base.copy(
            title = state.title.trim(),
            content = state.content,
            richContent = com.example.notesapp.data.RichDocumentCodec.encode(state.blocks),
            color = state.selectedColor,
            isPinned = state.isPinned,
            type = base.type,
            reminderAt = state.reminderAt,
            updatedAt = System.currentTimeMillis()
        )
    }

    /** 返回（或系统返回手势）：保存并退出。 */
    fun saveAndExit(currentType: Int) {
        if (state.isExiting) return  // 防止重复触发
        state.isExiting = true
        // 回收站笔记只读展示：退出时不保存（保存会通过 Repository 的 update 分支，
        // 虽然不会覆盖 isTrashed，但用户在回收站视图里的编辑本来就不该生效），
        // 也不弹失败提示——用户只是看了一眼笔记。
        if (state.isTrashedNote) {
            onBack()
            return
        }
        val note = buildNote(currentType)
        if (state.hasContent()) {
            viewModel.saveNote(
                note,
                onSaved = { onBack() },
                onFailed = {
                    // 目标笔记已不存在（如通知深链指向被删除的笔记）。
                    // 不保存任何内容，直接返回，避免覆盖或凭空重建。
                    val ctx = contextRef
                    if (ctx != null) {
                        Toast.makeText(
                            ctx,
                            ctx.getString(R.string.note_gone_readonly),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    onBack()
                }
            )
        } else {
            onBack()
        }
    }

    /** 删除当前笔记并返回。先置退出标志再删，防止自动保存竞态「复活」笔记。 */
    fun deleteAndExit() {
        state.isExiting = true
        existingNote?.let { viewModel.deleteNote(it) }
        onBack()
    }

    companion object {
        /**
         * saveAndExit 的 onFailed 回调需要 Toast 的 Context。
         * Controller 本身在 composable 里创建，这里用组合期注入而不是每次传参，
         * 保持调用点签名与重构前一致。
         */
        @Volatile
        internal var contextRef: android.content.Context? = null
    }
}

/**
 * 挂接保存链路：注入 Context、注册系统返回拦截、驱动自动保存 effect。
 */
@Composable
fun EditorSaveEffects(
    state: EditorState,
    viewModel: NotesViewModel,
    existingNote: Note?,
    noteId: Long,
    currentType: Int,
    onBack: () -> Unit
): EditorSaveController {
    val context = LocalContext.current
    val controller = androidx.compose.runtime.remember {
        EditorSaveController.contextRef = context
        EditorSaveController(state, viewModel, existingNote, noteId, onBack)
    }

    // 系统返回手势/按键：与顶栏返回按钮走同一条 saveAndExit 路径
    BackHandler { controller.saveAndExit(currentType) }

    // 自动保存：仅在编辑现有笔记且未退出时触发，避免与返回保存重复。
    // 以 blocks 为 key 触发，因此插入图片、拖拽改尺寸同样会触发自动保存。
    //
    // 必须带 noteLoaded：冷启动/深链进入时 DB 首帧是空的，existingNote 尚为 null，
    // 此时若抢先保存会把空白内容写入（并覆盖）已有笔记。
    // 必须带 !isTrashedNote：回收站笔记只读展示，自动保存会把 isTrashed 状态改回去。
    LaunchedEffect(state.title, state.blocks, state.selectedColor, state.isPinned, state.reminderAt) {
        if (noteId != 0L && !state.isExiting && state.noteLoaded && !state.isTrashedNote) {
            delay(1500)
            val note = controller.buildNote(currentType)
            if (state.hasContent()) {
                viewModel.saveNote(note) {}
            }
        }
    }

    return controller
}
