// SPDX-FileCopyrightText: 2026 深圳哇蓝文化科技有限公司 (ShenZhen wowblue culture and technology CO.,LTD.)
// SPDX-License-Identifier: MIT

package com.mengpaw.plugin.connector.yinxiang

import com.mengpaw.kernel.plugin.CommandHandler
import com.mengpaw.kernel.plugin.Plugin
import com.mengpaw.kernel.plugin.PluginMetadata
import com.mengpaw.kernel.plugin.PluginType

/**
 * 印象笔记连接器 (外部分发, 不内置) — EDAM 云 API 直连 (app.yinxiang.com)。
 *
 * 上游: Evernote 官方 Java SDK (evernote-api 1.25.1, Apache/Evernote SDK License),
 * 仅协议互操作调用, 不复制其代码。
 *
 * 能力: 搜索 / 读笔记 (含附件下载) / 建改删笔记 / 笔记本与标签列表。
 * 认证: Developer Token (dev.yinxiang.com 申请, 有效期约 7 天), 经 config 命令写入本地配置。
 */
class YinxiangConnectorPlugin : Plugin {

    override val metadata = PluginMetadata(
        id = PLUGIN_ID,
        name = "印象笔记",
        version = "0.1.0",
        type = PluginType.NATIVE,
        author = "MengPaw",
        description = "印象笔记连接器 — EDAM 云 API 直连: 搜索/读写笔记/附件下载/笔记本与标签",
        permissions = listOf("INTERNET"),
        minCoreVersion = "0.20.0",
        commands = listOf(
            "connector-yinxiang.config",
            "connector-yinxiang.info",
            "connector-yinxiang.search",
            "connector-yinxiang.get",
            "connector-yinxiang.create",
            "connector-yinxiang.update",
            "connector-yinxiang.delete",
            "connector-yinxiang.notebooks",
            "connector-yinxiang.tags"
        )
    )

    override val commands: Map<String, CommandHandler> = mapOf(
        "config" to YinxiangCommandHandlers.config,
        "info" to YinxiangCommandHandlers.info,
        "search" to YinxiangCommandHandlers.search,
        "get" to YinxiangCommandHandlers.get,
        "create" to YinxiangCommandHandlers.create,
        "update" to YinxiangCommandHandlers.update,
        "delete" to YinxiangCommandHandlers.delete,
        "notebooks" to YinxiangCommandHandlers.notebooks,
        "tags" to YinxiangCommandHandlers.tags
    )

    companion object {
        const val PLUGIN_ID = "connector-yinxiang-plugin"
    }
}
