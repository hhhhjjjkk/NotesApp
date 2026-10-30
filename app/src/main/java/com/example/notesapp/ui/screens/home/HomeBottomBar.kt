package com.example.notesapp.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.notesapp.R
import com.example.notesapp.ui.components.LiquidSegmentedSlider
import com.example.notesapp.ui.theme.liquidGlassSurface
import com.example.notesapp.ui.theme.rememberPressableGlassScale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 底部栏：「备忘录/代办」切换滑块 + 新建按钮。
 *
 * ## 入场动画为什么是「内容层一次性上浮」，而不是翻转 AnimatedVisibility（踩坑记录，勿删）
 * Compose Navigation 从二级页返回时 HomeScreen 的 composition 是「被保留」的，
 * AnimatedVisibility 不会自动重播 enter（它只响应变化，不响应初始值）。
 *
 * 旧实现（v3.3~v3.7.1）靠 replayKey 变化把 visibleState 在 false/true 间翻转来人为重播。
 * 该机制有两个脆弱点，且从未在真机上完整验证过：
 * 1. 每次翻转都会先播 exit（下滑 300ms）再播 enter（上浮 350ms）；只要触发比预期多
 *    （lifecycle 补发 ON_RESUME、effect 重启等），入场动画就被反复重启，
 *    观感即「底栏一直向上飞、落不到位」（v3.7.0/v3.7.1 用户真机反馈）。
 * 2. enter 的 initialOffsetY = { it } 取「内容自身高度」，转场期间父容器测量
 *    不稳定时起点可能异常。
 *
 * 新机制（v3.7.2 起重修）：
 * 1. AnimatedVisibility 只负责多选模式的显隐：enter 仅淡入，exit 保持下滑淡出；
 * 2. 「回到首页/离开多选」的上浮改为内容层 graphicsLayer 的单次 progress 动画
 *    （固定 200dp 距离，落点 = 布局位置，与测量无关）；
 * 3. 重播请求合并：动画进行中的再次触发直接忽略，让当前这一次自然落位——
 *    结构上保证「只上浮一次、必然落位」。
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
    val density = LocalDensity.current
    val floatDistancePx = with(density) { 200.dp.toPx() }
    // 上浮进度：0 = 起始（下方 + 透明），1 = 就位。初值 0 避免首帧闪现。
    val floatProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var replayJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(replayKey, selectionMode) {
        // 显隐只跟随多选模式，不再为了重播而翻转（翻转正是旧机制的 bug 源头）
        visibleState.targetState = !selectionMode
        if (selectionMode) return@LaunchedEffect
        // 合并护栏：上浮已在飞时，新触发直接忽略——保证只上浮一次、必然落位。
        // 用 composition 级 scope 启动，避免 effect 因 key 变化重启时把动画一起取消。
        if (replayJob?.isActive == true) return@LaunchedEffect
        replayJob = scope.launch {
            floatProgress.snapTo(0f)
            floatProgress.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(animationSpec = tween(200)),
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
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .graphicsLayer {
                    translationY = (1f - floatProgress.value) * floatDistancePx
                    alpha = floatProgress.value
                },
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
