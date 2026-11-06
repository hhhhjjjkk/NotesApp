package com.example.notesapp.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import com.example.notesapp.NotesApplication
import com.example.notesapp.data.DocumentBlock
import com.example.notesapp.data.Note
import com.example.notesapp.data.NoteType
import com.example.notesapp.data.RichDocumentCodec
import com.example.notesapp.ui.theme.MarkdownBlock
import com.example.notesapp.ui.theme.liquidGlassSurface
import com.example.notesapp.ui.theme.parseMarkdown
import com.example.notesapp.ui.theme.rememberPressableGlassScale
import com.example.notesapp.ui.theme.toNoteColor
import androidx.compose.foundation.shape.RoundedCornerShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NoteCard(
    note: Note,
    isDark: Boolean,
    radiusDp: Int,
    shadowEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    /** 滑动删除回调。direction: -1 = 向左滑出，+1 = 向右滑出，0 = 非滑动手势 */
    onSwipeDelete: (direction: Int) -> Unit = {},
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    transparency: Float = 0f,
    onComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    // 安全转换：预览/测试场景下 applicationContext 可能不是 NotesApplication
    val imageStore = remember(context) {
        (context.applicationContext as? NotesApplication)?.imageStore
    }
    // 含图片的笔记：卡片上只显示第一张缩略图，避免列表项被撑得过高
    val firstImage = remember(note.richContent, note.content) {
        RichDocumentCodec.decode(note.richContent, note.content)
            .filterIsInstance<DocumentBlock.Image>()
            .firstOrNull()
    }

    val baseColor = note.color.toNoteColor(isDark)
    val contentColor = if (baseColor.isDark()) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val shape = RoundedCornerShape(radiusDp.dp)
    val (scaleModifier, interactionSource) = rememberPressableGlassScale(pressedScale = 0.97f)

    // 液态侧滑删除（v3.9.0 重写）：替代 SwipeToDismissBox 的整卡滑走。
    // 交互参考鸿蒙通知中心：卡片本体只轻微跟随，滑动侧长出圆球跟手，
    // 卡片与圆球之间有液态粘连，松手不足阈值时圆球 Q 弹回（低阻尼弹簧），
    // 超过阈值触发删除。左滑/右滑双向可用，选择模式下禁用（enabled=false）。
    LiquidSwipeDelete(
        enabled = !selectionMode,
        onConfirmDelete = { direction -> onSwipeDelete(direction) },
        deleteIcon = { DeleteBallIcon() },
        modifier = modifier
    ) {
        // 液态方案的删除指示就是圆球本身（Canvas 绘制 + 跟手图标），
        // 不再需要整片红色底板；卡片内容即下方 content。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(scaleModifier)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .liquidGlassSurface(
                    shape = shape,
                    isDark = isDark,
                    borderWidth = 0.dp,
                    highlight = 0f
                )
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                    } else {
                        Modifier
                    }
                )
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                    if (note.title.isNotBlank()) {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = contentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (note.content.isNotBlank()) {
                        val markdownBlocks = remember(note.content) { parseMarkdown(note.content) }
                        MarkdownRenderer(
                            blocks = markdownBlocks,
                            textColor = contentColor.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = if (note.title.isNotBlank()) 6.dp else 0.dp),
                            maxLines = 6
                        )
                    }
                    if (firstImage != null && imageStore != null) {
                        NoteImageThumbnail(
                            imageStore = imageStore,
                            image = firstImage,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }
                    Text(
                        text = formatDate(note.updatedAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                // 代办类型：右侧完成圆圈（非选择模式下显示），点击即完成并移入回收站
                if (note.type == NoteType.TODO && !selectionMode) {
                    TodoCompleteButton(
                        noteId = note.id,
                        onComplete = onComplete,
                        tint = contentColor,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(end = 14.dp)
                    )
                }
            }

            // 选择模式下右上角显示勾选圈
            if (selectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.TopEnd)
                        .padding(10.dp)
                        .size(24.dp)
                )
            }
        }
    }
}

@Composable
fun MarkdownRenderer(
    blocks: List<MarkdownBlock>,
    textColor: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    Column(modifier = modifier) {
        var currentLine = 0
        val totalBlocks = blocks.size

        for ((index, block) in blocks.withIndex()) {
            // 如果达到了最大行数限制，停止渲染
            if (currentLine >= maxLines) break

            when (block) {
                is MarkdownBlock.Heading -> {
                    if (currentLine < maxLines) {
                        Text(
                            text = block.text,
                            style = if (block.level == 1) {
                                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            } else {
                                MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            },
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = if (currentLine == 0) 0.dp else 4.dp)
                        )
                        currentLine++
                        if (currentLine < maxLines && index < totalBlocks - 1 && blocks[index + 1] !is MarkdownBlock.Blank) {
                            // 标题后加点间距
                        }
                    }
                }
                is MarkdownBlock.ListItem -> {
                    if (currentLine < maxLines) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
                            Text(
                                text = if (block.isOrdered) "${block.index}. " else "• ",
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            Text(
                                text = block.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        currentLine++
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    if (currentLine < maxLines) {
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor,
                            maxLines = maxLines - currentLine,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = if (currentLine == 0) 0.dp else 2.dp)
                        )
                        currentLine = maxLines // 段落文本占用剩余所有行数
                    }
                }
                is MarkdownBlock.Blank -> {
                    // 在卡片中忽略空行以节省空间
                }
            }
        }
    }
}

private fun Color.isDark(): Boolean {
    val luminance = 0.299 * red + 0.587 * green + 0.114 * blue
    return luminance < 0.5
}

/**
 * 代办完成按钮：右侧空心圆圈，点击后填充勾选并触发完成。
 * 用于代办类型笔记，与备忘录区分交互逻辑。
 */
@Composable
private fun TodoCompleteButton(
    noteId: Long,
    onComplete: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier
) {
    // 绑定 noteId：undo 恢复后复用同一槽位时，checked 不会残留为 true
    var checked by remember(noteId) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .border(
                width = 1.6.dp,
                color = tint.copy(alpha = if (checked) 0f else 0.55f),
                shape = CircleShape
            )
            .background(
                if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                else Color.Transparent
            )
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                if (!checked) {
                    checked = true
                    onComplete()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * 液态侧滑删除的唯一圆球：红色实心圆 + 白色垃圾桶。
 * 不再叠加 Canvas 白球，避免出现「两个球」。
 */
@Composable
private fun DeleteBallIcon() {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(
                color = MaterialTheme.colorScheme.error,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onError,
            modifier = Modifier.size(22.dp)
        )
    }
}
