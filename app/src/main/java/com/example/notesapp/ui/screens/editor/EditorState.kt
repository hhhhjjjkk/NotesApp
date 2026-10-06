package com.example.notesapp.ui.screens.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.notesapp.data.DocumentBlock
import com.example.notesapp.data.RichDocumentCodec

/**
 * 编辑页的全部可变状态。
 *
 * 为什么要集中在这里：原先这些状态散落在 EditorScreen composable 的十几行
 * rememberSaveable 里，与「选图 / 保存 / 提醒」三块业务逻辑互相穿插，
 * 改任何一处都要通读 675 行。集中后各控制器只依赖自己需要的字段。
 *
 * 全部字段沿用原先的 rememberSaveable 语义（旋转屏后不丢），
 * 通过 [Saver]（见伴生对象）整体保存/恢复，行为与逐字段 saveable 一致。
 */
@Stable
class EditorState(
    initialTitle: String = "",
    initialBlocks: List<DocumentBlock> = listOf(DocumentBlock.text("")),
    initialSelectedColor: Int = 0,
    initialIsPinned: Boolean = false,
    initialReminderAt: Long = 0L,
    initialNoteLoaded: Boolean = false,
    initialIsTrashedNote: Boolean = false,
    initialFocusBlockId: String? = null,
    initialIsExiting: Boolean = false
) {
    var title: String by mutableStateOf(initialTitle)

    /** 图文混排文档：文字块与图片块按顺序排列，序列化保存，旋转屏不丢。 */
    var blocks: List<DocumentBlock> by mutableStateOf(initialBlocks)

    var selectedColor: Int by mutableStateOf(initialSelectedColor)
    var isPinned: Boolean by mutableStateOf(initialIsPinned)
    var reminderAt: Long by mutableStateOf(initialReminderAt)
    var noteLoaded: Boolean by mutableStateOf(initialNoteLoaded)

    /** 当前编辑的笔记是否在回收站（通知深链可能直达），只读展示。 */
    var isTrashedNote: Boolean by mutableStateOf(initialIsTrashedNote)

    /** 需要自动聚焦的文字块 id：新插入的文字段落要能立刻打字。 */
    var focusBlockId: String? by mutableStateOf(initialFocusBlockId)

    /** 退出标志：返回触发后停止自动保存，避免与 saveAndExit 竞态导致重复保存。 */
    var isExiting: Boolean by mutableStateOf(initialIsExiting)

    // ===== 派生值（始终与 blocks 同步，无需手动维护） =====

    /** 纯文本版本（搜索 / 分享 / 首页预览用），由块列表派生。 */
    val content: String get() = RichDocumentCodec.plainText(blocks)

    /** 字数 = 正文 + 标题。 */
    val wordCount: Int get() = content.length + title.length

    /** 笔记是否有内容：纯文本非空，或至少含一张图片。 */
    fun hasContent(): Boolean =
        title.isNotBlank() || content.isNotBlank() || blocks.any { it is DocumentBlock.Image }

    /** 从已有笔记填充全部状态（深链/编辑进入时调用一次）。 */
    fun loadFrom(
        title: String,
        blocks: List<DocumentBlock>,
        color: Int,
        pinned: Boolean,
        reminderAt: Long,
        isTrashed: Boolean
    ) {
        this.title = title
        this.blocks = blocks
        this.selectedColor = color
        this.isPinned = pinned
        this.reminderAt = reminderAt
        this.isTrashedNote = isTrashed
        this.noteLoaded = true
    }

    companion object {
        /**
         * Saver：把状态对象按固定顺序写入列表再按同顺序读出。
         * 顺序必须与下方恢复逻辑一一对应——新增字段时两端都要同步加。
         */
        // 不写显式类型注解：listSaver 返回 Saver<T, out Any?>，
        // 标注成 Saver<EditorState, Any> 会类型不匹配，进而让
        // rememberSaveable 的重载解析失败（报成 MutableState 相关的怪错）。
        // 用显式 Saver 构造，而不是 listSaver：
        // rememberSaveable 有两个重载（init 返回 T 或 MutableState<T>），
        // listSaver 推断出的 Saver<EditorState, Any?> 会让两个重载都大致匹配，
        // 编译器最终落到 MutableState 版本并报出难懂的类型错误。
        // 显式标注 Saver<EditorState, Any> 后只可能匹配 () -> T 重载。
        val Saver: Saver<EditorState, Any> = Saver(
            save = { state ->
                listOf(
                    state.title,
                    RichDocumentCodec.encode(state.blocks), // 借用编解码器序列化块列表
                    state.selectedColor,
                    state.isPinned,
                    state.reminderAt,
                    state.noteLoaded,
                    state.isTrashedNote,
                    state.focusBlockId,
                    state.isExiting
                )
            },
            restore = { values ->
                // 恢复必须防御：升级安装等极端情况下，进程里可能残留旧版本格式
                // 的保存状态。按下标取值，格式不匹配会抛 ClassCastException /
                // IndexOutOfBounds——直接崩在启动页。这里兜底为全新空白状态，
                // 代价只是丢一次未保存的草稿，远好于应用打不开。
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    val list = values as List<Any?>
                    EditorState(
                        initialTitle = list[0] as String,
                        initialBlocks = RichDocumentCodec.decode(list[1] as String, ""),
                        initialSelectedColor = list[2] as Int,
                        initialIsPinned = list[3] as Boolean,
                        initialReminderAt = list[4] as Long,
                        initialNoteLoaded = list[5] as Boolean,
                        initialIsTrashedNote = list[6] as Boolean,
                        initialFocusBlockId = list[7] as String?,
                        initialIsExiting = list[8] as Boolean
                    )
                }.getOrDefault(EditorState())
            }
        )
    }
}

/** 记住一个跨旋转屏存活的 [EditorState]。 */
@Composable
fun rememberEditorState(): EditorState {
    // 与原实现一致的 saveable 模式：init 返回 MutableState<T>，
    // 由调用方用 by 委托读写。（直接 init 返回 EditorState 会命中
    // rememberSaveable 的另一个重载，引发难懂的类型错误。）
    val state = rememberSaveable(stateSaver = EditorState.Saver) {
        androidx.compose.runtime.mutableStateOf(EditorState())
    }
    return state.value
}
