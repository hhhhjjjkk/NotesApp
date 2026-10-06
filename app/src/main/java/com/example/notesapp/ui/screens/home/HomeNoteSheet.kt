package com.example.notesapp.ui.screens.home

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.notesapp.R
import com.example.notesapp.data.DocumentBlock
import com.example.notesapp.data.Note
import com.example.notesapp.data.RichDocumentCodec
import com.example.notesapp.ui.viewmodel.NotesViewModel
import kotlinx.coroutines.launch

/**
 * 长按笔记弹出的底部操作菜单：置顶 / 分享 / 复制 / 删除。
 *
 * 用 let 安全捕获当前 note，避免 dismiss 过渡帧 sheetNote 已 null 时强解包 NPE。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeNoteSheet(
    note: Note?,
    viewModel: NotesViewModel,
    onNoteCopied: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    note?.let { current ->
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column {
                // 标题预览
                Text(
                    text = current.title.ifBlank { current.content.take(20).ifBlank { "备忘录" } },
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
                    title = stringResource(if (current.isPinned) R.string.unpin else R.string.pin),
                    onClick = {
                        viewModel.togglePin(current)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    }
                )

                // 分享：纯文本通道带不走图片，明确标注数量，避免接收方以为内容缺失
                BottomSheetItem(
                    icon = Icons.Default.IosShare,
                    title = stringResource(R.string.share),
                    onClick = {
                        val imageCount = RichDocumentCodec.decode(current.richContent, current.content)
                            .count { it is DocumentBlock.Image }
                        val shareText = buildString {
                            if (current.title.isNotBlank()) appendLine(current.title)
                            append(current.content)
                            if (imageCount > 0) {
                                if (isNotEmpty()) appendLine()
                                append("[包含 ${imageCount} 张图片]")
                            }
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
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
                            if (current.title.isNotBlank()) appendLine(current.title)
                            append(current.content)
                        })
                        clipboardManager.setPrimaryClip(clip)
                        scope.launch {
                            sheetState.hide()
                            onNoteCopied()
                        }
                    }
                )

                // 删除
                BottomSheetItem(
                    icon = Icons.Default.Delete,
                    title = stringResource(R.string.delete),
                    isDestructive = true,
                    onClick = {
                        viewModel.deleteNote(current)
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    }
                )

                // 底部安全间距
                androidx.compose.foundation.layout.Box(modifier = Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

/** 菜单项：图标 + 文案；isDestructive 时用 error 色标注危险操作。 */
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
