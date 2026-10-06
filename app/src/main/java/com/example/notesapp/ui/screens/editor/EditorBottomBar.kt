package com.example.notesapp.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.notesapp.R
import com.example.notesapp.ui.components.ColorPicker
import com.example.notesapp.ui.theme.liquidGlassSurface

/**
 * 底部工具条：颜色选择 + 插图 + 提醒闹钟。
 *
 * 浮动玻璃条：通过 imePadding 跟随键盘自动浮起；
 * 底色用半透明 surface（主题层已换成玻璃底色），叠 [liquidGlassSurface]
 * 的顶部高光与发丝描边，形成悬浮工具条的玻璃质感。
 */
@Composable
fun EditorBottomBar(
    isDark: Boolean,
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    onInsertImage: () -> Unit,
    hasReminder: Boolean,
    onAlarmClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
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
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorPicker(
                selectedColor = com.example.notesapp.ui.theme.noteCardColors
                    .find { it.toArgb() == selectedColor }
                    ?: com.example.notesapp.ui.theme.noteCardColors.first(),
                onColorSelected = { onColorSelected(it.toArgb()) },
                modifier = Modifier.weight(1f)
            )
            // 在文档末尾插入图片（插入后自动补一个文字块，可直接继续写字）；
            // 想插到文档中部：长按目标位置的图片，在它前面插入
            IconButton(onClick = onInsertImage) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = stringResource(R.string.insert_image),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 提醒闹钟按钮：已有提醒时再次点击取消
            IconButton(onClick = onAlarmClick) {
                Icon(
                    imageVector = if (hasReminder) Icons.Default.AlarmOn else Icons.Default.AlarmOff,
                    contentDescription = stringResource(R.string.reminder),
                    tint = if (hasReminder) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
