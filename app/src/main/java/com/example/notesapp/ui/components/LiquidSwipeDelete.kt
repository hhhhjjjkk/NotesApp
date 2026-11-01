package com.example.notesapp.ui.components
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.launch
/**
 * 液态侧滑删除（参考鸿蒙通知中心的「圆球跟手 + 粘连 + Q 弹」交互）。
 *
 * 与传统 SwipeToDismiss（整卡滑走）的区别：
 * - 卡片本体只做轻微跟随位移（约圆球的 18%），不整体飞出；
 * - 滑动侧长出一个圆球，圆心跟随手指横向移动；
 * - 圆球与卡片边缘之间画一段液态粘连（贝塞尔脖子），距离越远脖子越长越细，
 *   超过 [breakDistance] 即断裂；松手不足阈值时圆球以低阻尼弹簧 Q 弹回；
 * - 位移超过删除阈值松手 → 触发 [onConfirmDelete]（-1 = 向左滑删除，+1 = 向右）。
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
    val breakDistance = with(density) { 56.dp.toPx() }
    val deleteFraction = 0.32f
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val ballOffset = remember { Animatable(0f) }
    val cardShift = remember { Animatable(0f) }
    LaunchedEffect(dragX, dragging) {
        // 本 effect 体就是协程，snapTo 可直接调用；
        // 但为了「拖动中每帧都跟手」，改为在拖动时用当前值驱动绘制，
        // snapTo 只在 dragging 变 true 的瞬间执行一次。
        if (dragging) {
            ballOffset.snapTo(dragX)
            cardShift.snapTo(dragX * 0.18f)
        }
    }
    var ballJob: kotlinx.coroutines.Job? = null
    var cardJob: kotlinx.coroutines.Job? = null

    fun animateBack() {
        ballJob = scope.launch {
            ballOffset.animateTo(
                0f,
                spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium)
            )
        }
        cardJob = scope.launch {
            cardShift.animateTo(
                0f,
                spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
            )
        }
    }
    fun release() {
        dragging = false
        val threshold = widthPx * deleteFraction
        val travelled = abs(ballOffset.value)
        if (travelled >= threshold) {
            onConfirmDelete(if (ballOffset.value < 0f) -1 else 1)
        } else {
            animateBack()
        }
    }
    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        // 取消进行中的回弹（cancel 非 suspend，可在回调里直接调）
                        ballJob?.cancel()
                        cardJob?.cancel()
                        dragging = true
                        dragX = ballOffset.value
                    },
                    onDragEnd = { release() },
                    onDragCancel = { release() }
                ) { change, dragAmount ->
                    change.consume()
                    dragX = (dragX + dragAmount).coerceIn(-widthPx, widthPx)
                }
            }
    ) {
        content()
        val bx = ballOffset.value
        val shift = cardShift.value
        if (bx != 0f) {
            Canvas(Modifier.fillMaxSize()) {
                drawLiquid(
                    ballOffsetX = bx,
                    cardShiftX = shift,
                    ballRadius = ballRadius,
                    breakDistance = breakDistance,
                    ballColor = Color.White.copy(alpha = 0.90f)
                )
            }
        }
        if (bx != 0f) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(if (bx < 0f) Alignment.CenterEnd else Alignment.CenterStart)
                        .graphicsLayer {
                            // 图标圆球与 Canvas 圆球使用同一偏移，两者始终重合
                            translationX = bx
                        }
                ) {
                    deleteIcon()
                }
            }
        }
    }
}
/**
 * 两圆公切线粘连（metaball）：卡片边缘视作半径很大的虚拟圆。
 */
private fun DrawScope.drawLiquid(
    ballOffsetX: Float,
    cardShiftX: Float,
    ballRadius: Float,
    breakDistance: Float,
    ballColor: Color
) {
    val dir = sign(ballOffsetX)
    val dist = abs(ballOffsetX)
    if (dist < 1f) return
    val glued = dist < breakDistance
    val handleY = size.height / 2f
    val cardEdgeX = if (dir < 0f) cardShiftX else size.width + cardShiftX
    val ballCx = cardEdgeX + dir * (dist + ballRadius * 0.4f)
    val ballCy = handleY
    if (glued) {
        val r1 = ballRadius * 6f
        val cx1 = ballCx - dir * (ballRadius + r1)
        val cy1 = ballCy
        val r2 = ballRadius
        val cx2 = ballCx
        val cy2 = ballCy
        val d = (cx2 - cx1) * dir
        val u = ((d / (r1 + r2)).coerceIn(0f, 1f))
        val base = ((r1 - r2) / maxOf(d, 1f)).coerceIn(-1f, 1f)
        val angle = kotlin.math.acos(base) * (if (dir < 0f) -1f else 1f)
        val p1x = cx1 + r1 * kotlin.math.cos(angle)
        val p1y = cy1 + r1 * kotlin.math.sin(angle)
        val p2x = cx1 + r1 * kotlin.math.cos(-angle)
        val p2y = cy1 + r1 * kotlin.math.sin(-angle)
        val p3x = cx2 + r2 * kotlin.math.cos(angle)
        val p3y = cy2 + r2 * kotlin.math.sin(angle)
        val p4x = cx2 + r2 * kotlin.math.cos(-angle)
        val p4y = cy2 + r2 * kotlin.math.sin(-angle)
        val shrink = 1f - 0.55f * u
        val ctrl = (p3y - p1y) * shrink
        val path = Path().apply {
            moveTo(p1x, p1y)
            cubicTo(p1x, p1y - ctrl, p3x, p3y - ctrl, p3x, p3y)
            arcTo(
                rect = Rect(cx2 - r2, cy2 - r2, cx2 + r2, cy2 + r2),
                startAngleDegrees = kotlin.math.atan2(p3y - cy2, p3x - cx2) * 180f / kotlin.math.PI.toFloat(),
                sweepAngleDegrees = if (dir < 0f) -180f else 180f,
                forceMoveTo = false
            )
            cubicTo(p4x, p4y + ctrl, p2x, p2y + ctrl, p2x, p2y)
            close()
        }
        drawPath(path, ballColor, style = Fill)
    }
    drawCircle(
        color = ballColor,
        radius = ballRadius,
        center = Offset(ballCx, ballCy),
        style = Fill
    )
}
