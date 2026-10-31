package com.example.notesapp.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
 * ## 入场动画机制（v3.7.4 重修，勿退回旧写法）
 *
 * ### 为什么用 Compose 原生 AnimatedVisibility，而不是手动 graphicsLayer 位移
 * v3.7.2 曾用 `Animatable` 手动驱动 `graphicsLayer.translationY` 做「上浮」：
 * ```
 * floatProgress.snapTo(0f)        // ← 元凶：瞬间把底栏压到下方 200dp 并 alpha=0
 * floatProgress.animateTo(1f)    // ← 再从屏幕深处往上冲回就位
 * ```
 * `snapTo(0)` 产生了一次「向下一沉 + 瞬间消失」的跳变，紧接着 `animateTo` 从
 * 屏幕下方深处往上冲，两段运动方向相反，视觉上就是「先向下滑动、再往上冲」，
 * 观感完全反直觉（v3.7.0/v3.7.1/v3.7.3 用户真机反复反馈「向上飞/冲过屏顶」）。
 *
 * 现在改为**完全交给 Compose 原生过渡**：
 * - 动画由 AnimatedVisibility 在 false→true 时自动播放 `slideInVertically`，
 *   位移量恒等于「内容自身高度」，终点就是布局位置，**由动画系统保证不会越界**；
 * - 不再有任何 snapTo / 瞬移，底栏只会「从屏幕底边向上滑入就位」，方向唯一且明确。
 *
 * ### 如何在「从二级页返回首页」时重播入场动画
 * Compose Navigation 从二级页返回时 HomeScreen 的 composition 是「被保留」的，
 * AnimatedVisibility 的 targetState 自始至终没有变化，因此不会自动播放 enter。
 *
 * 做法：HomeScreen 用 `remember(replayKey) { MutableTransitionState(false) }`——
 * replayKey 变化时**重建**一个初始为 false 的 TransitionState，
 * 于是形成一次真实的 false→true 变化，AnimatedVisibility 自动播放 enter。
 * 相比旧版「把 targetState 压回 false 再推回 true」的翻转做法，本方案
 * **不会先播一段下滑的 exit**，也就没有「向下滑」的那一下。
 *
 * ⚠️ 前提：replayKey 每次返回必须**只变化一次**。ON_RESUME 观察者在转场期间
 * 被重新注册时，LifecycleRegistry 会给已 RESUMED 的 entry 补发一次 ON_RESUME，
 * 会让计数多跳一次 → 底栏连续重播两次（v3.7.0「一直向上飞」的真正成因）。
 * 该去重逻辑在 NotesNavHost 的 homeResumeCount 处实现，勿删。
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
        // 显隐只跟随多选模式；返回首页时的重播由 HomeScreen 重建 TransitionState
        // 触发（见文件头注释），这里不再做「翻转」那套会播下滑 exit 的操作。
        visibleState.targetState = !selectionMode
    }

    AnimatedVisibility(
        visibleState = visibleState,
        // 入场：自屏幕底边向上滑入 + 淡入。位移量 = 内容高度，终点 = 布局位置。
        enter = slideInVertically(
            animationSpec = tween(350, easing = FastOutSlowInEasing),
            initialOffsetY = { it }
        ) + fadeIn(animationSpec = tween(280)),
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
