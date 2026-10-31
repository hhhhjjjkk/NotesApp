package com.example.notesapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * 一次「删除溶解」粒子爆发的描述。
 *
 * @param id 唯一标识（用于 Composable key 与动画驱动）
 * @param area 被删卡片在**本覆盖层坐标系**中的矩形（由调用方换算好）
 */
data class NoteDissolveBurst(
    val id: Long,
    val area: Rect
)

/** 整段粒子飘散时长（与参考视频实测一致：约 0.5s 散尽）。 */
private const val BURST_DURATION_MS = 520

/**
 * 单个粒子的常量参数。在爆发创建时一次性生成，
 * 之后每帧只做解析式计算（位置/透明度/半径），避免逐帧随机造成抖动。
 */
private class Sparkle(
    val startX: Float,
    val startY: Float,
    val dx: Float,
    val dy: Float,
    val gravity: Float,
    val radius: Float,
    val brightness: Float,
    val life: Float
)

/**
 * 删除笔记时的「粒子飘散」覆盖层（参考系统通知消散效果）。
 *
 * 视觉特征：
 * - 大量细小白色（浅色主题为深灰）星尘，自卡片区域向四周扩散；
 * - 带轻微重力下坠，越到后段越暗、越小；
 * - 每个粒子寿命略有差异（[Sparkle.life]），避免整体「一刀切」地同时消失；
 * - 总时长约 520ms，播完通过 [onBurstFinished] 通知调用方回收。
 *
 * 该覆盖层是**纯视觉层**：不参与任何数据/撤销逻辑，删除与撤销行为完全不受影响。
 *
 * @param bursts 当前存活的爆发列表
 * @param sparkColor 粒子颜色（深色主题用白，浅色主题用深灰，保证可见）
 * @param onBurstFinished 某个爆发播完的回调，调用方据此从列表中移除
 */
@Composable
fun NoteDissolveParticles(
    bursts: List<NoteDissolveBurst>,
    sparkColor: Color,
    onBurstFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // 即使没有爆发也必须保留这个 Box：调用方依赖它的 onGloballyPositioned
    // 取得覆盖层在窗口中的原点，用来把卡片矩形换算到本层坐标系。
    val density = LocalDensity.current
    Box(modifier) {
        bursts.forEach { burst ->
            key(burst.id) {
                val sparkles = remember(burst.id) { createSparkles(burst, density) }
                val progress = remember { Animatable(0f) }
                LaunchedEffect(burst.id) {
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(BURST_DURATION_MS, easing = LinearEasing)
                    )
                    onBurstFinished(burst.id)
                }
                Canvas(Modifier.fillMaxSize()) {
                    val p = progress.value
                    if (p >= 1f) return@Canvas
                    sparkles.forEach { s ->
                        // 每个粒子有自己的寿命比例，短寿命的更早消失
                        val pi = (p / s.life).coerceAtMost(1f)
                        if (pi >= 1f) return@forEach
                        // 缓出：起步快、末段慢，接近真实粉尘的扩散手感
                        val ease = 1f - (1f - pi) * (1f - pi)
                        val x = s.startX + s.dx * ease
                        val y = s.startY + s.dy * ease + s.gravity * pi * pi
                        val alpha = if (pi < 0.12f) {
                            1f
                        } else {
                            ((1f - pi) / 0.88f).pow(1.45f)
                        }
                        drawCircle(
                            color = sparkColor.copy(
                                alpha = (alpha * s.brightness).coerceIn(0f, 1f)
                            ),
                            radius = s.radius * (1f - 0.35f * pi),
                            center = Offset(x, y)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 按爆发 id 作为种子生成全部粒子（同一次爆发每次重组得到相同结果）。
 *
 * 参数取值对齐参考视频：数量 70~114、半径约 0.55~2.5dp（少量亮点）、
 * 亮度 0.55~1.0、寿命 0.78~1.08 倍。
 */
private fun createSparkles(burst: NoteDissolveBurst, density: Density): List<Sparkle> {
    val area = burst.area
    // 用 id 与区域宽度混合做种子，保证不同卡片不会得到完全一致的花纹
    val rnd = Random(burst.id * 31L + area.width.toLong())
    val count = 70 + rnd.nextInt(45)
    val dp = density.density
    return List(count) {
        val startX = area.left + rnd.nextFloat() * area.width
        val startY = area.top + rnd.nextFloat() * area.height
        val angle = rnd.nextFloat() * (2f * PI.toFloat())
        // 扩散幅度：相对卡片尺寸的比例，避免小卡片炸得过大、大卡片炸得太小
        val spread = 0.10f + rnd.nextFloat() * 0.38f
        val outward = 0.35f + rnd.nextFloat() * 0.65f
        val isGlint = rnd.nextFloat() < 0.12f
        Sparkle(
            startX = startX,
            startY = startY,
            dx = cos(angle) * spread * area.width * outward,
            dy = sin(angle) * spread * area.height * outward,
            // 轻微重力：末段整体略微下坠，符合粉尘直觉
            gravity = (0.10f + rnd.nextFloat() * 0.22f) * area.height,
            radius = (if (isGlint) {
                1.6f + rnd.nextFloat() * 0.9f
            } else {
                0.55f + rnd.nextFloat() * 0.85f
            }) * dp,
            brightness = if (isGlint) 1f else 0.55f + rnd.nextFloat() * 0.45f,
            life = 0.78f + rnd.nextFloat() * 0.30f
        )
    }
}
