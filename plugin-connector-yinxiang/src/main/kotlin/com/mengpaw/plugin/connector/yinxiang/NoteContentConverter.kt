// SPDX-FileCopyrightText: 2026 深圳哇蓝文化科技有限公司 (ShenZhen wowblue culture and technology CO.,LTD.)
// SPDX-License-Identifier: MIT

package com.mengpaw.plugin.connector.yinxiang

/**
 * 笔记内容转换器 — ENML 与可读格式互转。
 *
 * v1 仅实现纯文本互转 (PlainTextConverter);接口预留格式扩展点,
 * 后续新增 Markdown 转换器 (MarkdownConverter) 时命令层无需改动。
 */
interface NoteContentConverter {
    /** ENML (XHTML 受限子集) → 可读文本。 */
    fun enmlToText(enml: String): String

    /** 可读文本 → ENML。 */
    fun textToEnml(text: String): String
}
