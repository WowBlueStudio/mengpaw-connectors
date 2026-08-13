// SPDX-FileCopyrightText: 2026 深圳哇蓝文化科技有限公司 (ShenZhen wowblue culture and technology CO.,LTD.)
// SPDX-License-Identifier: MIT

package com.mengpaw.plugin.connector.yinxiang

import com.evernote.auth.EvernoteAuth
import com.evernote.auth.EvernoteService
import com.evernote.clients.ClientFactory
import com.evernote.edam.error.EDAMErrorCode
import com.evernote.edam.error.EDAMNotFoundException
import com.evernote.edam.error.EDAMSystemException
import com.evernote.edam.error.EDAMUserException
import com.evernote.edam.notestore.NoteFilter
import com.evernote.edam.notestore.NotesMetadataList
import com.evernote.edam.notestore.NotesMetadataResultSpec
import com.evernote.edam.type.Note
import com.evernote.edam.type.Notebook
import com.evernote.edam.type.Tag
import com.evernote.thrift.transport.TTransportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 印象笔记 NoteStore 网关 — 命令层只依赖此接口, 测试注入 fake。 */
interface NoteStoreGateway {
    suspend fun listNotebooks(): List<Notebook>
    suspend fun listTags(): List<Tag>
    suspend fun findNotesMetadata(
        filter: NoteFilter,
        offset: Int,
        maxNotes: Int,
        spec: NotesMetadataResultSpec
    ): NotesMetadataList

    suspend fun getNote(guid: String, withContent: Boolean, withResourcesData: Boolean): Note
    /** 按资源 guid 下载原始字节 (正文与附件分离, 避免大附件全量进内存)。 */
    suspend fun getResourceData(resourceGuid: String): ByteArray
    suspend fun createNote(note: Note): Note
    suspend fun updateNote(note: Note): Note
    suspend fun deleteNote(guid: String)
}

/**
 * EDAM 真实实现 — 每次操作新建客户端, token 更新即时生效。
 * ClientFactory 首次 createNoteStoreClient 会经 UserStore 获取 NoteStore URL,
 * 之后直接使用缓存 URL, 对低频命令可接受。
 */
class EdamNoteStore(private val token: String) : NoteStoreGateway {

    private fun client() = ClientFactory(EvernoteAuth(EvernoteService.YINXIANG, token)).createNoteStoreClient()

    override suspend fun listNotebooks(): List<Notebook> = io { client().listNotebooks() }
    override suspend fun listTags(): List<Tag> = io { client().listTags() }

    override suspend fun findNotesMetadata(
        filter: NoteFilter,
        offset: Int,
        maxNotes: Int,
        spec: NotesMetadataResultSpec
    ): NotesMetadataList = io { client().findNotesMetadata(filter, offset, maxNotes, spec) }

    override suspend fun getNote(guid: String, withContent: Boolean, withResourcesData: Boolean): Note =
        io { client().getNote(guid, withContent, withResourcesData, false, false) }

    override suspend fun getResourceData(resourceGuid: String): ByteArray =
        io { client().getResourceData(resourceGuid) }

    override suspend fun createNote(note: Note): Note = io { client().createNote(note) }
    override suspend fun updateNote(note: Note): Note = io { client().updateNote(note) }
    override suspend fun deleteNote(guid: String) {
        io { client().deleteNote(guid) }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}

/** EDAM 异常 → 中文可读错误消息 (API Key / token 不出现在任何输出)。 */
internal fun Throwable.toYinxiangMessage(): String = when (this) {
    is EDAMUserException -> when (errorCode) {
        EDAMErrorCode.INVALID_AUTH, EDAMErrorCode.AUTH_EXPIRED ->
            "印象笔记认证失败: token 无效或已过期 (有效期 7 天, 用 connector-yinxiang.config 更新)"
        EDAMErrorCode.PERMISSION_DENIED -> "印象笔记无权限执行该操作"
        EDAMErrorCode.LIMIT_REACHED, EDAMErrorCode.QUOTA_REACHED -> "超出印象笔记配额/限流限制"
        EDAMErrorCode.ENML_VALIDATION -> "笔记内容不符合 ENML 格式要求"
        else -> "印象笔记请求被拒绝: ${errorCode?.name ?: "未知"}" +
            (parameter?.let { " (参数: $it)" } ?: "")
    }
    is EDAMSystemException -> {
        val rate = if (rateLimitDuration > 0) " (限流 $rateLimitDuration 秒)" else ""
        "印象笔记服务异常: ${message ?: errorCode?.name ?: "未知"}$rate"
    }
    is EDAMNotFoundException -> "印象笔记目标不存在: ${identifier ?: "未知"}"
    is TTransportException -> "无法连接印象笔记服务器 (检查网络): ${message ?: "网络错误"}"
    else -> "印象笔记操作失败: ${message ?: this::class.simpleName ?: "未知错误"}"
}
