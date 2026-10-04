package com.example.notesapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RichDocumentCodecTest {

    @Test
    fun `空文档解码为单段纯文本_保持旧笔记兼容`() {
        val blocks = RichDocumentCodec.decode("", "旧笔记正文")
        assertEquals(1, blocks.size)
        val text = blocks[0] as DocumentBlock.Text
        assertEquals("旧笔记正文", text.text)
    }

    @Test
    fun `损坏内容回退为纯文本_不丢内容`() {
        val blocks = RichDocumentCodec.decode("这不是合法格式", "兜底正文")
        assertEquals(1, blocks.size)
        assertEquals("兜底正文", (blocks[0] as DocumentBlock.Text).text)
    }

    @Test
    fun `编码后解码_文字与图片往返一致`() {
        val original = listOf(
            DocumentBlock.Text("t1", "第一段"),
            DocumentBlock.Image("i1", "a.jpg", 1.5f, 0.6f),
            DocumentBlock.Text("t2", "第二段")
        )

        val decoded = RichDocumentCodec.decode(RichDocumentCodec.encode(original))

        assertEquals(3, decoded.size)
        assertEquals("第一段", (decoded[0] as DocumentBlock.Text).text)
        val image = decoded[1] as DocumentBlock.Image
        assertEquals("a.jpg", image.fileName)
        assertEquals(1.5f, image.aspectRatio, 0.001f)
        assertEquals(0.6f, image.widthFraction, 0.001f)
        assertEquals("第二段", (decoded[2] as DocumentBlock.Text).text)
    }

    @Test
    fun `文本中的换行与制表符能正确往返`() {
        val tricky = "第一行\n第二行\t带制表符\\带反斜杠\r回车"
        val blocks = listOf(DocumentBlock.Text("t1", tricky))

        val decoded = RichDocumentCodec.decode(RichDocumentCodec.encode(blocks))

        assertEquals(1, decoded.size)
        assertEquals(tricky, (decoded[0] as DocumentBlock.Text).text)
    }

    @Test
    fun `多行文本不会破坏块结构`() {
        val blocks = listOf(
            DocumentBlock.Text("t1", "a\nb\nc"),
            DocumentBlock.Image("i1", "x.png", 2f, 1f),
            DocumentBlock.Text("t2", "d\ne")
        )

        val decoded = RichDocumentCodec.decode(RichDocumentCodec.encode(blocks))

        assertEquals(3, decoded.size)
        assertTrue(decoded[0] is DocumentBlock.Text)
        assertTrue(decoded[1] is DocumentBlock.Image)
        assertTrue(decoded[2] is DocumentBlock.Text)
        assertEquals("d\ne", (decoded[2] as DocumentBlock.Text).text)
    }

    @Test
    fun `非法宽高比被修正为1`() {
        val blocks = listOf(DocumentBlock.Image("i1", "a.jpg", 0f, 1f))
        val decoded = RichDocumentCodec.decode(RichDocumentCodec.encode(blocks))
        val image = decoded[0] as DocumentBlock.Image
        assertEquals(1f, image.aspectRatio, 0.001f)
    }

    @Test
    fun `宽度比例被夹到合法区间`() {
        assertEquals(
            DocumentBlock.MIN_WIDTH_FRACTION,
            DocumentBlock.sanitizeWidthFraction(0.01f),
            0.001f
        )
        assertEquals(
            DocumentBlock.MAX_WIDTH_FRACTION,
            DocumentBlock.sanitizeWidthFraction(5f),
            0.001f
        )
    }

    @Test
    fun `纯文本提取只保留文字块`() {
        val blocks = listOf(
            DocumentBlock.Text("t1", "上"),
            DocumentBlock.Image("i1", "a.jpg", 1f, 1f),
            DocumentBlock.Text("t2", "下")
        )
        assertEquals("上\n下", RichDocumentCodec.plainText(blocks))
    }

    @Test
    fun `引用图片集合用于孤儿清理`() {
        val blocks = listOf(
            DocumentBlock.Image("i1", "a.jpg", 1f, 1f),
            DocumentBlock.Text("t1", "文字"),
            DocumentBlock.Image("i2", "b.png", 1f, 1f)
        )
        assertEquals(setOf("a.jpg", "b.png"), RichDocumentCodec.referencedImageFiles(blocks))
    }

    @Test
    fun `文件名含制表符等特殊字符仍可往返`() {
        val blocks = listOf(DocumentBlock.Image("i1", "we\tird\\name.jpg", 1.2f, 0.5f))
        val decoded = RichDocumentCodec.decode(RichDocumentCodec.encode(blocks))
        assertEquals("we\tird\\name.jpg", (decoded[0] as DocumentBlock.Image).fileName)
    }

    @Test
    fun `空块列表编码为空串`() {
        assertEquals("", RichDocumentCodec.encode(emptyList()))
    }

    @Test
    fun `空文件名图片块被丢弃`() {
        val blocks = listOf(DocumentBlock.Image("i1", "", 1f, 1f))
        val decoded = RichDocumentCodec.decode(RichDocumentCodec.encode(blocks))
        // 无可解析块时回退为纯文本块，不返回空列表
        assertNotNull(decoded)
        assertTrue(decoded.none { it is DocumentBlock.Image })
    }
}
