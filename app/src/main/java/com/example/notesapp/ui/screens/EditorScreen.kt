package com.example.notesapp.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.notesapp.NotesApplication
import com.example.notesapp.R
import com.example.notesapp.notification.NotificationScheduler
import com.example.notesapp.data.DocumentBlock
import com.example.notesapp.data.RichDocumentCodec
import com.example.notesapp.ui.components.RichDocumentEditor
import com.example.notesapp.ui.components.SharedExpandBox
import com.example.notesapp.ui.screens.editor.EditorBottomBar
import com.example.notesapp.ui.screens.editor.EditorTimePickerDialog
import com.example.notesapp.ui.screens.editor.EditorTitleField
import com.example.notesapp.ui.screens.editor.EditorTopBar
import com.example.notesapp.ui.screens.editor.ReminderBanner
import com.example.notesapp.ui.screens.editor.TrashedBanner
import com.example.notesapp.ui.screens.editor.rememberEditorImagePicker
import com.example.notesapp.ui.screens.editor.rememberEditorReminder
import com.example.notesapp.ui.screens.editor.rememberEditorState
import com.example.notesapp.ui.screens.editor.EditorSaveEffects
import com.example.notesapp.ui.theme.isDarkColor
import com.example.notesapp.ui.viewmodel.NotesViewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

/**
 * 编辑页。
 *
 * 组合关系：
 * - [EditorState]：全部可变状态（标题/块列表/颜色/置顶/提醒…），旋转屏存活
 * - [rememberEditorImagePicker]：选图与插入
 * - [rememberEditorReminder]：通知权限、日期/时间选择
 * - [EditorSaveEffects]：返回保存、自动保存、删除
 * - [EditorTopBar] / [EditorBottomBar] / [ReminderBanner]：纯 UI 区块
 *
 * 对外签名与重构前一致：EditorScreen(viewModel, noteId, onBack)。
 */
@Composable
fun EditorScreen(
    viewModel: NotesViewModel,
    noteId: Long,
    onBack: () -> Unit,
    /** 共享元素展开起点（相对全屏矩形比例）；null 表示不播展开动画（通知深链冷启动） */
    expandFrom: androidx.compose.ui.geometry.Rect? = null
) {
    val state = rememberEditorState()
    // 关闭动画状态：置 true 后 SharedExpandBox 反向收拢，动画结束再真正 pop
    var closing by remember { mutableStateOf(false) }
    // 退出时「动画已播完」与「数据已保存完」是两个并行条件，都满足才真正 pop。
    // ⚠️ v3.12.1：此前是先等 saveAndExit 保存完才启动动画，
    // 保存耗时几十~几百毫秒期间画面完全静止 → 用户感知为「返回动画拖拉」。
    // 现在点击立即启动收拢动画，保存并行进行，两者都完成才退出。
    var saveDone by remember { mutableStateOf(false) }
    var animDone by remember { mutableStateOf(false) }
    fun tryPop() {
        if ((animDone || expandFrom == null) && saveDone) onBack()
    }

    // ===== 内容跟随页面展开（v3.11.8）=====
    // 不再给文字单独的浮动动画：此前 titleShiftY/bodyShiftY 与页面的展开运动
    // 方向叠加，导致打开/关闭时文字「明明页面在放大、文字却在乱飘」。
    // 现在文字完全由 SharedExpandBox 的展开进度 p 驱动，与页面同步：
    //   打开 p:0→1 文字随页面一起向上铺开到位；关闭 p:1→0 随页面一起降回卡片。
    val bodyFocusRequester = remember { FocusRequester() }
    val titleFocusRequester = remember { FocusRequester() }
    // 显式唤起输入法：Compose 里获得焦点并不等于键盘弹出
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val imageStore = remember(context) {
        (context.applicationContext as NotesApplication).imageStore
    }
    val reminder = rememberEditorReminder(state)
    val (imagePicker, imageLauncher) = rememberEditorImagePicker(state, imageStore)

    val notes by viewModel.allActiveNotes.collectAsStateWithLifecycle()
    val currentType by viewModel.noteType.collectAsStateWithLifecycle()
    val existingNote = remember(noteId, notes) { notes.find { it.id == noteId } }

    // ===== 笔记加载 =====
    // key 含 existingNote：冷启动经通知深链直达编辑页时，allActiveNotes 首帧为空，
    // existingNote 为 null；待 DB 发射、existingNote 变非空后 effect 需重跑加载。
    LaunchedEffect(noteId, existingNote) {
        if (!state.noteLoaded && existingNote != null) {
            state.isTrashedNote = existingNote.isTrashed
            state.loadFrom(
                title = existingNote.title,
                blocks = RichDocumentCodec.decode(existingNote.richContent, existingNote.content),
                color = existingNote.color,
                pinned = existingNote.isPinned,
                reminderAt = existingNote.reminderAt,
                isTrashed = existingNote.isTrashed
            )
        }
    }

    // 数据库直达查询兜底：深链指向回收站笔记时 allActiveNotes 查不到（只含未删除的），
    // 需单独按 id 查询，才能显示「已移入回收站」而不是当作空白笔记。
    LaunchedEffect(noteId, state.noteLoaded) {
        if (!state.noteLoaded && noteId != 0L) {
            val found = viewModel.getNoteById(noteId) ?: return@LaunchedEffect
            state.isTrashedNote = found.isTrashed
            state.loadFrom(
                title = found.title,
                blocks = RichDocumentCodec.decode(found.richContent, found.content),
                color = found.color,
                pinned = found.isPinned,
                reminderAt = found.reminderAt,
                isTrashed = found.isTrashed
            )
        }
    }

    // 新建笔记时自动聚焦正文（交给编辑器按 focusBlockId 挂载并聚焦）
    //
    // ⚠️ 必须等转场结束再聚焦（v3.10.6）：转场期间弹键盘会立刻改变 imePadding，
    // 整页内容同时上移 + 键盘弹出，把打开动画彻底盖掉——用户看到的只有「页面乱跳」，
    // 表现为「打开根本没有动画」。延后到转场时长之后再聚焦，两者互不干扰。
    LaunchedEffect(Unit) {
        if (noteId == 0L) {
            delay(520)
            state.focusBlockId = state.blocks.firstOrNull()?.id
        }
    }

    val save = EditorSaveEffects(
        state = state,
        viewModel = viewModel,
        existingNote = existingNote,
        noteId = noteId,
        currentType = currentType,
        // 顶栏按钮与系统返回手势共用：退出一触发就立刻启动收拢动画
        onExitStart = {
            if (expandFrom != null && !closing) closing = true else animDone = true
        },
        // 数据落库后不直接 pop：等动画结束的 onClosed 再返回
        onBack = {
            // 保存完成。若动画还没播完，等动画结束的 onClosed 再 pop
            saveDone = true
            tryPop()
        }
    )

    Scaffold(
        topBar = {
            EditorTopBar(
                state = state,
                isNewNote = noteId == 0L,
                wordCount = state.wordCount,
                onBack = { save.saveAndExit(currentType) },
                onDelete = { save.deleteAndExit() }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val isDark = MaterialTheme.colorScheme.background.isDarkColor()

        SharedExpandBox(
            from = expandFrom,
            closing = closing,
            onClosed = {
                animDone = true
                tryPop()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { expandProgress ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                // 文字只做淡入：整页已在从下方滑入，文字**不应再有自己的位移**，
                // 否则两段运动叠加会互相打架（之前「文字乱飘」的根因）。
                Box(
                    modifier = Modifier.graphicsLayer {
                        alpha = 0.6f + 0.4f * expandProgress
                    }
                ) {
                    EditorTitleField(
                        title = state.title,
                        onTitleChange = { state.title = it },
                        focusRequester = titleFocusRequester,
                        onRequestKeyboard = {
                            titleFocusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    )
                }

                // 回收站只读横幅：通知深链可能直达已移入回收站的笔记。
                // 此时禁止编辑保存（避免覆盖回收站状态），提供一键恢复。
                TrashedBanner(
                    state = state,
                    existingNote = existingNote,
                    onRestored = { save.saveAndExit(currentType) }
                )

                ReminderBanner(
                    state = state,
                    onCancel = {
                        // 已调度的闹钟必须同步撤销：只清 reminderAt 的话，
                        // 杀进程后闹钟仍会触发并弹出已取消的提醒。
                        if (noteId > 0L) NotificationScheduler.cancel(context, noteId)
                        state.reminderAt = 0L
                    }
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            alpha = 0.6f + 0.4f * expandProgress
                        }
                ) {
                    RichDocumentEditor(
                        blocks = state.blocks,
                        onBlocksChange = { state.blocks = it },
                        // 图片块拖动重排：与目标位置的相邻块交换，文字自动让位不被覆盖。
                        onReorderImage = { imageId, direction ->
                            val list = state.blocks.toMutableList()
                            val from = list.indexOfFirst { it.id == imageId }
                            val to = from + direction
                            if (from >= 0 && to in list.indices) {
                                val moved = list.removeAt(from)
                                list.add(to, moved)
                                state.blocks = list
                            }
                        },
                        imageStore = imageStore,
                        onAddTextAfterImage = { imageId -> imagePicker.insertTextAfterImage(imageId) },
                        onInsertImageBefore = { imageId -> imagePicker.insertBefore(imageLauncher, imageId) },
                        onBlankAreaClick = {
                            val lastTextBlock = state.blocks.lastOrNull() as? DocumentBlock.Text
                            val targetBlock = lastTextBlock ?: DocumentBlock.text("")
                            if (lastTextBlock == null) {
                                state.blocks = state.blocks + targetBlock
                            }
                            state.focusBlockId = targetBlock.id
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
                        textColor = MaterialTheme.colorScheme.onBackground,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        hintText = stringResource(R.string.content_hint),
                        focusBlockId = state.focusBlockId,
                        focusRequester = bodyFocusRequester
                    )
                }
            }

            EditorBottomBar(
                isDark = isDark,
                selectedColor = state.selectedColor,
                onColorSelected = { state.selectedColor = it },
                onInsertImage = { imagePicker.requestInsert(imageLauncher, state.blocks.size) },
                hasReminder = state.reminderAt > 0L,
                onAlarmClick = {
                    // 再次点击 = 取消：先撤销已调度闹钟，再走控制器的取消逻辑
                    if (state.reminderAt > 0L && noteId > 0L) {
                        NotificationScheduler.cancel(context, noteId)
                    }
                    reminder.onAlarmClicked()
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    EditorTimePickerDialog(controller = reminder)
}