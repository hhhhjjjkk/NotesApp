package com.example.notesapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拖拽缩放数学的单元测试。
 *
 * 拖拽手感本身无法在无真机环境验证，但「横向位移 → 宽度比例」的换算与边界夹取
 * 是纯数学，必须保证正确——尤其是不能出现除零、NaN 或越界比例，
 * 否则图片会消失或撑破布局。
 */
class DocumentBlockResizeTest {

    private val availableWidth = 1000f

    @Test
    fun `向右拖拽变宽_且位移与宽度变化一比一`() {
        val result = DocumentBlock.resizeWidthFraction(
            startFraction = 0.5f,
            dragX = 100f,
            availableWidthPx = availableWidth
        )
        // 拖 100px / 可用 1000px = 10% 宽度，边缘精确跟随手指
        assertEquals(0.6f, result, 0.0001f)
    }

    @Test
    fun `向左拖拽变窄`() {
        val result = DocumentBlock.resizeWidthFraction(
            startFraction = 0.5f,
            dragX = -100f,
            availableWidthPx = availableWidth
        )
        assertEquals(0.4f, result, 0.0001f)
    }

    @Test
    fun `向右拖过头夹到最大比例`() {
        val result = DocumentBlock.resizeWidthFraction(
            startFraction = 0.9f,
            dragX = 500f,
            availableWidthPx = availableWidth
        )
        assertEquals(DocumentBlock.MAX_WIDTH_FRACTION, result, 0.0001f)
    }

    @Test
    fun `向左拖过头夹到最小比例_图片不会缩到看不见`() {
        val result = DocumentBlock.resizeWidthFraction(
            startFraction = 0.3f,
            dragX = -900f,
            availableWidthPx = availableWidth
        )
        assertEquals(DocumentBlock.MIN_WIDTH_FRACTION, result, 0.0001f)
    }

    @Test
    fun `零位移时比例不变`() {
        val result = DocumentBlock.resizeWidthFraction(
            startFraction = 0.42f,
            dragX = 0f,
            availableWidthPx = availableWidth
        )
        assertEquals(0.42f, result, 0.0001f)
    }

    @Test
    fun `可用宽度为零时不产生除零或非法值`() {
        val result = DocumentBlock.resizeWidthFraction(
            startFraction = 0.7f,
            dragX = 100f,
            availableWidthPx = 0f
        )
        assertTrue("结果不应为 NaN", !result.isNaN())
        assertTrue("结果不应为无穷", result.isFinite())
        assertEquals(0.7f, result, 0.0001f)
    }

    @Test
    fun `起始比例非法时被修正`() {
        val nanStart = DocumentBlock.resizeWidthFraction(
            startFraction = Float.NaN,
            dragX = 0f,
            availableWidthPx = availableWidth
        )
        assertEquals(1f, nanStart, 0.0001f)

        val tooBig = DocumentBlock.resizeWidthFraction(
            startFraction = 5f,
            dragX = 0f,
            availableWidthPx = availableWidth
        )
        assertEquals(DocumentBlock.MAX_WIDTH_FRACTION, tooBig, 0.0001f)
    }

    @Test
    fun `连续拖拽累加后结果仍在合法区间`() {
        var fraction = 0.5f
        // 模拟一次手势里连续多次位移事件
        repeat(50) {
            fraction = DocumentBlock.resizeWidthFraction(
                startFraction = fraction,
                dragX = 40f,
                availableWidthPx = availableWidth
            )
        }
        assertTrue(fraction >= DocumentBlock.MIN_WIDTH_FRACTION)
        assertTrue(fraction <= DocumentBlock.MAX_WIDTH_FRACTION)
        assertEquals(DocumentBlock.MAX_WIDTH_FRACTION, fraction, 0.0001f)
    }

    @Test
    fun `按原图比例显示时高度由宽度与宽高比决定`() {
        // 显示尺寸 = 可用宽度 × 比例；高度 = 宽度 / 宽高比
        val aspect = 1.5f
        val fraction = 0.6f
        val width = availableWidth * fraction
        val height = width / aspect
        assertEquals(600f, width, 0.001f)
        assertEquals(400f, height, 0.001f)
    }
}
