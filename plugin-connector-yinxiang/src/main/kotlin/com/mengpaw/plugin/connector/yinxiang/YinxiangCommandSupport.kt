// SPDX-FileCopyrightText: 2026 深圳哇蓝文化科技有限公司 (ShenZhen wowblue culture and technology CO.,LTD.)
// SPDX-License-Identifier: MIT

package com.mengpaw.plugin.connector.yinxiang

import com.mengpaw.kernel.cli.ErrorCodes
import com.mengpaw.kernel.cli.ExecutionResult
import com.mengpaw.plugin.connector.common.ConnectorConfigStore
import com.evernote.auth.EvernoteService

/**
 * 命令共享工具 — 参数解析 / token 访问 / 笔记本解析 / 文件名消毒。
 * 独立成文件以保持命令处理器单文件 ≤400 行 (项目铁律)。
 */
object YinxiangCommandSupport {

    /** 端点单源: 与 EDAM 客户端一致取自 [EvernoteService.YINXIANG], 避免双处硬编码。 */
    val YINXIANG_ENDPOINT: String = "https://" + EvernoteService.YINXIANG.host
    const val DEFAULT_LIMIT = 20

    fun tokenOrNull(): String? {
        val token = ConnectorConfigStore.read(YinxiangConnectorPlugin.PLUGIN_ID).token
        return token?.takeIf { it.isNotBlank() }
    }

    fun storeOrNull(): NoteStoreGateway? {
        val token = tokenOrNull() ?: return null
        val factory = YinxiangCommandHandlers.gatewayFactory
        return if (factory != null) factory(token) else EdamNoteStore(token)
    }

    fun tokenRequired(): ExecutionResult = ExecutionResult.fail(
        "印象笔记 token 未配置 — 先执行 connector-yinxiang.config --token-file <路径> --yes",
        errorCode = ErrorCodes.ERR_INVALID_INPUT
    )

    fun describe(cfg: ConnectorConfigStore.ConnectorConfig): String {
        val token = cfg.token
        return "Token: ${if (token.isNullOrBlank()) "未配置" else "已配置 (长度 ${token.length})"}\n" +
            "端点: $YINXIANG_ENDPOINT\n" +
            "凭据存储: 配置/connector-yinxiang-plugin-connector.json (脱敏)"
    }

    suspend fun notebookNameMap(store: NoteStoreGateway): Map<String?, String> = try {
        store.listNotebooks().associate { it.guid to (it.name ?: it.guid ?: "?") }
    } catch (_: Exception) {
        // 笔记本列表仅用于搜索结果美化 — 拉取失败时降级为只显示 guid, 不影响搜索主流程
        emptyMap()
    }

    suspend fun resolveNotebookGuid(store: NoteStoreGateway, nameOrGuid: String): String {
        val notebooks = try {
            store.listNotebooks()
        } catch (e: Exception) {
            throw IllegalStateException("获取笔记本列表失败: ${e.toYinxiangMessage()}")
        }
        val hit = notebooks.firstOrNull { it.guid == nameOrGuid }
            ?: notebooks.firstOrNull { it.name == nameOrGuid }
            ?: throw IllegalStateException(
                "找不到笔记本「$nameOrGuid」 — 可用: connector-yinxiang.notebooks 查看"
            )
        return hit.guid
    }

    fun sanitizeFileName(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_')
        return cleaned.take(80).ifBlank { "unnamed" }
    }

    fun flagValue(args: List<String>, flag: String): String? {
        val index = args.indexOf(flag)
        return if (index >= 0 && index + 1 < args.size) args[index + 1] else null
    }

    fun flagValues(args: List<String>, flag: String): List<String> {
        val out = mutableListOf<String>()
        var index = 0
        while (index < args.size) {
            if (args[index] == flag && index + 1 < args.size) {
                out += args[index + 1]
                index += 2
            } else {
                index += 1
            }
        }
        return out
    }

    fun positionals(args: List<String>): List<String> = args.filter { !it.startsWith("--") }
}
