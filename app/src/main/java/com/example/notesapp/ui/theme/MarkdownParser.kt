package com.example.notesapp.ui.theme

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * 轻量级 Markdown 解析器，支持：
 * - 标题 (H1, H2)
 * - 无序/有序列表
 * - 粗体 (**text**)
 * - 斜体 (*text*)
 */

sealed class MarkdownBlock {
    data class Heading(val text: AnnotatedString, val level: Int) : MarkdownBlock()
    data class ListItem(val text: AnnotatedString, val isOrdered: Boolean, val index: Int = 0) : MarkdownBlock()
    data class Paragraph(val text: AnnotatedString) : MarkdownBlock()
    data object Blank : MarkdownBlock()
}

// 预编译正则，避免每次解析都重新编译（parseMarkdown 在 NoteCard 每次重组都会调用）
private val BOLD_REGEX = Regex("""\*\*(.+?)\*\*""")
private val ITALIC_REGEX = Regex("""\*(.+?)\*""")
private val ORDERED_LIST_REGEX = Regex("""^\d+\. .*""")
private val ORDERED_LIST_PARSE_REGEX = Regex("""^(\d+)\. (.*)""")

/**
 * 将带有内联标记的字符串解析为 AnnotatedString，支持粗体和斜体。
 *
 * 实现说明：必须基于 [Regex.findAll] 的匹配位置逐段拼接，**不能**用 [Regex.split]。
 * 因为 split 会丢弃分隔符本身，而 `**text**` 的正文位于捕获组内，
 * 用 split 会得到 ["前半", "后半"]，导致被标记的文字整体丢失
 * （例如 "普通**加粗**普通" 会被渲染成 "普通普通"）。
 *
 * 处理顺序：先粗体、再斜体；斜体只在非粗体片段内解析，避免 `**a**` 被斜体规则二次匹配。
 */
fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0

        for (boldMatch in BOLD_REGEX.findAll(text)) {
            // 粗体标记之前的普通文本，其中可能还含有斜体
            appendItalicSegment(text.substring(cursor, boldMatch.range.first))
            // 粗体内容本身：整段加粗，内部不再解析斜体标记
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(boldMatch.groupValues[1])
            }
            cursor = boldMatch.range.last + 1
        }

        // 收尾：最后一段不含粗体标记的普通文本
        if (cursor < text.length) {
            appendItalicSegment(text.substring(cursor))
        }
    }
}

/**
 * 向 [AnnotatedString.Builder] 追加一段文本，并解析其中的 `*斜体*` 标记。
 * 同样基于匹配位置处理，确保斜体文字不会丢失。
 */
private fun AnnotatedString.Builder.appendItalicSegment(segment: String) {
    var cursor = 0
    for (italicMatch in ITALIC_REGEX.findAll(segment)) {
        val before = segment.substring(cursor, italicMatch.range.first)
        if (before.isNotEmpty()) append(before)
        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            append(italicMatch.groupValues[1])
        }
        cursor = italicMatch.range.last + 1
    }
    if (cursor < segment.length) {
        append(segment.substring(cursor))
    }
}

/**
 * 将完整的 Markdown 文本解析为一组语义化的块。
 * 加 try-catch 容错：任何解析异常都降级为纯文本段落，绝不让 UI 崩溃。
 */
fun parseMarkdown(text: String): List<MarkdownBlock> {
    if (text.isBlank()) return emptyList()
    return try {
        parseMarkdownInternal(text)
    } catch (e: Exception) {
        // 降级：把整段作为普通段落返回
        listOf(MarkdownBlock.Paragraph(AnnotatedString(text)))
    }
}

private fun parseMarkdownInternal(text: String): List<MarkdownBlock> {

    val lines = text.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmedLine = line.trimStart()

        when {
            // 标题 (H1 或 H2)
            trimmedLine.startsWith("# ") -> {
                blocks.add(MarkdownBlock.Heading(parseInlineMarkdown(trimmedLine.removePrefix("# ")), 1))
                i++
            }
            trimmedLine.startsWith("## ") -> {
                blocks.add(MarkdownBlock.Heading(parseInlineMarkdown(trimmedLine.removePrefix("## ")), 2))
                i++
            }

            // 无序列表
            trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") -> {
                val content = trimmedLine.removeRange(0, 2)
                blocks.add(MarkdownBlock.ListItem(parseInlineMarkdown(content), isOrdered = false))
                i++
            }

            // 有序列表
            trimmedLine.matches(ORDERED_LIST_REGEX) -> {
                val matchResult = ORDERED_LIST_PARSE_REGEX.find(trimmedLine)
                if (matchResult != null) {
                    val index = matchResult.groupValues[1].toInt()
                    val content = matchResult.groupValues[2]
                    blocks.add(MarkdownBlock.ListItem(parseInlineMarkdown(content), isOrdered = true, index = index))
                }
                i++
            }

            // 空行
            line.isBlank() -> {
                blocks.add(MarkdownBlock.Blank)
                i++
            }

            // 普通段落
            else -> {
                // 收集连续的非空、非特殊行组成段落
                val paragraphLines = mutableListOf<String>()
                while (i < lines.size && !lines[i].isBlank() && !lines[i].trimStart().startsWith("# ") &&
                    !lines[i].trimStart().startsWith("## ") &&
                    !lines[i].trimStart().startsWith("- ") && !lines[i].trimStart().startsWith("* ") &&
                    !lines[i].trimStart().matches(ORDERED_LIST_REGEX)) {
                    paragraphLines.add(lines[i])
                    i++
                }
                val paragraphText = paragraphLines.joinToString(" ") { it.trim() }
                if (paragraphText.isNotBlank()) {
                    blocks.add(MarkdownBlock.Paragraph(parseInlineMarkdown(paragraphText)))
                }
            }
        }
    }

    // 移除连续的空行，最多保留一个
    val cleanedBlocks = mutableListOf<MarkdownBlock>()
    var lastWasBlank = false
    for (block in blocks) {
        if (block is MarkdownBlock.Blank) {
            if (!lastWasBlank && cleanedBlocks.isNotEmpty()) {
                cleanedBlocks.add(block)
            }
            lastWasBlank = true
        } else {
            cleanedBlocks.add(block)
            lastWasBlank = false
        }
    }
    // 移除首尾的空行
    while (cleanedBlocks.isNotEmpty() && cleanedBlocks.last() is MarkdownBlock.Blank) {
        cleanedBlocks.removeAt(cleanedBlocks.lastIndex)
    }
    while (cleanedBlocks.isNotEmpty() && cleanedBlocks.first() is MarkdownBlock.Blank) {
        cleanedBlocks.removeAt(0)
    }

    return cleanedBlocks
}
