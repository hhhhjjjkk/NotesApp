package com.example.notesapp.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notesapp.R

/**
 * 标题输入框。
 * 视觉与行为与重构前一致：22sp 加粗、单行、主色光标、空态显示占位提示。
 */
@Composable
fun EditorTitleField(
    title: String,
    onTitleChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = title,
        onValueChange = onTitleChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
        textStyle = TextStyle(
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        ),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            if (title.isEmpty()) {
                Text(
                    text = stringResource(R.string.title_hint),
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            innerTextField()
        }
    )
}

/**
 * 回收站只读横幅。
 *
 * 通知深链可能直达已移入回收站的笔记：此时禁止编辑保存
 * （保存会覆盖回收站状态），提供一键恢复。
 * 恢复后按当前编辑内容保存为新状态并退出只读。
 *
 * 注意：state.isTrashedNote 只反映「进入时」的回收站状态，恢复动作由
 * 调用方通过 [onRestored] 完成（内部走 viewModel.restoreNote + 保存链路），
 * 这里不直接触碰 ViewModel，保持本组件纯 UI。
 */
@Composable
fun TrashedBanner(
    state: EditorState,
    existingNote: com.example.notesapp.data.Note?,
    onRestored: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.isTrashedNote) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.trashed_readonly),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.restore),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onRestored() }
        )
    }
}
