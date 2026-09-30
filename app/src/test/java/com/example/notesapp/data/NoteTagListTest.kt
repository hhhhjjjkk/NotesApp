package com.example.notesapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Note.tagList] 的单元测试。
 *
 * 标签以逗号分隔字符串存储，tagList 负责解析成列表。
 * 这里锁定其容错行为：空格裁剪、空串过滤、空标签返回空列表。
 */
class NoteTagListTest {

    @Test
    fun `empty tags produce empty list`() {
        assertTrue(Note(tags = "").tagList.isEmpty())
    }

    @Test
    fun `single tag is parsed`() {
        assertEquals(listOf("工作"), Note(tags = "工作").tagList)
    }

    @Test
    fun `multiple tags are parsed in order`() {
        assertEquals(listOf("工作", "灵感", "生活"), Note(tags = "工作,灵感,生活").tagList)
    }

    @Test
    fun `whitespace around tags is trimmed`() {
        assertEquals(listOf("工作", "灵感"), Note(tags = "  工作 ,  灵感  ").tagList)
    }

    @Test
    fun `empty segments are filtered out`() {
        assertEquals(listOf("工作", "灵感"), Note(tags = "工作,,灵感,").tagList)
    }

    @Test
    fun `note defaults are sensible for a new note`() {
        val note = Note()

        assertEquals(0L, note.id)
        assertEquals("", note.title)
        assertEquals("", note.content)
        assertEquals(NoteType.NOTE, note.type)
        assertEquals(false, note.isPinned)
        assertEquals(false, note.isTrashed)
        assertEquals(0L, note.reminderAt)
    }
}
