// SPDX-FileCopyrightText: 2026 深圳哇蓝文化科技有限公司 (ShenZhen wowblue culture and technology CO.,LTD.)
// SPDX-License-Identifier: MIT

package com.mengpaw.plugin.connector.yinxiang

import com.evernote.edam.notestore.NoteFilter
import com.evernote.edam.notestore.NotesMetadataResultSpec
import com.evernote.edam.type.Note
import com.mengpaw.kernel.DataPaths
import com.mengpaw.kernel.cli.ErrorCodes
import com.mengpaw.kernel.cli.ExecutionContext
import com.mengpaw.kernel.cli.ExecutionResult
import com.mengpaw.kernel.plugin.CommandHandler
import com.mengpaw.plugin.connector.common.ConnectorConfigStore
import kotlinx.coroutines.CancellationException
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 印象笔记连接器命令实现 — 全部命令以 [NoteStoreGateway] 为唯一数据通道 (可测试注入 fake)。
 *
 * 安全约定:
 * - token 只经 ConnectorConfigStore 读写, 输出/日志一律脱敏 (只回显长度);
 * - --token-file 为推荐注入方式 (token 不进入命令文本/会话历史/审计日志);
 * - 附件文件名做消毒, 防目录穿越。
 */
object YinxiangCommandHandlers {

    /** 印象笔记单资源大小上限 (约 EDAM 资源上限), 超过则跳过并提示, 防 OOM。internal 供测试断言。 */
    internal const val MAX_RESOURCE_BYTES = 25 * 1024 * 1024
    private val converter: NoteContentConverter = PlainTextConverter()
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val support = YinxiangCommandSupport

    /** 网关工厂 — 生产默认 [EdamNoteStore]; 测试注入 fake 以覆盖成功路径。 */
    @Volatile
    internal var gatewayFactory: ((String) -> NoteStoreGateway)? = null

    val config: CommandHandler = ::configHandler
    val info: CommandHandler = ::infoHandler
    val search: CommandHandler = ::searchHandler
    val get: CommandHandler = ::getHandler
    val create: CommandHandler = ::createHandler
    val update: CommandHandler = ::updateHandler
    val delete: CommandHandler = ::deleteHandler
    val notebooks: CommandHandler = ::notebooksHandler
    val tags: CommandHandler = ::tagsHandler

    // ── config ─────────────────────────────────────────────────────────

    private suspend fun configHandler(args: List<String>, ctx: ExecutionContext): ExecutionResult {
        val current = ConnectorConfigStore.read(YinxiangConnectorPlugin.PLUGIN_ID)
        if (args.isEmpty()) {
            return ExecutionResult.ok(
                "当前印象笔记配置:\n" + support.describe(current) +
                    "\n用法: connector-yinxiang.config [--token <token> | --token-file <路径>] --yes\n" +
                    "推荐 --token-file (token 不进入会话历史/审计日志)。"
            )
        }
        if (!args.contains("--yes")) {
        val state = current.token?.let { "已配置 (长度 ${it.length})" } ?: "未配置"
            return ExecutionResult.fail(
                "⚠️ 覆盖印象笔记 token 需确认 — 当前: $state。配置未改变。\n" +
                    "确认请执行: connector-yinxiang.config --yes --token-file <路径>",
                errorCode = ErrorCodes.ERR_INVALID_INPUT
            )
        }
        val token = when {
            args.contains("--token-file") -> {
                val path = support.flagValue(args, "--token-file")
                if (path.isNullOrBlank()) {
                    return ExecutionResult.fail(
                        "用法: connector-yinxiang.config --token-file <路径> --yes",
                        errorCode = ErrorCodes.ERR_INVALID_INPUT
                    )
                }
                val f = File(path)
                val content = try {
                    if (!f.isFile) null else f.readText().trim()
                } catch (_: Exception) {
                    null
                }
                if (content == null) {
                    return ExecutionResult.fail(
                        "读取 token 文件失败或文件不存在: $path",
                        errorCode = ErrorCodes.ERR_IO
                    )
                }
                content
            }
            args.contains("--token") -> support.flagValue(args, "--token").orEmpty().trim()
            else -> {
                return ExecutionResult.fail(
                    "缺少 token 来源: --token <token> 或 --token-file <路径>",
                    errorCode = ErrorCodes.ERR_INVALID_INPUT
                )
            }
        }
        if (token.isBlank()) {
            return ExecutionResult.fail("token 不能为空", errorCode = ErrorCodes.ERR_INVALID_INPUT)
        }
        ConnectorConfigStore.write(
            YinxiangConnectorPlugin.PLUGIN_ID,
            ConnectorConfigStore.ConnectorConfig(token = token)
        )
        return ExecutionResult.ok("已保存印象笔记 token (长度 ${token.length})。")
    }

    // ── info ───────────────────────────────────────────────────────────

    private suspend fun infoHandler(args: List<String>, ctx: ExecutionContext): ExecutionResult {
        val token = support.tokenOrNull()
        if (token == null) {
            return ExecutionResult.ok(
                "连接器: connector-yinxiang\n端点: ${support.YINXIANG_ENDPOINT}\n" +
                    "Token: 未配置\n连接状态: 未连接\n" +
                    "先执行 connector-yinxiang.config --token-file <路径> --yes 配置 token。"
            )
        }
        val status = try {
            EdamNoteStore(token).listNotebooks()
            "正常"
        } catch (e: Exception) {
            "异常: ${e.toYinxiangMessage()}"
        }
        return ExecutionResult.ok(
            "连接器: connector-yinxiang\n端点: ${support.YINXIANG_ENDPOINT}\n" +
                "Token: 已配置 (长度 ${token.length})\n连接状态: $status"
        )
    }

    // ── search ─────────────────────────────────────────────────────────

    private suspend fun searchHandler(args: List<String>, ctx: ExecutionContext): ExecutionResult {
        val store = support.storeOrNull() ?: return support.tokenRequired()
        val query = support.positionals(args).firstOrNull().orEmpty()
        val limit = (support.flagValue(args, "--limit")?.toIntOrNull()?.coerceIn(1, 100)) ?: support.DEFAULT_LIMIT
        val filter = NoteFilter().apply { words = query }
        val spec = NotesMetadataResultSpec().apply {
            setIncludeTitle(true)
            setIncludeUpdated(true)
            setIncludeNotebookGuid(true)
        }
        val list = try {
            store.findNotesMetadata(filter, 0, limit, spec)
        } catch (e: Exception) {
            rethrowIfCancelled(e)
            return ExecutionResult.fail(e.toYinxiangMessage(), errorCode = ErrorCodes.ERR_INTERNAL)
        }
        val notebooks = support.notebookNameMap(store)
        val meta = list.notes ?: emptyList()
        val sb = StringBuilder()
        sb.appendLine("搜索「${query.ifBlank { "全部" }}」: 共 ${list.totalNotes} 条 (显示前 ${meta.size})")
        if (meta.isEmpty()) sb.appendLine("(无结果)")
        meta.forEachIndexed { index, m ->
            val name = notebooks[m.notebookGuid] ?: m.notebookGuid ?: "-"
            val updated = if (m.updated > 0) formatTime(m.updated) else "-"
            sb.appendLine("${index + 1}. ${m.title ?: "(无标题)"} [${m.guid}] [更新: $updated] [笔记本: $name]")
        }
        return ExecutionResult.ok(sb.toString().trimEnd())
    }

    // ── get ─────────────────────────────────────────────────────────────

    private suspend fun getHandler(args: List<String>, ctx: ExecutionContext): ExecutionResult {
        val guid = support.positionals(args).firstOrNull()
        if (guid.isNullOrBlank()) {
            return ExecutionResult.fail(
                "用法: connector-yinxiang.get <guid> [--out <目录>]",
                errorCode = ErrorCodes.ERR_INVALID_INPUT
            )
        }
        val store = support.storeOrNull() ?: return support.tokenRequired()
        val note = try {
            store.getNote(guid, true, false)
        } catch (e: Exception) {
            rethrowIfCancelled(e)
            return ExecutionResult.fail(e.toYinxiangMessage(), errorCode = ErrorCodes.ERR_INTERNAL)
        }
        val sb = StringBuilder()
        sb.appendLine("标题: ${note.title ?: "(无标题)"}")
        sb.appendLine("GUID: ${note.guid}")
        sb.appendLine("更新: ${if (note.updated > 0) formatTime(note.updated) else "-"}")
        sb.appendLine("────────")
        sb.appendLine(converter.enmlToText(note.content.orEmpty()))
        sb.appendLine("────────")
        val resources = note.resources ?: emptyList()
        if (resources.isNotEmpty()) {
            val outDir = support.flagValue(args, "--out") ?: "${DataPaths.BASE}/outputs/yinxiang"
            val noteDir = File(outDir, guid)
            val saved = mutableListOf<Pair<String, String>>()
            resources.forEach { res ->
                val rawName = res.attributes?.fileName ?: "resource-${res.guid}"
                val fileName = support.sanitizeFileName(rawName)
                val target = File(noteDir, fileName)
                try {
                    val body = store.getResourceData(res.guid)
                    if (body.size > MAX_RESOURCE_BYTES) {
                        saved += (fileName to "超过大小上限 ${MAX_RESOURCE_BYTES / 1024 / 1024}MB, 跳过")
                        return@forEach
                    }
                    if (body.isEmpty()) {
                        saved += (fileName to "下载为空")
                        return@forEach
                    }
                    target.parentFile?.mkdirs()
                    target.writeBytes(body)
                    saved += (fileName to target.absolutePath)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    saved += (fileName to "下载失败: ${e.message}")
                }
            }
            sb.appendLine("附件 (${saved.size}/${resources.size}):")
            saved.forEach { (name, path) -> sb.appendLine("  - $name → $path") }
        } else {
            sb.appendLine("附件: 无")
        }
        return ExecutionResult.ok(sb.toString().trimEnd())
    }

    // ── create ──────────────────────────────────────────────────────────

    private suspend fun createHandler(args: List<String>, ctx: ExecutionContext): ExecutionResult {
        val positional = support.positionals(args)
        if (positional.size < 2) {
            return ExecutionResult.fail(
                "用法: connector-yinxiang.create <标题> <正文> [--notebook <名称|guid>] [--tag <名称>...]",
                errorCode = ErrorCodes.ERR_INVALID_INPUT
            )
        }
        val store = support.storeOrNull() ?: return support.tokenRequired()
        val title = positional[0]
        val content = positional.drop(1).joinToString(" ")
        val notebook = support.flagValue(args, "--notebook")
        val tags = support.flagValues(args, "--tag")
        val note = try {
            Note().apply {
                this.title = title
                this.content = converter.textToEnml(content)
                if (!notebook.isNullOrBlank()) {
                    notebookGuid = support.resolveNotebookGuid(store, notebook)
                }
                if (tags.isNotEmpty()) tagNames = tags
            }
        } catch (e: IllegalStateException) {
            return ExecutionResult.fail(e.message ?: "参数错误", errorCode = ErrorCodes.ERR_INVALID_INPUT)
        }
        val created = try {
            store.createNote(note)
        } catch (e: Exception) {
            rethrowIfCancelled(e)
            return ExecutionResult.fail(e.toYinxiangMessage(), errorCode = ErrorCodes.ERR_INTERNAL)
        }
        return ExecutionResult.ok("已创建笔记: ${created.title ?: "(无标题)"} [${created.guid}]")
    }

    // ── update ──────────────────────────────────────────────────────────

    private suspend fun updateHandler(args: List<String>, ctx: ExecutionContext): ExecutionResult {
        val guid = support.positionals(args).firstOrNull()
        if (guid.isNullOrBlank()) {
            return ExecutionResult.fail(
                "用法: connector-yinxiang.update <guid> [--title <标题>] [--content <正文>] [--notebook <名称|guid>]",
                errorCode = ErrorCodes.ERR_INVALID_INPUT
            )
        }
        val store = support.storeOrNull() ?: return support.tokenRequired()
        val existing = try {
            store.getNote(guid, true, false)
        } catch (e: Exception) {
            rethrowIfCancelled(e)
            return ExecutionResult.fail(e.toYinxiangMessage(), errorCode = ErrorCodes.ERR_INTERNAL)
        }
        // 仅更新标题/内容/笔记本字段 — 清空资源列表, 防 updateNote 携带不完整资源数据触发服务端校验分歧
        existing.unsetResources()
        val newTitle = support.flagValue(args, "--title")
        val newContent = support.flagValue(args, "--content")
        val newNotebook = support.flagValue(args, "--notebook")
        if (newTitle.isNullOrBlank() && newContent.isNullOrBlank() && newNotebook.isNullOrBlank()) {
            return ExecutionResult.fail(
                "未提供变更参数 (--title / --content / --notebook 至少一项)",
                errorCode = ErrorCodes.ERR_INVALID_INPUT
            )
        }
        if (!newTitle.isNullOrBlank()) existing.title = newTitle
        if (!newContent.isNullOrBlank()) existing.content = converter.textToEnml(newContent)
        if (!newNotebook.isNullOrBlank()) {
            existing.notebookGuid = try {
                support.resolveNotebookGuid(store, newNotebook)
            } catch (e: IllegalStateException) {
                return ExecutionResult.fail(e.message ?: "参数错误", errorCode = ErrorCodes.ERR_INVALID_INPUT)
            }
        }
        val saved = try {
            store.updateNote(existing)
        } catch (e: Exception) {
            rethrowIfCancelled(e)
            return ExecutionResult.fail(e.toYinxiangMessage(), errorCode = ErrorCodes.ERR_INTERNAL)
        }
        return ExecutionResult.ok("已更新笔记: ${saved.title ?: "(无标题)"} [${saved.guid}]")
    }

    // ── delete ──────────────────────────────────────────────────────────

    private suspend fun deleteHandler(args: List<String>, ctx: ExecutionContext): ExecutionResult {
        val guid = support.positionals(args).firstOrNull()
        if (guid.isNullOrBlank()) {
            return ExecutionResult.fail(
                "用法: connector-yinxiang.delete <guid>",
                errorCode = ErrorCodes.ERR_INVALID_INPUT
            )
        }
        val store = support.storeOrNull() ?: return support.tokenRequired()
        try {
            store.deleteNote(guid)
        } catch (e: Exception) {
            rethrowIfCancelled(e)
            return ExecutionResult.fail(e.toYinxiangMessage(), errorCode = ErrorCodes.ERR_INTERNAL)
        }
        return ExecutionResult.ok("已移入废纸篓: $guid (可在印象笔记废纸篓恢复)")
    }

    // ── notebooks / tags ────────────────────────────────────────────────

    private suspend fun notebooksHandler(args: List<String>, ctx: ExecutionContext): ExecutionResult {
        val store = support.storeOrNull() ?: return support.tokenRequired()
        val list = try {
            store.listNotebooks()
        } catch (e: Exception) {
            rethrowIfCancelled(e)
            return ExecutionResult.fail(e.toYinxiangMessage(), errorCode = ErrorCodes.ERR_INTERNAL)
        }
        if (list.isEmpty()) return ExecutionResult.ok("笔记本: (无)")
        val sb = StringBuilder("笔记本 (${list.size}):")
        list.forEach { n ->
            val mark = if (n.isDefaultNotebook()) " (默认)" else ""
            sb.appendLine("\n- ${n.name ?: "(未命名)"} [${n.guid}]$mark")
        }
        return ExecutionResult.ok(sb.toString().trimEnd())
    }

    private suspend fun tagsHandler(args: List<String>, ctx: ExecutionContext): ExecutionResult {
        val store = support.storeOrNull() ?: return support.tokenRequired()
        val list = try {
            store.listTags()
        } catch (e: Exception) {
            rethrowIfCancelled(e)
            return ExecutionResult.fail(e.toYinxiangMessage(), errorCode = ErrorCodes.ERR_INTERNAL)
        }
        if (list.isEmpty()) return ExecutionResult.ok("标签: (无)")
        val sb = StringBuilder("标签 (${list.size}):")
        list.forEach { t -> sb.appendLine("\n- ${t.name ?: "(未命名)"} [${t.guid}]") }
        return ExecutionResult.ok(sb.toString().trimEnd())
    }

    private fun formatTime(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dateTimeFormatter)

    /** 协程取消必须向上传播, 不能转成失败结果。 */
    private fun rethrowIfCancelled(e: Exception) {
        if (e is CancellationException) throw e
    }
}
