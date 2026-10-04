package com.example.notesapp.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.notesapp.R
import com.example.notesapp.data.Note
import com.example.notesapp.data.ThemeMode
import com.example.notesapp.ui.components.EmptyState
import com.example.notesapp.ui.components.LiquidSegmentedSlider
import com.example.notesapp.ui.components.NoteCard
import com.example.notesapp.ui.components.SearchBar
import com.example.notesapp.ui.theme.liquidGlassSurface
import com.example.notesapp.ui.theme.rememberPressableGlassScale
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: NotesViewModel,
    settingsViewModel: SettingsViewModel,
    onNoteClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTrashClick: () -> Unit,
    /**
     * 每次「重新回到首页」时该值应发生变化，用于重播底部滑块的上浮动画。
     * 因为返回时 HomeScreen 的 composition 被保留（不重建），必须由外部显式告知，
     * 否则动画不会重新播放。传入 back stack entry 的 id 即可满足。
     */
    replayKey: Any? = Unit
) {
    val context = LocalContext.current
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val searchQuery = viewModel.currentSearchQuery
    val noteType by viewModel.noteType.collectAsStateWithLifecycle()

    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val backgroundUri by settingsViewModel.backgroundUri.collectAsStateWithLifecycle()
    val hasCustomBackground = !backgroundUri.isNullOrBlank()
    val cardRadius by settingsViewModel.cardRadius.collectAsStateWithLifecycle()
    val cardShadow by settingsViewModel.cardShadow.collectAsStateWithLifecycle()
    val cardTransparency by settingsViewModel.cardTransparency.collectAsStateWithLifecycle()
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemDark
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()


    // 长按选中的笔记，用于底部弹窗
    var sheetNote by remember { mutableStateOf<Note?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 多选删除模式
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

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
        floatingActionButton = {
            // FAB 与底部滑块同处一个 Row，此处刻意留空
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
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 搜索栏固定为顶部浮层：抬高层级，避免列表向上滚动时卡片文字从下方穿透。
            // 默认纯色背景时加不透明底色遮挡穿透内容；自定义壁纸时保持透明，
            // 以保留毛玻璃观感，不与背景层产生冲突。
            SearchBar(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        // 自定义壁纸下保持透明以保留毛玻璃观感
                        if (hasCustomBackground) Modifier
                        else Modifier.background(MaterialTheme.colorScheme.background)
                    )
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
                // 顶部让出搜索栏高度，并额外留 12dp 呼吸间距，避免首行卡片贴住搜索栏
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
                            onLongClick = {
                                sheetNote = note
                                scope.launch { sheetState.show() }
                            },
                            onSwipeDelete = {
                                val deletedNote = note
                                viewModel.deleteNote(deletedNote)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.note_deleted),
                                        actionLabel = context.getString(R.string.undo),
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoDelete(deletedNote)
                                    }
                                }
                            },
                            onComplete = {
                                val completedNote = note
                                viewModel.deleteNote(completedNote)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.todo_completed),
                                        actionLabel = context.getString(R.string.undo),
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoDelete(completedNote)
                                    }
                                }
                            },
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
            }

            // 底部滑块 + 添加按钮：直接锚定到内容 Box 底部，无外层包裹。
            //
            // 背景：Compose Navigation 用 getVisibleEntries() 驱动 AnimatedContent，
            // 且内容由 SaveableStateHolder/LocalOwnersProvider 承载，因此从二级页返回时
            // HomeScreen 的 composition 是「被保留」的，remember 值不会重置，
            // visible = !selectionMode 也自始至终没有变化 —— 所以 AnimatedVisibility
            // 不会播放 enter 动画（它只响应变化，不响应初始值）。
            //
            // 因此这里必须「主动重播」：以 replayKey 标记每一次重新回到首页，
            // 当其变化时先把状态压回 false（无动画地回到起点），再推入 true，
            // 人为制造一次真实的 false -> true 变化，从而触发上浮入场动画。
            // 单一 effect 同时负责「回到首页重播」与「多选模式跟随」，
            // 拆成两个 effect 会在首次组合时互相覆盖 targetState，产生竞态。
            val bottomBarVisible = remember { MutableTransitionState(false) }
            LaunchedEffect(replayKey, selectionMode) {
                if (!selectionMode) {
                    // 回到首页：先压回 false 并等一帧，确保 AnimatedVisibility 观察到该变化，
                    // 否则同一帧内 false -> true 会被合并，动画不会播放。
                    bottomBarVisible.targetState = false
                    withFrameNanos { }
                }
                bottomBarVisible.targetState = !selectionMode
            }

            AnimatedVisibility(
                visibleState = bottomBarVisible,
                enter = slideInVertically(
                    animationSpec = tween(350),
                    initialOffsetY = { it }
                ) + fadeIn(animationSpec = tween(350)),
                exit = slideOutVertically(
                    animationSpec = tween(300),
                    targetOffsetY = { it }
                ) + fadeOut(animationSpec = tween(300)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    LiquidSegmentedSlider(
                        selected = noteType,
                        onSelected = { viewModel.setNoteType(it) },
                        leftLabel = stringResource(R.string.tab_note),
                        rightLabel = stringResource(R.string.tab_todo),
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                    val (scaleMod, fabSrc) = rememberPressableGlassScale(pressedScale = 0.92f)
                    val primary = MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .then(scaleMod)
                            .shadow(4.dp, CircleShape)
                            .background(
                                color = primary,
                                shape = CircleShape
                            )
                            .liquidGlassSurface(
                                shape = CircleShape,
                                isDark = isDark
                            )
                            .clickable(
                                interactionSource = fabSrc,
                                indication = null,
                                onClick = onAddClick
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.new_note),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // 多选模式下的底部悬浮删除栏：锚定到 Box 底部，圆角玻璃卡片
            AnimatedVisibility(
                visible = selectionMode && selectedIds.isNotEmpty(),
                enter = slideInVertically(
                    animationSpec = tween(350),
                    initialOffsetY = { it }
                ) + fadeIn(animationSpec = tween(350)),
                exit = slideOutVertically(
                    animationSpec = tween(300),
                    targetOffsetY = { it }
                ) + fadeOut(animationSpec = tween(300)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(28.dp),
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                            spotColor = Color.Black.copy(alpha = 0.16f)
                        )
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(28.dp)
                        )
                        .liquidGlassSurface(
                            shape = RoundedCornerShape(28.dp),
                            isDark = isDark
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .animateContentSize(animationSpec = tween(250)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 左侧：选中数量
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${selectedIds.size}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Text(
                            text = "已选 ${selectedIds.size} 项",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                    // 右侧：清空 + 删除
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "清空选择",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 删除按钮：强调色圆形背景
                        Box(
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .clickable {
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
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                }
            }
        }
    }

    // 长按弹出的底部操作菜单
    // 用 let 安全捕获当前 note，避免 dismiss 过渡帧 sheetNote 已 null 时强解包 NPE
    sheetNote?.let { note ->
        ModalBottomSheet(
            onDismissRequest = { sheetNote = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column {
                // 标题预览
                Text(
                    text = note.title.ifBlank { note.content.take(20).ifBlank { "备忘录" } },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 置顶/取消置顶
                BottomSheetItem(
                    icon = Icons.Default.PushPin,
                    title = stringResource(if (note.isPinned) R.string.unpin else R.string.pin),
                    onClick = {
                        viewModel.togglePin(note)
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            sheetNote = null
                        }
                    }
                )

                // 分享
                BottomSheetItem(
                    icon = Icons.Default.IosShare,
                    title = stringResource(R.string.share),
                    onClick = {
                        val shareText = buildString {
                            if (note.title.isNotBlank()) appendLine(note.title)
                            append(note.content)
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            sheetNote = null
                        }
                    }
                )

                // 复制内容
                BottomSheetItem(
                    icon = Icons.Default.ContentCopy,
                    title = "复制内容",
                    onClick = {
                        val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("note", buildString {
                            if (note.title.isNotBlank()) appendLine(note.title)
                            append(note.content)
                        })
                        clipboardManager.setPrimaryClip(clip)
                        scope.launch {
                            sheetState.hide()
                            sheetNote = null
                            snackbarHostState.showSnackbar(
                                message = "已复制到剪贴板",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                )

                // 删除
                BottomSheetItem(
                    icon = Icons.Default.Delete,
                    title = stringResource(R.string.delete),
                    isDestructive = true,
                    onClick = {
                        val deletedNote = note
                        viewModel.deleteNote(deletedNote)
                        scope.launch {
                            sheetState.hide()
                            sheetNote = null
                            // 显示撤销 Snackbar
                            val result = snackbarHostState.showSnackbar(
                                message = context.getString(R.string.note_deleted),
                                actionLabel = context.getString(R.string.undo),
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.undoDelete(deletedNote)
                            }
                        }
                    }
                )

                // 底部安全间距
                Box(modifier = Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

@Composable
private fun BottomSheetItem(
    icon: ImageVector,
    title: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (isDestructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface

    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(end = 16.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            modifier = Modifier.weight(1f)
        )
    }
}
