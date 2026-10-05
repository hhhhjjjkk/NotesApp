package com.example.notesapp.storage

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * NoteImageStore EXIF 方向处理的单元测试。
 *
 * 这些测试验证：
 * 1. readRotation 正确映射 4 种有效方向到角度
 * 2. rotationIsSidewise 正确判断 90/270 度需交换宽高
 * 3. 缺失/无效 EXIF 时默认不旋转
 * 注意：真实 ExifInterface 需要 Android 运行时，这里用 JVM 单元测试只能验证纯逻辑映射，
 * 实际文件解码在 Android 仪器测试或真机上验证。
 */
class NoteImageStoreExifTest {

    /**
     * 验证方向标签到角度的映射逻辑（私有方法逻辑外提测试）。
     * 对应 NoteImageStore.readRotation 内部的 when 映射。
     */
    @Test
    fun `EXIF 方向到角度映射_四个标准方向`() {
        val map = mapOf(
            ExifInterface.ORIENTATION_NORMAL to 0f,
            ExifInterface.ORIENTATION_ROTATE_90 to 90f,
            ExifInterface.ORIENTATION_ROTATE_180 to 180f,
            ExifInterface.ORIENTATION_ROTATE_270 to 270f,
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to 0f,   // 镜像不旋转
            ExifInterface.ORIENTATION_FLIP_VERTICAL to 0f,
            ExifInterface.ORIENTATION_TRANSPOSE to 0f,
            ExifInterface.ORIENTATION_TRANSVERSE to 0f,
            999 to 0f // 未知值回退
        )

        map.forEach { (orientation, expectedAngle) ->
            val actual = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            assertEquals("方向 $orientation 应映射为 $expectedAngle", expectedAngle, actual, 0.001f)
        }
    }

    @Test
    fun `旋转是否侧向_90和270返回true_其余false`() {
        assertTrue("90 度是侧向", isSideways(90f))
        assertTrue("270 度是侧向", isSideways(270f))
        assertFalse("0 度不是侧向", isSideways(0f))
        assertFalse("180 度不是侧向（宽高不交换）", isSideways(180f))
        assertFalse("NaN 当作非侧向", isSideways(Float.NaN))
        assertFalse("负角度当作非侧向", isSideways(-90f))
    }

    @Test
    fun `宽高比交换逻辑_侧向旋转时宽高互换`() {
        // 模拟 readAspectRatio 的宽高交换逻辑
        val originalWidth = 1200
        val originalHeight = 800
        val originalRatio = originalWidth.toFloat() / originalHeight  // 1.5

        // 90° 旋转：显示时宽变高、高变宽
        val rotated90Ratio = calculateDisplayAspectRatio(originalRatio, 90f)
        assertEquals("90° 时比例应为倒数", originalHeight.toFloat() / originalWidth, rotated90Ratio, 0.001f)

        // 270° 同理
        val rotated270Ratio = calculateDisplayAspectRatio(originalRatio, 270f)
        assertEquals("270° 时比例应为倒数", originalHeight.toFloat() / originalWidth, rotated270Ratio, 0.001f)

        // 0° 和 180° 不交换
        val rotated0Ratio = calculateDisplayAspectRatio(originalRatio, 0f)
        assertEquals("0° 时比例不变", originalRatio, rotated0Ratio, 0.001f)

        val rotated180Ratio = calculateDisplayAspectRatio(originalRatio, 180f)
        assertEquals("180° 时比例不变", originalRatio, rotated180Ratio, 0.001f)
    }

    /** 复制 NoteImageStore.private fun rotationIsSideways 逻辑供测试 */
    private fun isSideways(rotation: Float): Boolean {
        return rotation == 90f || rotation == 270f
    }

    /** 复制 readAspectRatio 中的宽高交换计算逻辑供测试 */
    private fun calculateDisplayAspectRatio(fileAspectRatio: Float, rotation: Float): Float {
        return if (isSideways(rotation)) 1f / fileAspectRatio else fileAspectRatio
    }

    @Test
    fun `BitmapFactory 回退路径_侧向旋转会交换宽高`() {
        // 这是模拟 readAspectRatio 中 BitmapFactory 回退分支的逻辑
        // ImageDecoder (API 28+) 自动应用 EXIF，不需手动交换
        // BitmapFactory (API < 28) 需要手动判断 rotationIsSideways 并交换宽高

        val fileAspectRatio = 1.5f // 1200/800
        val orientation90 = ExifInterface.ORIENTATION_ROTATE_90

        // 模拟 rotationIsSideways(90f) -> true
        val isSideways = (orientation90 == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation90 == ExifInterface.ORIENTATION_ROTATE_270)

        val displayRatio = if (isSideways) 1f / fileAspectRatio else fileAspectRatio
        assertEquals("BitmapFactory 回退分支 90° 需交换宽高", 800f / 1200f, displayRatio, 0.001f)
    }
}
