package com.example.notesapp.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.notesapp.R
import com.example.notesapp.ui.components.LiquidSegmentedSlider
import com.example.notesapp.ui.theme.liquidGlassSurface
import com.example.notesapp.ui.theme.rememberPressableGlassScale

/**
 * 底部栏：「备忘录/代办」切换滑块 + 新建按钮。
 *
 * ## 显隐策略（v3.11.2 定案，勿再加「返回首页重播」）
 * 底栏是**常驻元素**：只在进入/退出多选模式时淡入淡出，返回首页时不播任何动画。
 *
 * 历史踩坑（别再走回头路）：
 * - v3.3~v3.7.3 用 replayKey 翻转 targetState 重播，触发源不稳定（ON_RESUME 补发）
 *   导致重复重播，用户反馈「底栏一直向上飞、冲过屏顶」；
 * - v3.7.4 改成 remember(replayKey) 重建 TransitionState，虽然不再翻转，
 *   但重建会先打回 false（瞬间消失）→ 再淡入，与首页 homePopEnter 的淡入
 *   叠加成双重淡入，用户反馈「退出备忘录时底部滑块和 + 按钮会闪」。
 * - 结论：底栏不需要「返回时重播」这个需求本身。删掉即根治。
 */
@Composable
fun HomeBottomBar(
    visibleState: MutableTransitionState<Boolean>,
    selectionMode: Boolean,
    noteType: Int,
    onNoteTypeChange: (Int) -> Unit,
    isDark: Boolean,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 显隐只跟随多选模式。返回首页（replayKey 变化）不再触发任何重播——
    // 底栏是常驻元素，重播会与首页的 homePopEnter 淡入叠加成双重淡入，
    // 表现为「底部滑块与 + 按钮闪一下」。
    LaunchedEffect(selectionMode) {
        visibleState.targetState = !selectionMode
    }

    AnimatedVisibility(
        visibleState = visibleState,
        // 入场：纯淡入（v3.10.3 起）。此前 slideInVertically 的起始偏移取内容高度，
        // 返回首页时父容器还在淡入、底栏高度测量不稳定，偏移会被重映射，
        // 表现为底部元素反复弹跳。淡入无位移，不可能弹跳，且与整页淡入在同一节奏。
        enter = fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing)),
        // 退场（进入多选模式）：向下滑出屏幕底部 —— 底部栏的消失方向就该是下沉。
        exit = slideOutVertically(
            animationSpec = tween(250, easing = FastOutSlowInEasing),
            targetOffsetY = { it }
        ) + fadeOut(animationSpec = tween(250)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)
        ) {
            LiquidSegmentedSlider(
                selected = noteType,
                onSelected = onNoteTypeChange,
                leftLabel = stringResource(com.example.notesapp.R.string.tab_note),
                rightLabel = stringResource(com.example.notesapp.R.string.tab_todo),
                modifier = Modifier.weight(1f)
            )
            val (scaleMod, fabSrc) = rememberPressableGlassScale(pressedScale = 0.97f)
            val primary = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .then(scaleMod)
                    .shadow(4.dp, CircleShape)
                    .background(color = primary, shape = CircleShape)
                    .liquidGlassSurface(shape = CircleShape, isDark = isDark)
                    .clickable(
                        interactionSource = fabSrc,
                        indication = null,
                        onClick = onAddClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(com.example.notesapp.R.string.new_note),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
