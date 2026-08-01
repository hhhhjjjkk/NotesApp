package com.example.notesapp.ui.screens

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.notesapp.R
import com.example.notesapp.data.Note
import com.example.notesapp.data.ThemeMode
import com.example.notesapp.ui.components.EmptyState
import com.example.notesapp.ui.components.NoteCard
import com.example.notesapp.ui.components.SearchBar
import com.example.notesapp.ui.theme.liquidGlassSurface
import com.example.notesapp.ui.theme.rememberPressableGlassScale
import com.example.notesapp.ui.viewmodel.NotesViewModel
import com.example.notesapp.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: NotesViewModel,
    settingsViewModel: SettingsViewModel,
    onNoteClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val notes by viewModel.notes.collectAsState()
    val searchQuery = viewModel.currentSearchQuery

    val themeMode by settingsViewModel.themeMode.collectAsState()
    val cardRadius by settingsViewModel.cardRadius.collectAsState()
    val cardShadow by settingsViewModel.cardShadow.collectAsState()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            // 液态玻璃球 FAB：半透明强调色 + 径向高光 + 玻璃边缘 + 按压形变
            val (scaleMod, interactionSource) = rememberPressableGlassScale(pressedScale = 0.88f)
            val primary = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .size(56.dp)
                    .then(scaleMod)
                    .shadow(8.dp, CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.92f),
                                primary.copy(alpha = 0.62f)
                            )
                        ),
                        shape = CircleShape
                    )
                    .liquidGlassSurface(
                        shape = CircleShape,
                        isDark = isDark,
                        borderWidth = 1.5.dp
                    )
                    .clickable(
                        interactionSource = interactionSource,
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth()
            )

            if (notes.isEmpty()) {
                EmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 80.dp)
                )
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(160.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 72.dp),
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
                            onClick = { onNoteClick(note.id) },
                            onLongClick = {
                                sheetNote = note
                                scope.launch { sheetState.show() }
                            },
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
            }
        }
    }

    // 长按弹出的底部操作菜单
    if (sheetNote != null) {
        ModalBottomSheet(
            onDismissRequest = { sheetNote = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            val note = sheetNote!!
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
                                viewModel.saveNote(deletedNote)
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
