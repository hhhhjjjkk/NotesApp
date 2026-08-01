package com.example.notesapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import com.example.notesapp.data.Note
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
    onSwipeDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    transparency: Float = 0f
) {
    val baseColor = note.color.toNoteColor(isDark)
    // 透明度开关：transparency=0 完全不透明；>0 时降低 alpha，让背景图透过来
    // 限制最大透明度 0.85，避免卡片内容不可读
    val cardColor = if (transparency > 0f) {
        baseColor.copy(alpha = (1f - transparency * 0.85f).coerceIn(0.15f, 1f))
    } else {
        baseColor
    }
    val contentColor = if (cardColor.isDark()) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val shape = RoundedCornerShape(radiusDp.dp)
    val (scaleModifier, interactionSource) = rememberPressableGlassScale(pressedScale = 0.97f)

    // 左滑删除：选择模式下禁用，避免误删
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onSwipeDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !selectionMode,
        backgroundContent = {
            // 红色删除背景跟随滑动进度渐变：
            // 直接读取 dismissState 的像素偏移自己计算进度，确保静止时 progress=0、
            // 背景完全透明，从而透过卡片显示自定义壁纸；滑动时红色渐进显现。
            val density = LocalDensity.current
            val progress by remember {
                derivedStateOf {
                    // requireOffset 在未 layout 时可能抛异常，安全降级为 0
                    val off = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
                    val maxPx = with(density) { 160.dp.toPx() }
                    (abs(off) / maxPx).coerceIn(0f, 1f)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = progress)
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = progress)
                )
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(scaleModifier)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .liquidGlassSurface(shape = shape, isDark = isDark, borderWidth = 1.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = shape,
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (shadowEnabled) 2.dp else 0.dp
            ),
            // 选中态用强调色描边，否则按原逻辑
            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            else if (!shadowEnabled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
        ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
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
                    val markdownBlocks = parseMarkdown(note.content)
                    MarkdownRenderer(
                        blocks = markdownBlocks,
                        textColor = contentColor.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = if (note.title.isNotBlank()) 6.dp else 0.dp),
                        maxLines = 6
                    )
                }
                Text(
                    text = formatDate(note.updatedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp)
                )
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

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
