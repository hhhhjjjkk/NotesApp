package com.example.notesapp.ui.theme

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [parseMarkdown] / [parseInlineMarkdown] 的单元测试。
 *
 * 这些用例覆盖卡片渲染依赖的解析契约：
 * - 标题、无序/有序列表、段落的分块归类
 * - 行内粗体与斜体的样式标注
 * - 空行清理与异常降级（解析失败时不能崩溃，必须退化为纯文本段落）
 */
class MarkdownParserTest {

    // ===== 块级解析 =====

    @Test
    fun `blank input returns empty list`() {
        assertTrue(parseMarkdown("").isEmpty())
        assertTrue(parseMarkdown("   \n  \n").isEmpty())
    }

    @Test
    fun `h1 and h2 are parsed as headings with correct level`() {
        val blocks = parseMarkdown("# 一级标题\n## 二级标题")

        assertEquals(2, blocks.size)
        assertEquals(1, (blocks[0] as MarkdownBlock.Heading).level)
        assertEquals("一级标题", (blocks[0] as MarkdownBlock.Heading).text.text)
        assertEquals(2, (blocks[1] as MarkdownBlock.Heading).level)
        assertEquals("二级标题", (blocks[1] as MarkdownBlock.Heading).text.text)
    }

    @Test
    fun `unordered list items are parsed for both dash and asterisk markers`() {
        val blocks = parseMarkdown("- 第一项\n* 第二项")

        assertEquals(2, blocks.size)
        blocks.forEach { block ->
            val item = block as MarkdownBlock.ListItem
            assertEquals(false, item.isOrdered)
        }
        assertEquals("第一项", (blocks[0] as MarkdownBlock.ListItem).text.text)
        assertEquals("第二项", (blocks[1] as MarkdownBlock.ListItem).text.text)
    }

    @Test
    fun `ordered list keeps original index`() {
        val blocks = parseMarkdown("3. 第三项\n4. 第四项")

        assertEquals(2, blocks.size)
        val first = blocks[0] as MarkdownBlock.ListItem
        assertEquals(true, first.isOrdered)
        assertEquals(3, first.index)
        assertEquals("第三项", first.text.text)
        assertEquals(4, (blocks[1] as MarkdownBlock.ListItem).index)
    }

    @Test
    fun `consecutive plain lines are merged into one paragraph`() {
        val blocks = parseMarkdown("第一行\n第二行")

        assertEquals(1, blocks.size)
        // 段落内多行以空格连接
        assertEquals("第一行 第二行", (blocks[0] as MarkdownBlock.Paragraph).text.text)
    }

    @Test
    fun `consecutive blank lines collapse into at most one and are trimmed at edges`() {
        val blocks = parseMarkdown("\n\n第一段\n\n\n\n第二段\n\n")

        // 首尾空行被移除，中间连续空行合并为一个
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
        assertTrue(blocks[1] is MarkdownBlock.Blank)
        assertTrue(blocks[2] is MarkdownBlock.Paragraph)
    }

    // ===== 行内样式 =====

    @Test
    fun `bold text is marked as bold and markers are removed`() {
        val annotated = parseInlineMarkdown("普通**加粗**普通")

        assertEquals("普通加粗普通", annotated.text)

        val boldRanges = annotated.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(1, boldRanges.size)
        // "加粗" 位于索引 2..4
        assertEquals(2, boldRanges[0].start)
        assertEquals(4, boldRanges[0].end)
    }

    @Test
    fun `italic text is marked as italic`() {
        val annotated = parseInlineMarkdown("前*斜体*后")

        assertEquals("前斜体后", annotated.text)

        val italicRanges = annotated.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertEquals(1, italicRanges.size)
        assertEquals(1, italicRanges[0].start)
        assertEquals(3, italicRanges[0].end)
    }

    @Test
    fun `text without markup has no span styles`() {
        val annotated = parseInlineMarkdown("没有任何标记的纯文本")

        assertEquals("没有任何标记的纯文本", annotated.text)
        assertTrue(annotated.spanStyles.isEmpty())
    }

    @Test
    fun `heading containing bold renders inline style`() {
        val blocks = parseMarkdown("# 标题**重点**")

        val heading = blocks[0] as MarkdownBlock.Heading
        assertEquals("标题重点", heading.text.text)
        assertTrue(heading.text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }

    // ===== 回归测试：修复前后对比 =====

    /**
     * 回归用例：旧实现用 Regex.split 解析行内标记，会把被标记的文字整体丢弃，
     * "普通**加粗**普通" 被渲染成 "普通普通"。修复后必须完整保留全部字符。
     * 这里直接断言文本内容，不依赖样式位置。
     */
    @Test
    fun `regression marked text is never dropped`() {
        assertEquals("普通加粗普通", parseInlineMarkdown("普通**加粗**普通").text)
        assertEquals("前斜体后", parseInlineMarkdown("前*斜体*后").text)
        assertEquals("A粗B斜C", parseInlineMarkdown("A**粗**B*斜*C").text)
    }

    @Test
    fun `regression multiple bold segments keep all content`() {
        val annotated = parseInlineMarkdown("**一**中间**二**")

        assertEquals("一中间二", annotated.text)
        val boldCount = annotated.spanStyles.count { it.item.fontWeight == FontWeight.Bold }
        assertEquals(2, boldCount)
    }

    @Test
    fun `styling offsets are correct after fix`() {
        val annotated = parseInlineMarkdown("普通**加粗**普通")

        val bold = annotated.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        // "加粗" 应为索引 2..4
        assertEquals(2, bold.start)
        assertEquals(4, bold.end)
        assertEquals("加粗", annotated.text.substring(bold.start, bold.end))
    }
}
