package com.example.notesapp.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * - 叠加半透明遮罩（scrim）保证上层文字可读性
 * - 明色模式用白雾遮罩，暗色模式用黑雾遮罩
 * - 加载大图时按目标像素降采样，避免 OOM
 * - URI 为空时渲染纯背景色，行为与默认一致
 */
@Composable
fun AppBackground(
    uri: String?,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = if (uri.isNullOrBlank()) {
            null
        } else {
            loadDownsampledBitmap(context, Uri.parse(uri))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // 遮罩：让背景图柔化，确保上层内容可读
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isDark) Color.Black.copy(alpha = 0.55f)
                        else Color.White.copy(alpha = 0.65f)
                    )
            )
        } else {
            // 无自定义背景时，渲染默认背景色
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialThemeDefaults.backgroundColor(isDark))
            )
        }
    }
}

/**
 * 为 [NotesAppTheme] 计算实际 `background` 配色：
 * - 有自定义背景时使用半透明遮罩色，让背景图透过 Scaffold 的 containerColor 显示
 * - 无自定义背景时使用默认不透明背景色
 */
fun resolveBackgroundColor(hasCustomBackground: Boolean, isDark: Boolean): Color =
    if (hasCustomBackground) {
        if (isDark) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.65f)
    } else {
        MaterialThemeDefaults.backgroundColor(isDark)
    }

private object MaterialThemeDefaults {
    fun backgroundColor(isDark: Boolean): Color =
        if (isDark) DarkBackground else Color(0xFFFDFDFD)
}

/**
 * 在 IO 线程加载并降采样位图，避免主线程卡顿与 OOM。
 * 目标边长 2048，足以覆盖大多数屏幕并保持清晰。
 */
private suspend fun loadDownsampledBitmap(context: Context, uri: Uri): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val targetMaxSize = 2048
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val sz = info.size
                    val maxDim = maxOf(sz.width, sz.height)
                    if (maxDim > targetMaxSize) {
                        val ratio = targetMaxSize.toFloat() / maxDim
                        decoder.setTargetSize(
                            (sz.width * ratio).toInt().coerceAtLeast(1),
                            (sz.height * ratio).toInt().coerceAtLeast(1)
                        )
                    }
                    decoder.setMutableRequired(false)
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
                var sample = 1
                while (maxDim / sample > targetMaxSize) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }
        }.getOrNull()
    }
