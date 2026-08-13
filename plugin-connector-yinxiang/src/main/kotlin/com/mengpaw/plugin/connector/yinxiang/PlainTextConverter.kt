// SPDX-FileCopyrightText: 2026 深圳哇蓝文化科技有限公司 (ShenZhen wowblue culture and technology CO.,LTD.)
// SPDX-License-Identifier: MIT

package com.mengpaw.plugin.connector.yinxiang

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * 纯文本互转实现 — ENML→纯文本 (jsoup 解析) 与 纯文本→ENML (逐行 div)。
 *
 * ENML→纯文本规则:
 * - div/p/section/h1-h6/br 产生换行; li 前缀 "- "; 连续空行合并为最多 2 行
 * - en-todo 渲染为 [x] / [ ]; img 渲染为 [图片]
 * - 超链接只保留可见文本; HTML 实体由 jsoup 自动解码
 */
class PlainTextConverter : NoteContentConverter {

    override fun enmlToText(enml: String): String {
        if (enml.isBlank()) return ""
        val doc = try {
            Jsoup.parse(enml)
        } catch (_: Exception) {
            // 非 HTML 内容原样返回, 不吞内容
            return enml
        }
        val root = doc.selectFirst("en-note") ?: doc.body()
        val sb = StringBuilder()
        renderChildren(root, sb)
        return sb.toString()
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    override fun textToEnml(text: String): String {
        val body = text.trim().lines().joinToString("") { line ->
            val escaped = escapeHtml(line)
            if (escaped.isEmpty()) "<div><br/></div>" else "<div>$escaped</div>"
        }
        return "<en-note>$body</en-note>"
    }

    private fun renderChildren(el: Element, sb: StringBuilder) {
        for (node in el.childNodes()) {
            when (node) {
                is TextNode -> sb.append(node.text())
                is Element -> renderElement(node, sb)
                else -> Unit
            }
        }
    }

    private fun renderElement(el: Element, sb: StringBuilder) {
        when (el.tagName()) {
            "br" -> {
                if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
            }
            "div", "p", "section", "blockquote" -> {
                ensureNewline(sb)
                renderChildren(el, sb)
                ensureNewline(sb)
            }
            "li" -> {
                ensureNewline(sb)
                sb.append("- ")
                renderChildren(el, sb)
                ensureNewline(sb)
            }
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                ensureNewline(sb)
                renderChildren(el, sb)
                ensureNewline(sb)
            }
            "en-todo" -> {
                val checked = el.hasAttr("checked")
                sb.append(if (checked) "[x]" else "[ ]")
            }
            "img" -> sb.append("[图片]")
            "en-media" -> sb.append("[附件]")
            "ul", "ol", "table", "thead", "tbody", "tr", "td", "th", "en-note", "span", "a",
            "b", "strong", "i", "em", "u", "s", "code", "font" -> renderChildren(el, sb)
            else -> renderChildren(el, sb)
        }
    }

    private fun ensureNewline(sb: StringBuilder) {
        if (sb.isEmpty()) return
        if (sb.last() != '\n') sb.append('\n')
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
