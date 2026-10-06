package com.example.notesapp.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.notesapp.R
import com.example.notesapp.ui.theme.GlassDarkSurface
import com.example.notesapp.ui.theme.GlassLightSurface
import com.example.notesapp.ui.theme.liquidGlassSurface

/**
 * 顶部搜索栏。
 *
 * 用**半透明玻璃底色**而非不透明色：
 * - 搜索栏是浮在列表之上的固定层，必须有底色遮挡下方滚过的文字（否则可读性极差）
 * - 但如果用不透明纯色，就会在背景壁纸上挖出一块死板的矩形，破坏整体的玻璃观感
 * - 半透明底 + 玻璃高光/描边能同时满足「遮挡」与「通透」两个矛盾诉求
 *
 * @param isDark 用于挑选明暗两套玻璃配方；默认由主题推导，调用方可覆盖
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = MaterialTheme.colorScheme.background.luminanceIsDark()
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .liquidGlassSurface(
                shape = RoundedCornerShape(28.dp),
                isDark = isDark,
                borderWidth = 0.dp // 聚焦时由 OutlinedTextField 自己画强调色描边，避免双描边
            ),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        singleLine = true,
        // 形状由上面的 liquidGlassSurface 统一裁剪（已 clip 到 28dp 圆角），
        // 这里用 RectangleShape 避免两套圆角互相削角、边缘出现毛刺。
        shape = RectangleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = if (isDark) GlassDarkSurface else GlassLightSurface,
            unfocusedContainerColor = if (isDark) GlassDarkSurface else GlassLightSurface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/** 依据背景色亮度判断是否属于深色主题（兼容强制主题场景）。 */
private fun Color.luminanceIsDark(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f
