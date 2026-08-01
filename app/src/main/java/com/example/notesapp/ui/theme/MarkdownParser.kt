package com.example.notesapp.ui.theme

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

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

/**
 * 将带有内联标记的字符串解析为 AnnotatedString，支持粗体和斜体。
 */
fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val boldRegex = Regex("""\*\*(.+?)\*\*""")
        val italicRegex = Regex("""\*(.+?)\*""")

        // 先处理粗体
        val boldParts = boldRegex.split(text)
        for (i in boldParts.indices) {
            val part = boldParts[i]
            if (i % 2 == 1) {
                // 这部分是粗体内容
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(part)
                pop()
            } else {
                // 这部分可能包含斜体
                val italicParts = italicRegex.split(part)
                for (j in italicParts.indices) {
                    val italicPart = italicParts[j]
                    if (j % 2 == 1) {
                        pushStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                        append(italicPart)
                        pop()
                    } else {
                        append(italicPart)
                    }
                }
            }
        }
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
            trimmedLine.matches(Regex("""^\d+\. .*""")) -> {
                val matchResult = Regex("""^(\d+)\. (.*)""").find(trimmedLine)
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
                    !lines[i].trimStart().matches(Regex("""^\d+\. .*"""))) {
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
