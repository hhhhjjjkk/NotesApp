package com.example.notesapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.notesapp.R
import com.example.notesapp.ui.theme.toNoteColor

@Composable
fun PreviewCard(
    isDark: Boolean,
    radiusDp: Int,
    shadowEnabled: Boolean,
    cardColorValue: Int = 0,
    modifier: Modifier = Modifier
) {
    val cardColor = cardColorValue.toNoteColor(isDark)
    val contentColor = if (cardColor.isDark()) Color.White else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(radiusDp.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (shadowEnabled) 4.dp else 0.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.preview),
                style = MaterialTheme.typography.titleLarge,
                color = contentColor
            )
            Text(
                text = "这是一张示范卡片，实时反映你的设置效果。",
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private fun Color.isDark(): Boolean {
    val luminance = 0.299 * red + 0.587 * green + 0.114 * blue
    return luminance < 0.5
}
