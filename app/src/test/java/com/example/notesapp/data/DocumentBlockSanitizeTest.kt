package com.example.notesapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DocumentBlock 内部工具函数的单元测试（sanitize、边界夹取等）。
 * 这些是纯函数，无需 Android 运行时即可在 JVM 跑通。
 */
class DocumentBlockSanitizeTest {

    @Test
    fun sanitizeWidthFraction_NaN_回退为1() {
        assertEquals(1f, DocumentBlock.sanitizeWidthFraction(Float.NaN), 0.001f)
    }

    @Test
    fun sanitizeWidthFraction_过小_夹到最小值() {
        assertEquals(DocumentBlock.MIN_WIDTH_FRACTION, DocumentBlock.sanitizeWidthFraction(0.01f), 0.001f)
        assertEquals(DocumentBlock.MIN_WIDTH_FRACTION, DocumentBlock.sanitizeWidthFraction(-1f), 0.001f)
    }

    @Test
    fun sanitizeWidthFraction_过大_夹到最大值() {
        assertEquals(DocumentBlock.MAX_WIDTH_FRACTION, DocumentBlock.sanitizeWidthFraction(5f), 0.001f)
    }

    @Test
    fun sanitizeWidthFraction_合法区间_保持不变() {
        assertEquals(0.5f, DocumentBlock.sanitizeWidthFraction(0.5f), 0.001f)
        assertEquals(0.2f, DocumentBlock.sanitizeWidthFraction(0.2f), 0.001f)
        assertEquals(1f, DocumentBlock.sanitizeWidthFraction(1f), 0.001f)
    }

    @Test
    fun sanitizeAspectRatio_NaN或非正_回退为1() {
        assertEquals(1f, DocumentBlock.sanitizeAspectRatio(Float.NaN), 0.001f)
        assertEquals(1f, DocumentBlock.sanitizeAspectRatio(0f), 0.001f)
        assertEquals(1f, DocumentBlock.sanitizeAspectRatio(-1f), 0.001f)
    }

    @Test
    fun sanitizeAspectRatio_过小_夹到0_05() {
        assertEquals(0.05f, DocumentBlock.sanitizeAspectRatio(0.01f), 0.001f)
    }

    @Test
    fun sanitizeAspectRatio_过大_夹到20() {
        assertEquals(20f, DocumentBlock.sanitizeAspectRatio(100f), 0.001f)
    }

    @Test
    fun sanitizeAspectRatio_合法区间_保持不变() {
        assertEquals(1.5f, DocumentBlock.sanitizeAspectRatio(1.5f), 0.001f)
        assertEquals(0.5f, DocumentBlock.sanitizeAspectRatio(0.5f), 0.001f)
        assertEquals(20f, DocumentBlock.sanitizeAspectRatio(20f), 0.001f)
    }

    @Test
    fun sanitizeAspectRatio_极端值不产生非法高度() {
        // 即使 aspectRatio 极大，sanitize 也会夹到 20，避免 宽度/20 导致高度接近0
        val aspect = DocumentBlock.sanitizeAspectRatio(1000f)
        assertTrue(aspect <= 20f && aspect >= 0.05f)
    }
}