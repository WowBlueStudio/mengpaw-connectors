// SPDX-FileCopyrightText: 2026 深圳哇蓝文化科技有限公司 (ShenZhen wowblue culture and technology CO.,LTD.)
// SPDX-License-Identifier: MIT

package com.mengpaw.plugin.connector.yinxiang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteContentConverterTest {

    private val converter: NoteContentConverter = PlainTextConverter()

    @Test
    fun `enml basic paragraphs keep line breaks`() {
        val enml = "<en-note><div>第一段</div><div>第二段</div></en-note>"
        assertEquals("第一段\n第二段", converter.enmlToText(enml))
    }

    @Test
    fun `enml list renders dash prefix`() {
        val enml = "<en-note><ul><li>项目A</li><li>项目B</li></ul></en-note>"
        assertEquals("- 项目A\n- 项目B", converter.enmlToText(enml))
    }

    @Test
    fun `enml html entities are decoded`() {
        val enml = "<en-note><div>A &amp; B &lt;C&gt;</div></en-note>"
        assertEquals("A & B <C>", converter.enmlToText(enml))
    }

    @Test
    fun `enml todo renders checked state`() {
        val enml = "<en-note><en-todo/>未完成<en-todo checked=\"true\"/>已完成</en-note>"
        val text = converter.enmlToText(enml)
        assertTrue(text.contains("[ ]"))
        assertTrue(text.contains("[x]"))
    }

    @Test
    fun `enml link keeps visible text`() {
        val enml = "<en-note><a href=\"https://example.com\">链接文字</a></en-note>"
        assertEquals("链接文字", converter.enmlToText(enml))
    }

    @Test
    fun `enml multiple blank lines collapse`() {
        val enml = "<en-note><div>A</div><div><br/></div><div><br/></div><div><br/></div><div>B</div></en-note>"
        assertFalse(converter.enmlToText(enml).contains("\n\n\n"))
    }

    @Test
    fun `blank enml returns empty`() {
        assertEquals("", converter.enmlToText(""))
        assertEquals("", converter.enmlToText("   "))
    }

    @Test
    fun `non html content passes through`() {
        assertEquals("纯文本内容", converter.enmlToText("纯文本内容"))
    }

    @Test
    fun `text to enml escapes and wraps`() {
        val enml = converter.textToEnml("a < b & c")
        assertTrue(enml.startsWith("<en-note>"))
        assertTrue(enml.endsWith("</en-note>"))
        assertTrue(enml.contains("a &lt; b &amp; c"))
    }

    @Test
    fun `text to enml converts newlines to divs`() {
        val enml = converter.textToEnml("行1\n行2")
        assertTrue(enml.contains("<div>行1</div><div>行2</div>"))
    }

    @Test
    fun `text to enml empty line becomes br div`() {
        val enml = converter.textToEnml("A\n\nB")
        assertTrue(enml.contains("<div><br/></div>"))
    }
}
