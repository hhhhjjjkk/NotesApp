package com.example.notesapp.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.runtime.withFrameNanos
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
 * ## 为什么必须「主动重播」入场动画（踩坑记录，勿删）
 * Compose Navigation 用 getVisibleEntries() 驱动 AnimatedContent，
 * 且内容由 SaveableStateHolder/LocalOwnersProvider 承载，因此从二级页返回时
 * HomeScreen 的 composition 是「被保留」的，remember 值不会重置，
 * visible = !selectionMode 也自始至终没有变化 —— 所以 AnimatedVisibility
 * 不会播放 enter 动画（它只响应变化，不响应初始值）。
 *
 * 因此必须主动重播：以 replayKey 标记每一次重新回到首页，
 * 当其变化时先把状态压回 false（无动画地回到起点），再推入 true，
 * 人为制造一次真实的 false -> true 变化，从而触发上浮入场动画。
 * 单一 effect 同时负责「回到首页重播」与「多选模式跟随」，
 * 拆成两个 effect 会在首次组合时互相覆盖 targetState，产生竞态。
 */
@Composable
fun HomeBottomBar(
    visibleState: MutableTransitionState<Boolean>,
    replayKey: Any?,
    selectionMode: Boolean,
    noteType: Int,
    onNoteTypeChange: (Int) -> Unit,
    isDark: Boolean,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(replayKey, selectionMode) {
        if (!selectionMode) {
            // 回到首页：先压回 false 并等一帧，确保 AnimatedVisibility 观察到该变化，
            // 否则同一帧内 false -> true 会被合并，动画不会播放。
            visibleState.targetState = false
            withFrameNanos { }
        }
        visibleState.targetState = !selectionMode
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = slideInVertically(
            animationSpec = tween(350),
            initialOffsetY = { it }
        ) + fadeIn(animationSpec = tween(350)),
        exit = slideOutVertically(
            animationSpec = tween(300),
            targetOffsetY = { it }
        ) + fadeOut(animationSpec = tween(300)),
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
                isDark = isDark,
                modifier = Modifier.weight(1f)
            )
            val (scaleMod, fabSrc) = rememberPressableGlassScale(pressedScale = 0.92f)
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
