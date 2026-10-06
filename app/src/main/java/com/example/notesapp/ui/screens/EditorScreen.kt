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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.notesapp.NotesApplication
import com.example.notesapp.R
import com.example.notesapp.data.RichDocumentCodec
import com.example.notesapp.ui.components.RichDocumentEditor
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
    onBack: () -> Unit
) {
    val state = rememberEditorState()
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
    LaunchedEffect(Unit) {
        if (noteId == 0L) {
            state.focusBlockId = state.blocks.firstOrNull()?.id
        }
    }

    val save = EditorSaveEffects(
        state = state,
        viewModel = viewModel,
        existingNote = existingNote,
        noteId = noteId,
        currentType = currentType,
        onBack = onBack
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                EditorTitleField(
                    title = state.title,
                    onTitleChange = { state.title = it }
                )

                // 回收站只读横幅：通知深链可能直达已移入回收站的笔记。
                // 此时禁止编辑保存（避免覆盖回收站状态），提供一键恢复。
                TrashedBanner(
                    state = state,
                    existingNote = existingNote,
                    onRestored = { save.saveAndExit(currentType) }
                )

                ReminderBanner(state = state)

                RichDocumentEditor(
                    blocks = state.blocks,
                    onBlocksChange = { state.blocks = it },
                    imageStore = imageStore,
                    onAddTextAfterImage = { imageId -> imagePicker.insertTextAfterImage(imageId) },
                    onInsertImageBefore = { imageId -> imagePicker.insertBefore(imageLauncher, imageId) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
                    textColor = MaterialTheme.colorScheme.onBackground,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    hintText = stringResource(R.string.content_hint),
                    focusBlockId = state.focusBlockId,
                    focusRequester = null
                )
            }

            EditorBottomBar(
                isDark = isDark,
                selectedColor = state.selectedColor,
                onColorSelected = { state.selectedColor = it },
                onInsertImage = { imagePicker.requestInsert(imageLauncher, state.blocks.size) },
                hasReminder = state.reminderAt > 0L,
                onAlarmClick = { reminder.onAlarmClicked() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    EditorTimePickerDialog(controller = reminder)
}
