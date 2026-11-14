package com.example.notesapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 液态侧滑删除 v2（v3.9.3 重写交互模型）。
 *
 * v3.9.2 仍有一个根本问题：卡片只跟随 18%，球和脖子画在卡片内容「上方」，
 * 观感是白球白脖子压在文字上。本版改为与参考视频一致的模型：
 *
 * - **卡片真实滑走**（80% 跟手），让出揭示区；
 * - **圆球钉在屏幕边缘**（左滑→右屏边，右滑→左屏边），从卡片滑开时就地显现；
 * - 只有一个圆球（deleteIcon 提供，红色实心）；Canvas 不再另画白色圆，
 *   避免出现「两个球」；球内缩 3dp 完整可见；
 * - 松手不足阈值：卡片低阻尼弹簧 Q 弹回位，圆球以更低阻尼弹簧贴回卡片边缘、
   被回位的卡片自然盖住——两个弹簧独立wobble，产生粘连拖尾手感；
 * - 超过阈值（卡宽 32%）：触发删除（-1 左滑 / +1 右滑），粒子飘散方向沿用。
 *
 * 所有坐标都被限制在「卡片尾缘 ↔ 屏幕边」之间，几何上不可能越界爆开。
 */
@Composable
fun LiquidSwipeDelete(
    enabled: Boolean,
    deleteIcon: @Composable () -> Unit,
    onConfirmDelete: (direction: Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val ballRadius = with(density) { 22.dp.toPx() }
    val deleteFraction = 0.32f
    val cardFollow = 0.8f

    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var lastDir by remember { mutableFloatStateOf(-1f) }
    // 方向在手势开始后首次显著移动时锁定：中途反向不再跳到屏幕另一边
    var lockedDir by remember { mutableFloatStateOf(0f) }
    var ballVisible by remember { mutableStateOf(false) }

    // 卡片位移；球心 X 绝对坐标；图标透明度
    val cardShift = remember { Animatable(0f) }
    val ballX = remember { Animatable(0f) }
    val iconAlpha = remember { Animatable(0f) }

    var cardJob by remember { mutableStateOf<Job?>(null) }
    var ballJob by remember { mutableStateOf<Job?>(null) }

    val edgeInset = with(density) { 3.dp.toPx() }
    fun restBallCx(dir: Float) =
        if (dir < 0f) widthPx - ballRadius - edgeInset else ballRadius + edgeInset
    fun edgeBase(dir: Float) = if (dir < 0f) widthPx else 0f

    // 拖动中逐帧：卡片跟手，球钉在屏边
    LaunchedEffect(dragX, dragging) {
        if (dragging) {
            cardShift.snapTo(dragX * cardFollow)
            if (abs(dragX) > 4f) {
                if (lockedDir == 0f) lockedDir = if (dragX < 0f) -1f else 1f
                lastDir = lockedDir
                ballX.snapTo(restBallCx(lastDir))
                ballVisible = true
                iconAlpha.snapTo(1f)
            }
        }
    }

    fun release() {
        if (!dragging) return
        dragging = false
        val travelled = abs(dragX)
        val dir = if (lockedDir != 0f) lockedDir else lastDir
        lockedDir = 0f
        if (travelled >= widthPx * deleteFraction) {
            // 删除：状态复位，回调交由列表移除本卡片（粒子在 HomeScreen 侧）
            dragX = 0f
            ballVisible = false
            scope.launch {
                cardShift.snapTo(0f)
                iconAlpha.snapTo(0f)
            }
            onConfirmDelete(dir.toInt())
        } else {
            // Q 弹回。⚠️ 圆球必须与卡片同步淡出（v3.11.0）：此前等两个低刚度弹簧
            // 都收敛才隐藏（>1s），用户看到的是「卡片回位了、红球还挂在屏幕边」。
            // 现在圆球跟随卡片同一时长淡出，球本体同时弹回边缘。
            cardJob = scope.launch {
                cardShift.animateTo(
                    0f,
                    spring(dampingRatio = 0.8f, stiffness = 380f)
                )
            }
            ballJob = scope.launch {
                ballX.animateTo(
                    edgeBase(dir) - dir * 0.35f * ballRadius,
                    spring(dampingRatio = 0.8f, stiffness = 380f)
                )
            }
            scope.launch { iconAlpha.animateTo(0f, tween(160)) }
            val doneCard = cardJob
            val doneBall = ballJob
            scope.launch {
                doneCard?.join()
                doneBall?.join()
                ballVisible = false
                ballX.snapTo(0f)
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        cardJob?.cancel()
                        ballJob?.cancel()
                        dragging = true
                        dragX = 0f
                        lockedDir = 0f
                    },
                    onDragEnd = { release() },
                    onDragCancel = { release() }
                ) { change, dragAmount ->
                    change.consume()
                    dragX = (dragX + dragAmount).coerceIn(-widthPx, widthPx)
                }
            }
    ) {
        // 卡片本体：80% 跟手滑走
        Box(modifier = Modifier.graphicsLayer { translationX = cardShift.value }) {
            content()
        }

        // 删除图标（唯一的圆球）：钉在揭示区屏幕边缘，随拖动/回弹位移
        if (ballVisible) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(if (lastDir < 0f) Alignment.CenterEnd else Alignment.CenterStart)
                        .graphicsLayer {
                            translationX = ballX.value - restBallCx(lastDir)
                            alpha = iconAlpha.value
                        }
                ) {
                    deleteIcon()
                }
            }
        }
    }
}
