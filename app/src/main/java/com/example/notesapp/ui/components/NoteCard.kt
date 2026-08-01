package com.example.notesapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    isDark: Boolean,
    radiusDp: Int,
    shadowEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = note.color.toNoteColor(isDark)
    val contentColor = if (cardColor.isDark()) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val shape = RoundedCornerShape(radiusDp.dp)
    val (scaleModifier, interactionSource) = rememberPressableGlassScale(pressedScale = 0.97f)

    Card(
        modifier = modifier
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
        border = if (!shadowEnabled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    ) {
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
