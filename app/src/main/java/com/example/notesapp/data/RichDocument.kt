package com.example.notesapp.data

import java.util.UUID

/**
 * 图文混排文档的单个块。
 *
 * 设计要点：
 * - [Note.content] 仍保留纯文本版本（用于搜索与分享），[Note.richContent] 存放本模型的序列化结果。
 * - 旧笔记 [Note.richContent] 为空串，[RichDocumentCodec.decode] 会退回为「单段纯文本」，
 *   因此历史数据零丢失、无需一次性迁移内容。
 */
sealed interface DocumentBlock {
    val id: String

    /** 文字块。文本可包含换行，序列化时会被转义。 */
    data class Text(
        override val id: String,
        val text: String
    ) : DocumentBlock

    /**
     * 图片块。
     *
     * @param fileName 相对 app 内部图片目录的文件名（不存绝对路径，便于备份/换机恢复）
     * @param aspectRatio 宽高比 width/height，恒 > 0，用于按原图比例显示
     * @param widthFraction 显示宽度占可用宽度的比例，取值 [MIN_WIDTH_FRACTION, 1f]
     */
    data class Image(
        override val id: String,
        val fileName: String,
        val aspectRatio: Float,
        val widthFraction: Float = 1f
    ) : DocumentBlock

    companion object {
        /** 图片最小显示宽度比例（20%），避免被拖到看不见 */
        const val MIN_WIDTH_FRACTION = 0.2f
        /** 图片最大显示宽度比例（100%），即撑满可用宽度 */
        const val MAX_WIDTH_FRACTION = 1f

        fun newId(): String = UUID.randomUUID().toString()

        fun text(text: String): Text = Text(newId(), text)

        /** 把任意输入夹到合法区间，防止反序列化出越界值导致渲染异常 */
        fun sanitizeWidthFraction(value: Float): Float =
            if (value.isNaN()) 1f else value.coerceIn(MIN_WIDTH_FRACTION, MAX_WIDTH_FRACTION)

        /** 宽高比缺失或非法时退化为 1:1，避免除零与无限高度 */
        fun sanitizeAspectRatio(value: Float): Float =
            if (value.isNaN() || value <= 0f) 1f else value.coerceIn(0.05f, 20f)

        /**
         * 计算拖拽右下角手柄后的新宽度比例。
         *
         * 只采用横向位移，且与宽度变化 1:1 对应：
         * 因为显示时锁定原图宽高比，宽度是唯一自由度，横向位移直接就是宽度变化，
         * 图片边缘会精确跟随手指。若把纵向位移也折算进来（例如纵向位移 × 宽高比后再取平均），
         * 横向拖动就只生效一半，边缘会明显落后于手指，手感发飘。
         * 纵向拖拽本身也会被外层纵向滚动容器接管，因此不参与计算。
         *
         * 抽成纯函数是为了能脱离设备做单元测试——拖拽手感无法在无真机环境验证，
         * 但「位移 → 比例」的换算与夹取这部分数学必须是对的。
         *
         * @param startFraction 拖拽开始时的宽度比例
         * @param dragX 横向位移（px，右为正）
         * @param availableWidthPx 可用宽度（px），必须 > 0
         */
        fun resizeWidthFraction(
            startFraction: Float,
            dragX: Float,
            availableWidthPx: Float
        ): Float {
            if (availableWidthPx <= 0f) return sanitizeWidthFraction(startFraction)
            val startPx = sanitizeWidthFraction(startFraction) * availableWidthPx
            return sanitizeWidthFraction((startPx + dragX) / availableWidthPx)
        }
    }
}

/**
 * [DocumentBlock] 列表与字符串之间的编解码。
 *
 * 采用纯 Kotlin 手写转义格式（不依赖 org.json，从而可在 JVM 单元测试中直接运行）：
 * ```
 * V1
 * T<TAB>id<TAB>escaped text
 * I<TAB>id<TAB>fileName<TAB>aspectRatio<TAB>widthFraction
 * ```
 * 文本中的反斜杠、换行、回车、制表符会被转义，因此每条记录恰好占一行。
 */
object RichDocumentCodec {

    private const val VERSION = "V1"
    private const val TAG_TEXT = "T"
    private const val TAG_IMAGE = "I"
    private const val SEP = '\t'
    private const val LINE_SEP = '\n'

    fun encode(blocks: List<DocumentBlock>): String {
        if (blocks.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append(VERSION)
        for (block in blocks) {
            sb.append(LINE_SEP)
            when (block) {
                is DocumentBlock.Text -> {
                    sb.append(TAG_TEXT).append(SEP)
                        .append(block.id).append(SEP)
                        .append(escape(block.text))
                }
                is DocumentBlock.Image -> {
                    sb.append(TAG_IMAGE).append(SEP)
                        .append(block.id).append(SEP)
                        .append(escape(block.fileName)).append(SEP)
                        .append(DocumentBlock.sanitizeAspectRatio(block.aspectRatio)).append(SEP)
                        .append(DocumentBlock.sanitizeWidthFraction(block.widthFraction))
                }
            }
        }
        return sb.toString()
    }

    /**
     * 解析图文文档。
     *
     * @param richContent [Note.richContent]，可能为空（旧数据）或损坏
     * @param fallbackText 解析失败或为旧数据时使用的纯文本内容
     */
    fun decode(richContent: String, fallbackText: String = ""): List<DocumentBlock> {
        val fallback = listOf(DocumentBlock.Text(DocumentBlock.newId(), fallbackText))
        if (richContent.isBlank()) return fallback

        val lines = richContent.split(LINE_SEP)
        if (lines.isEmpty() || lines[0] != VERSION) return fallback

        val blocks = mutableListOf<DocumentBlock>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isEmpty()) continue
            val parts = line.split(SEP)
            when (parts.getOrNull(0)) {
                TAG_TEXT -> {
                    val id = parts.getOrNull(1) ?: continue
                    val text = unescape(parts.getOrNull(2) ?: "")
                    blocks.add(DocumentBlock.Text(id, text))
                }
                TAG_IMAGE -> {
                    val id = parts.getOrNull(1) ?: continue
                    val fileName = unescape(parts.getOrNull(2) ?: "")
                    if (fileName.isEmpty()) continue
                    val aspect = DocumentBlock.sanitizeAspectRatio(
                        parts.getOrNull(3)?.toFloatOrNull() ?: 1f
                    )
                    val width = DocumentBlock.sanitizeWidthFraction(
                        parts.getOrNull(4)?.toFloatOrNull() ?: 1f
                    )
                    blocks.add(DocumentBlock.Image(id, fileName, aspect, width))
                }
                // 未知标签（未来版本新增的块类型）：跳过，保证向前兼容不崩
                else -> Unit
            }
        }
        // 全部行都不可解析时退回纯文本，避免用户看到空白笔记
        return blocks.ifEmpty { fallback }
    }

    /** 提取纯文本内容，用于搜索、分享与首页预览。图片块不产出文本。 */
    fun plainText(blocks: List<DocumentBlock>): String =
        blocks.filterIsInstance<DocumentBlock.Text>()
            .joinToString("\n") { it.text }
            .trim()

    /** 文档中引用到的全部图片文件名，用于孤儿清理。 */
    fun referencedImageFiles(blocks: List<DocumentBlock>): Set<String> =
        blocks.filterIsInstance<DocumentBlock.Image>().map { it.fileName }.toSet()

    private fun escape(raw: String): String {
        val sb = StringBuilder(raw.length + 8)
        for (c in raw) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun unescape(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (raw[i + 1]) {
                    '\\' -> { sb.append('\\'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    else -> { sb.append(c); i++ }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
