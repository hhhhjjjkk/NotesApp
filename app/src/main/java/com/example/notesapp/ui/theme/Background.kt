package com.example.notesapp.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 全局自定义背景。
 *
 * 设计要点：
 * - 传入 URI 时加载图片并以 ContentScale.Crop 铺满屏幕
 * - **背景做真实模糊**：降采样 + CPU 箱式模糊 + GPU 放大（见 [BoxBlur] 的说明），
 *   在 API 26+ 全平台有效，且每帧成本为 0
 * - 叠加半透明遮罩（scrim）保证上层文字可读性
 * - 明色模式用白雾遮罩，暗色模式用黑雾遮罩
 * - [dim] 控制遮罩强度（0f=无遮罩，背景图最清晰；1f=完全遮挡）
 * - URI 为空时渲染纯背景色，行为与默认一致
 */

/**
 * 背景模糊的降采样目标：长边像素数。
 *
 * 取 96 是刻意偏小的值：越小则放大后的模糊感越强、CPU 开销越低。
 * 壁纸在这种尺度的模糊背景里没有任何细节可辨，因此不会「糊得不对」。
 */
private const val BLURRED_BACKGROUND_MAX_DIM = 96

/** 模糊半径（按降采样后的像素计）。半径 4 在 96px 尺度上约等于原图上很大的模糊。 */
private const val BLURRED_BACKGROUND_RADIUS = 4

/** 保护 [cachedBlurKey] / [cachedBlurBitmap] 的锁，避免并发加载时互相覆盖。 */
private val backgroundCacheLock = Any()

private var cachedBlurKey: String? = null
private var cachedBlurBitmap: Bitmap? = null

@Composable
fun AppBackground(
    uri: String?,
    isDark: Boolean,
    dim: Float = 0.55f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = if (uri.isNullOrBlank()) {
            null
        } else {
            loadBlurredBackground(context, Uri.parse(uri))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                // 小图放大必须用双线性插值，否则会出现明显马赛克方块。
                // 这一层插值本身就是「最后一档模糊」，让结果非常平滑。
                filterQuality = FilterQuality.Low,
                contentScale = ContentScale.Crop
            )
            // 遮罩：让背景图柔化，确保上层内容可读。强度由 dim 控制。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isDark) Color.Black.copy(alpha = dim)
                        else Color.White.copy(alpha = dim)
                    )
            )
        } else {
            // 无自定义背景时，画一层**柔和的径向渐变**而不是纯色。
            // 纯色背景下，半透明玻璃卡片与背景几乎同色，「玻璃」完全看不出来；
            // 有了这道渐变，卡片的高光/描边才有明暗层次可依附，
            // 玻璃质感在不设壁纸时同样成立。
            val base = MaterialThemeDefaults.backgroundColor(isDark)
            val accent = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 顺序很重要：先铺不透明底色，再叠半透明渐变。
                    // 反过来写的话，不透明底色会把渐变整个盖住，渐变等于没画。
                    .background(base)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = if (isDark) 0.20f else 0.13f),
                                Color.Transparent
                            ),
                            center = Offset(0.15f, 0.05f),
                            radius = 1400f
                        )
                    )
            )
        }
    }
}

/**
 * 为 [NotesAppTheme] 计算 Scaffold 的 `background` 配色。
 *
 * **始终返回透明**：背景的绘制权完全交给 [AppBackground] 这一层
 * （它负责壁纸模糊、遮罩、以及无壁纸时的底色 + 渐变）。
 *
 * 为什么不在这里返回实际颜色：
 * - 无壁纸时返回不透明底色，会把 AppBackground 画的渐变整个盖住，
 *   玻璃卡片也就失去了衬托（这是曾经的真实问题）
 * - 有壁纸时返回半透明遮罩色，会与 AppBackground 自己的遮罩叠成两层，
 *   实际暗度超过用户设定的 dim，且难以排查
 *
 * [hasCustomBackground] 与 [dim] 保留在签名里，是为了不改动调用点。
 */
@Suppress("UNUSED_PARAMETER")
fun resolveBackgroundColor(hasCustomBackground: Boolean, isDark: Boolean, dim: Float = 0.55f): Color =
    Color.Transparent

private object MaterialThemeDefaults {
    fun backgroundColor(isDark: Boolean): Color =
        if (isDark) DarkBackground else Color(0xFFF7F7F9)
}

/**
 * 在 IO 线程加载壁纸并返回**已模糊**的小位图，结果按 uri 缓存。
 *
 * 流程：解码到长边 [BLURRED_BACKGROUND_MAX_DIM] 的缩略图 → 取像素 →
 * [BoxBlur] 两遍箱式模糊 → 写回新 Bitmap。
 *
 * 全程在小图上进行：96×96 的 IntArray 只有约 37KB，
 * 模糊耗时毫秒级，且只在切换壁纸时发生一次。
 */
private suspend fun loadBlurredBackground(context: Context, uri: Uri): Bitmap? =
    withContext(Dispatchers.IO) {
        val key = uri.toString()
        synchronized(backgroundCacheLock) {
            if (key == cachedBlurKey) return@withContext cachedBlurBitmap
        }

        val decoded = runCatching { decodeThumbnail(context, uri) }.getOrNull()
            ?: return@withContext null

        val blurred = runCatching {
            val w = decoded.width
            val h = decoded.height
            if (w <= 0 || h <= 0) return@runCatching null
            val pixels = IntArray(w * h)
            decoded.getPixels(pixels, 0, w, 0, 0, w, h)
            val out = BoxBlur.blur(pixels, w, h, BLURRED_BACKGROUND_RADIUS)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                setPixels(out, 0, w, 0, 0, w, h)
            }
        }.getOrNull()

        decoded.recycle()

        if (blurred != null) {
            synchronized(backgroundCacheLock) {
                // 旧缓存位图要回收，否则反复换壁纸会持续泄漏 native 内存
                cachedBlurBitmap?.takeIf { it !== blurred && !it.isRecycled }?.recycle()
                cachedBlurKey = key
                cachedBlurBitmap = blurred
            }
        }
        blurred
    }

/**
 * 把壁纸解码成长边不超过 [BLURRED_BACKGROUND_MAX_DIM] 的缩略图。
 *
 * 用 ImageDecoder 的 setTargetSize（API 28+）能正确应用 EXIF 方向；
 * 低版本退化为 BitmapFactory + inSampleSize。模糊背景看不出方向差异，
 * 因此低版本不做 EXIF 补偿，避免为此多读一次文件。
 */
private fun decodeThumbnail(context: Context, uri: Uri): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val sz = info.size
            val maxDim = maxOf(sz.width, sz.height)
            if (maxDim > BLURRED_BACKGROUND_MAX_DIM) {
                val ratio = BLURRED_BACKGROUND_MAX_DIM.toFloat() / maxDim
                decoder.setTargetSize(
                    (sz.width * ratio).toInt().coerceAtLeast(1),
                    (sz.height * ratio).toInt().coerceAtLeast(1)
                )
            }
            // 只有 SOFTWARE 分配的位图才允许 getPixels；默认的 HARDWARE 位图读不了像素
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setMutableRequired(false)
        }
    }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
    if (maxDim <= 0) return null
    var sample = 1
    while (maxDim / sample > BLURRED_BACKGROUND_MAX_DIM) sample *= 2
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        // 显式 ARGB_8888，保证可读像素
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    }
}
