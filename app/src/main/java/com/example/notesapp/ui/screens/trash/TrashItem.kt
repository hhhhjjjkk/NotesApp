package com.example.notesapp.ui.screens.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.notesapp.R
import com.example.notesapp.data.Note
import com.example.notesapp.ui.theme.liquidGlassSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 回收站单条笔记：标题/正文预览 + 回收时间 + 恢复/永久删除按钮。
 * 从 TrashScreen 拆出的独立组件，行为与视觉与拆分前一致。
 */
@Composable
fun TrashItem(
    note: Note,
    isDark: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .liquidGlassSurface(shape = shape, isDark = isDark, borderWidth = 1.dp)
            .padding(14.dp)
    ) {
        Column {
            if (note.title.isNotBlank()) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (note.content.isNotBlank()) {
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (note.title.isNotBlank()) 4.dp else 0.dp)
                )
            }
            Text(
                text = formatTrashedDate(note.trashedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 恢复
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickableNoRipple(onRestore)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.trash_restore),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                // 永久删除
                Row(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(CircleShape)
                        .clickableNoRipple(onDelete)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.trash_delete),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

/** 无涟漪点击：回收站按钮是文字按钮，涟漪会溢出胶囊形，视觉上很脏。 */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.then(
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    )
}

private fun formatTrashedDate(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
