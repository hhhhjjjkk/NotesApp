package com.example.notesapp.ui.theme

/**
 * 可分离箱式模糊（separable box blur），纯 Kotlin，不依赖任何 Android API。
 *
 * ## 为什么自己实现，而不是用 Modifier.blur / RenderEffect
 * - Modifier.blur 需要 API 31+；本项目 minSdk 26，
 *   在 Android 8~11 上会静默失效——用户看到的「模糊」其实不存在。
 * - RenderEffect 是逐帧的 GPU 开销：全屏背景每帧模糊一次，
 *   在低端机上是实打实的掉帧来源。
 *
 * ## 这里的方案：一次性 CPU 模糊 + 双线性放大
 * 把壁纸先降采样到很小（长边约 96px）再模糊，之后交给 GPU 双线性放大绘制。
 * 放大的插值本身就是平滑的，视觉上等价于一个很大半径的模糊，
 * 而每帧成本为 0（就是画一张小图）。这在 API 26+ 全平台都成立。
 *
 * ## 算法
 * 连续 [passes] 遍箱式模糊（默认 2 遍），逼近高斯模糊的钟形权重分布——
 * 单遍箱式模糊会有明显的方块感，两遍后肉眼已看不出。
 * 每遍拆成横向 + 纵向两个一维 pass，用滑动窗口累加，
 * 复杂度 O(w·h)，与模糊半径无关（不是 O(w·h·r)）。
 *
 * 输入输出均为 ARGB_8888 的 [IntArray]（即 Bitmap.getPixels 的像素表示），
 * 因此可以脱离设备做 JVM 单元测试。
 */
internal object BoxBlur {

    /**
     * 对 ARGB 像素数组做箱式模糊。
     *
     * @param pixels 长度至少为 width * height 的 ARGB_8888 像素
     * @param radius 模糊半径（像素）。0 或负数表示不模糊，原样返回
     * @param passes 重复遍数，默认 2（单遍有明显方块感）
     * @return 新的模糊结果数组；入参非法时原样返回 [pixels]
     */
    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 2): IntArray {
        if (width <= 0 || height <= 0 || radius <= 0) return pixels
        if (pixels.size < width * height) return pixels
        if (passes <= 0) return pixels

        // src 保存当前结果；一遍 = 横向写到 scratch，纵向再写回 src
        var src = pixels.copyOf()
        val scratch = IntArray(src.size)
        repeat(passes) {
            blurHorizontal(src, scratch, width, height, radius)
            blurVertical(scratch, src, width, height, radius)
        }
        return src
    }

    /**
     * 横向一维模糊。窗口在左右边缘做 clamp（复制边缘像素）而不是补 0，
     * 否则图像四周会因为混入透明像素而发暗，形成一圈黑边。
     */
    private fun blurHorizontal(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val div = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0

            // 初始化窗口 [-radius, radius]，越界处 clamp 到边缘像素
            for (i in -radius..radius) {
                val p = src[row + i.coerceIn(0, width - 1)]
                sumA += (p ushr 24) and 0xFF
                sumR += (p ushr 16) and 0xFF
                sumG += (p ushr 8) and 0xFF
                sumB += p and 0xFF
            }

            for (x in 0 until width) {
                dst[row + x] = pack(sumA / div, sumR / div, sumG / div, sumB / div)
                // 窗口右移一格：移出左边一个，移入右边一个（同样 clamp）
                val outP = src[row + (x - radius).coerceIn(0, width - 1)]
                val inP = src[row + (x + radius + 1).coerceIn(0, width - 1)]
                sumA += ((inP ushr 24) and 0xFF) - ((outP ushr 24) and 0xFF)
                sumR += ((inP ushr 16) and 0xFF) - ((outP ushr 16) and 0xFF)
                sumG += ((inP ushr 8) and 0xFF) - ((outP ushr 8) and 0xFF)
                sumB += (inP and 0xFF) - (outP and 0xFF)
            }
        }
    }

    /** 纵向一维模糊。按列滑动窗口，边缘处理同 [blurHorizontal]。 */
    private fun blurVertical(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val div = radius * 2 + 1
        for (x in 0 until width) {
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0

            for (i in -radius..radius) {
                val p = src[i.coerceIn(0, height - 1) * width + x]
                sumA += (p ushr 24) and 0xFF
                sumR += (p ushr 16) and 0xFF
                sumG += (p ushr 8) and 0xFF
                sumB += p and 0xFF
            }

            for (y in 0 until height) {
                dst[y * width + x] = pack(sumA / div, sumR / div, sumG / div, sumB / div)
                val outP = src[(y - radius).coerceIn(0, height - 1) * width + x]
                val inP = src[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                sumA += ((inP ushr 24) and 0xFF) - ((outP ushr 24) and 0xFF)
                sumR += ((inP ushr 16) and 0xFF) - ((outP ushr 16) and 0xFF)
                sumG += ((inP ushr 8) and 0xFF) - ((outP ushr 8) and 0xFF)
                sumB += (inP and 0xFF) - (outP and 0xFF)
            }
        }
    }

    private fun pack(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b
}
