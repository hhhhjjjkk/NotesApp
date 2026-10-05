package com.example.notesapp.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.example.notesapp.data.DocumentBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 笔记内图片的本地存储。
 *
 * 为什么要把图片复制进应用内部，而不是直接保存相册 Uri：
 * 相册里的原图被用户删除、移动，或外部存储权限变化后，Uri 会失效，
 * 笔记就会变成一堆裂图。复制到 filesDir/note_images/ 后由应用完全掌控生命周期。
 *
 * 该目录同时被 backup_rules.xml 与 data_extraction_rules.xml 显式包含，
 * 因此换机 / 云备份会一并带走图片。
 */
class NoteImageStore(private val context: Context) {

    companion object {
        const val DIR_NAME = "note_images"
        /**
         * 解码上限（长边像素），避免大图直接进内存导致 OOM。
         * 取 1280 是因为手机竖屏可用宽度通常在 1080px 上下，再大对观感没有帮助，
         * 却会成倍占用内存——编辑页是非懒加载 Column，多张图会同时驻留。
         */
        const val DEFAULT_MAX_DIMENSION = 1280

        private val ALLOWED_EXTENSIONS =
            setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    }

    fun imageDir(): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 解析图片文件。会拒绝含路径分隔符或 ".." 的文件名，
     * 防止数据库里的恶意/损坏值读到目录外（路径穿越）。
     */
    fun fileFor(fileName: String): File? {
        if (fileName.isBlank()) return null
        if (fileName.contains('/') || fileName.contains('\\') || fileName.contains("..")) return null
        return File(imageDir(), fileName)
    }

    fun exists(fileName: String): Boolean = fileFor(fileName)?.exists() == true

    /**
     * 把用户选中的图片复制进应用内部目录。
     *
     * @return 成功时返回可直接放进 [DocumentBlock.Image] 的块；失败返回 null
     */
    suspend fun importImage(uri: Uri): DocumentBlock.Image? = withContext(Dispatchers.IO) {
        runCatching {
            val extension = resolveExtension(uri)
            val fileName = UUID.randomUUID().toString() + "." + extension
            val target = File(imageDir(), fileName)

            val stream = context.contentResolver.openInputStream(uri)
                ?: return@runCatching null
            stream.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }

            if (!target.exists() || target.length() == 0L) {
                target.delete()
                return@runCatching null
            }

            val aspect = readAspectRatio(target)
            if (aspect == null) {
                target.delete()
                return@runCatching null
            }

            DocumentBlock.Image(
                id = DocumentBlock.newId(),
                fileName = fileName,
                aspectRatio = aspect,
                widthFraction = 1f
            )
        }.getOrNull()
    }

    /**
     * 读取并降采样图片，供 Compose 显示。
     * 长边不超过 [maxDimension]，避免整张原图进内存。
     */
    suspend fun loadBitmap(
        fileName: String,
        maxDimension: Int = DEFAULT_MAX_DIMENSION
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = fileFor(fileName) ?: return@withContext null
        if (!file.exists()) return@withContext null
        runCatching { decodeDownsampled(file, maxDimension) }.getOrNull()
    }

    fun delete(fileName: String) {
        fileFor(fileName)?.delete()
    }

    /**
     * 删除不再被任何笔记引用的图片。
     *
     * 注意：**不要**在每次保存后立即调用。自动保存与撤销/回收站并存时，
     * 短暂「未被引用」的图片可能马上又被引用回来，立即清理会造成丢图。
     * 建议仅在明确的操作后（如永久删除笔记、清空回收站、应用启动）调用，
     * 且传入当前全部笔记（含回收站）的引用集合。
     *
     * @param gracePeriodMs 宽限期：文件创建后该时长内不清理。
     *   覆盖「文件刚导入、笔记尚未保存」的窗口期——此时文件还没被任何笔记引用，
     *   但马上就会被引用；不加宽限期会把用户刚导入的图删掉。
     */
    suspend fun cleanupOrphans(referencedFiles: Set<String>, gracePeriodMs: Long = 0L) =
        withContext(Dispatchers.IO) {
            val dir = imageDir()
            val files = dir.listFiles() ?: return@withContext
            val now = System.currentTimeMillis()
            for (file in files) {
                if (file.name in referencedFiles) continue
                // 宽限期内的新文件一律保留（lastModified 在导入完成时写入）
                if (gracePeriodMs > 0L && now - file.lastModified() < gracePeriodMs) continue
                file.delete()
            }
        }

    /** 当前目录下全部图片文件名，供调用方与引用集合比对。 */
    fun listImageFiles(): Set<String> {
        val files = imageDir().listFiles() ?: return emptySet()
        return files.filter { it.isFile }.map { it.name }.toSet()
    }

    // ===== 内部实现 =====

    private fun resolveExtension(uri: Uri): String {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val fromMime = when (mime?.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/bmp", "image/x-ms-bmp" -> "bmp"
            "image/heic", "image/heif" -> "heic"
            "image/jpeg", "image/jpg" -> "jpg"
            else -> null
        }
        if (fromMime != null) return fromMime

        // MIME 缺失或不可识别时退回扩展名判断
        val raw = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return if (raw in ALLOWED_EXTENSIONS) {
            if (raw == "jpeg") "jpg" else raw
        } else {
            "jpg"
        }
    }

    /**
     * 读取宽高比。优先用 [ImageDecoder]，因为它会正确应用 EXIF 旋转
     * （手机竖拍的照片在原始像素里常是横的，不处理会导致方向错乱）。
     *
     * 后备路径（[BitmapFactory]，API < 28 或 ImageDecoder 失败时）只报告原始像素尺寸，
     * 不会应用 EXIF 方向，因此这里额外用 [ExifInterface] 读出旋转角：
     * 90/270 度时宽高对调，得到与 ImageDecoder 一致、符合人眼观感的宽高比。
     */
    private fun readAspectRatio(file: File): Float? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decoded = runCatching {
                val source = ImageDecoder.createSource(file)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    // 只为拿比例，解一张很小的图即可
                    decoder.setTargetSampleSize(16)
                }
            }.getOrNull()
            if (decoded != null) {
                val ratio = if (decoded.height > 0) {
                    decoded.width.toFloat() / decoded.height.toFloat()
                } else {
                    null
                }
                decoded.recycle()
                if (ratio != null && ratio > 0f) return ratio
            }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        val rotated = rotationIsSideways(file)
        val width = if (rotated) options.outHeight else options.outWidth
        val height = if (rotated) options.outWidth else options.outHeight
        return width.toFloat() / height.toFloat()
    }

    private fun decodeDownsampled(file: File, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 取最小的 2 的幂，使长边 <= maxDimension。
        // 注意不能写成 while (longest / 2 >= maxDimension)：那样会在长边已小于
        // 2*maxDimension 时提前停下，实际可能解出接近 2*maxDimension 的位图
        // （1600 上限下可达 ~3200px，单张约 40MB），与「长边不超过 maxDimension」的承诺不符。
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > maxDimension) {
            sample *= 2
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decoded = runCatching {
                val source = ImageDecoder.createSource(file)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.setTargetSampleSize(sample)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            }.getOrNull()
            if (decoded != null) return decoded
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val rotation = readRotation(file)
        if (rotation == 0f) return decoded
        // BitmapFactory 不应用 EXIF 方向，需手动旋转；ImageDecoder 分支已在内部处理，不会走到这里。
        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated != decoded) decoded.recycle()
        return rotated
    }

    /**
     * 读取 EXIF 旋转角（度）。返回 0 / 90 / 180 / 270 之一，读取失败时返回 0（不旋转）。
     * [ExifInterface] 在所有 API 级别可用（内部对 HEIF 等格式有专门处理），
     * 是补齐 [BitmapFactory] 不会自动应用方向的唯一可靠手段。
     */
    private fun readRotation(file: File): Float = runCatching {
        ExifInterface(file.absolutePath)
            .getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL).let { orientation ->
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }

    /** 旋转角是否为 90/270 度（即宽高需要交换）。 */
    private fun rotationIsSideways(file: File): Boolean {
        val r = readRotation(file)
        return r == 90f || r == 270f
    }
}
