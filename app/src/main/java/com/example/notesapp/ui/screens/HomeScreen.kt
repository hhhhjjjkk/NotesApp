package com.example.notesapp.ui.screens

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.notesapp.R
import com.example.notesapp.data.Note
import com.example.notesapp.ui.components.EmptyState
import com.example.notesapp.ui.components.NoteCard
import com.example.notesapp.ui.components.SearchBar
import com.example.notesapp.ui.screens.home.HomeBottomBar
import com.example.notesapp.ui.screens.home.HomeNoteSheet
import com.example.notesapp.ui.screens.home.HomeSelectionBar
import com.example.notesapp.ui.viewmodel.NotesViewModel
import com.example.notesapp.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * 搜索栏占位高度：OutlinedTextField 默认 56dp + SearchBar 自身上下各 8dp 内边距 = 72dp。
 * 列表顶部留白与此保持一致，避免硬编码数值分散在各处导致错位。
 */
private val SearchBarHeight = 72.dp

/**
 * 底部滑块行占位高度：滑块 44dp + 上下外边距 16dp*2 = 76dp。
 * 用于 Snackbar 抬升与空状态底部避让，避免提示被滑块遮挡。
 */
private val BottomBarHeight = 76.dp

/**
 * 首页：搜索 + 笔记瀑布流 + 类型切换滑块 + 多选删除。
 * 本文件只负责「骨架组合」；多选逻辑、底部菜单、底部栏分别在
 * [HomeSelectionBar] / [HomeNoteSheet] / [HomeBottomBar]。
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
fun HomeScreen(
    viewModel: NotesViewModel,
    settingsViewModel: SettingsViewModel,
    onNoteClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTrashClick: () -> Unit,
    /** 每次「重新回到首页」时该值应变化，用于重播底部滑块上浮动画（详见 HomeBottomBar）。 */
    replayKey: Any? = Unit
) {
    val context = LocalContext.current
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val searchQuery = viewModel.currentSearchQuery
    val noteType by viewModel.noteType.collectAsStateWithLifecycle()

    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val cardRadius by settingsViewModel.cardRadius.collectAsStateWithLifecycle()
    val cardShadow by settingsViewModel.cardShadow.collectAsStateWithLifecycle()
    val cardTransparency by settingsViewModel.cardTransparency.collectAsStateWithLifecycle()
    val isDark = when (themeMode) {
        com.example.notesapp.data.ThemeMode.LIGHT -> false
        com.example.notesapp.data.ThemeMode.DARK -> true
        com.example.notesapp.data.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ===== 多选模式 =====
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // 长按选中的笔记，用于底部弹窗
    var sheetNote by remember { mutableStateOf<Note?>(null) }

    // 底部栏入场动画状态（重播逻辑在 HomeBottomBar 内，注释见彼处）
    val bottomBarVisible = remember { MutableTransitionState(false) }

    fun deleteWithUndo(note: Note, message: String) {
        viewModel.deleteNote(note)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete(note)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectionMode) "已选 ${selectedIds.size} 项"
                        else stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Medium),
                        color = if (selectionMode) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.animateContentSize(animationSpec = tween(250))
                    )
                },
                actions = {
                    if (selectionMode) {
                        // 退出选择模式
                        IconButton(onClick = {
                            selectionMode = false
                            selectedIds = emptySet()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "取消",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else {
                        // 进入编辑/选择模式
                        IconButton(onClick = { selectionMode = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "编辑",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        // 回收站入口
                        IconButton(onClick = onTrashClick) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = stringResource(R.string.trash),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (selectionMode) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = if (selectionMode) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = {
            // 向上抬升，避开底部切换滑块（滑块 44dp + 上下 16dp*2 外边距），
            // 与 BottomBarHeight 保持一致，避免提示被滑块遮挡。
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = BottomBarHeight)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 搜索栏固定为顶部浮层：抬高层级，避免列表向上滚动时卡片文字从下方穿透。
            // 自身用半透明玻璃底色（见 SearchBar），既遮挡穿透内容又保留壁纸观感。
            SearchBar(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                isDark = isDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
            )

            if (notes.isEmpty()) {
                EmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        // 上下同时避让搜索栏与底部滑块，使空状态在剩余区域内居中
                        .padding(top = SearchBarHeight + 16.dp, bottom = BottomBarHeight)
                )
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(160.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = SearchBarHeight + 12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalItemSpacing = 8.dp
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            isDark = isDark,
                            radiusDp = cardRadius,
                            shadowEnabled = cardShadow,
                            selectionMode = selectionMode,
                            isSelected = note.id in selectedIds,
                            transparency = cardTransparency,
                            onClick = {
                                if (selectionMode) {
                                    selectedIds = if (note.id in selectedIds) {
                                        selectedIds - note.id
                                    } else {
                                        selectedIds + note.id
                                    }
                                } else {
                                    onNoteClick(note.id)
                                }
                            },
                            onLongClick = { sheetNote = note },
                            onSwipeDelete = {
                                deleteWithUndo(note, context.getString(R.string.note_deleted))
                            },
                            onComplete = {
                                deleteWithUndo(note, context.getString(R.string.todo_completed))
                            },
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
            }

            // 底部滑块 + 添加按钮（动画重播逻辑见 HomeBottomBar 的踩坑注释）
            HomeBottomBar(
                visibleState = bottomBarVisible,
                replayKey = replayKey,
                selectionMode = selectionMode,
                noteType = noteType,
                onNoteTypeChange = { viewModel.setNoteType(it) },
                isDark = isDark,
                onAddClick = onAddClick,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // 多选模式下的底部悬浮删除栏
            HomeSelectionBar(
                visible = selectionMode && selectedIds.isNotEmpty(),
                selectedCount = selectedIds.size,
                onClearSelection = { selectedIds = emptySet() },
                onDeleteSelected = {
                    val toDelete = notes.filter { it.id in selectedIds }
                    toDelete.forEach { viewModel.deleteNote(it) }
                    val count = toDelete.size
                    selectedIds = emptySet()
                    selectionMode = false
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = context.getString(R.string.notes_deleted, count),
                            actionLabel = context.getString(R.string.undo),
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            toDelete.forEach { viewModel.undoDelete(it) }
                        }
                    }
                },
                isDark = isDark,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // 长按弹出的底部操作菜单
    HomeNoteSheet(
        note = sheetNote,
        viewModel = viewModel,
        onNoteCopied = {
            sheetNote = null
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "已复制到剪贴板",
                    duration = SnackbarDuration.Short
                )
            }
        },
        onDismiss = { sheetNote = null }
    )
}
