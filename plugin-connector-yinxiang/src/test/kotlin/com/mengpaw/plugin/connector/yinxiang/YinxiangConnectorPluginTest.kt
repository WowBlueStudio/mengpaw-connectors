// SPDX-FileCopyrightText: 2026 深圳哇蓝文化科技有限公司 (ShenZhen wowblue culture and technology CO.,LTD.)
// SPDX-License-Identifier: MIT

package com.mengpaw.plugin.connector.yinxiang

import com.evernote.edam.error.EDAMErrorCode
import com.evernote.edam.error.EDAMUserException
import com.evernote.edam.notestore.NoteFilter
import com.evernote.edam.notestore.NoteMetadata
import com.evernote.edam.notestore.NotesMetadataList
import com.evernote.edam.notestore.NotesMetadataResultSpec
import com.evernote.edam.type.Data
import com.evernote.edam.type.Note
import com.evernote.edam.type.Notebook
import com.evernote.edam.type.Resource
import com.evernote.edam.type.ResourceAttributes
import com.evernote.edam.type.Tag
import com.mengpaw.kernel.DataPaths
import com.mengpaw.kernel.cli.ExecutionContext
import com.mengpaw.kernel.cli.ExecutionResult
import com.mengpaw.plugin.connector.common.ConnectorConfigStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class YinxiangConnectorPluginTest {

    private lateinit var tmpBase: File
    private val fake = FakeNoteStoreGateway()
    private val plugin = YinxiangConnectorPlugin()
    private val ctx = ExecutionContext(sessionId = "test")

    @Before
    fun setUp() {
        tmpBase = File(System.getProperty("java.io.tmpdir"), "yx-test-${System.currentTimeMillis()}")
        tmpBase.mkdirs()
        DataPaths.initialize(tmpBase.absolutePath)
        YinxiangCommandHandlers.gatewayFactory = { _ -> fake }
        ConnectorConfigStore.write(
            YinxiangConnectorPlugin.PLUGIN_ID,
            ConnectorConfigStore.ConnectorConfig(token = "test-token-123")
        )
    }

    @After
    fun tearDown() {
        YinxiangCommandHandlers.gatewayFactory = null
    }

    private fun run(cmd: String, vararg args: String): ExecutionResult {
        val handler = plugin.commands[cmd]
        assertNotNull("命令 $cmd 已注册", handler)
        val h = handler ?: error("命令 $cmd 已注册")
        return runBlocking { h.invoke(args.toList(), ctx) }
    }

    // ── 配置 ────────────────────────────────────────────────────────────

    @Test
    fun `config without args masks token`() {
        val result = run("config")
        assertTrue(result.success)
        assertTrue(result.output.contains("长度"))
        assertFalse(result.output.contains("test-token-123"))
    }

    @Test
    fun `config overwrite requires yes flag`() {
        val result = run("config", "--token", "new-token")
        assertFalse(result.success)
        assertTrue(result.error?.contains("确认") == true)
    }

    @Test
    fun `config writes token from file`() {
        val tokenFile = File(tmpBase, "token.txt")
        tokenFile.writeText("file-token-abc\n")
        val result = run("config", "--token-file", tokenFile.absolutePath, "--yes")
        assertTrue(result.success)
        assertEquals(
            "file-token-abc",
            ConnectorConfigStore.read(YinxiangConnectorPlugin.PLUGIN_ID).token
        )
    }

    @Test
    fun `unconfigured token returns actionable error`() {
        ConnectorConfigStore.write(YinxiangConnectorPlugin.PLUGIN_ID, ConnectorConfigStore.ConnectorConfig())
        val result = run("search", "hello")
        assertFalse(result.success)
        assertTrue(result.error?.contains("未配置") == true)
    }

    // ── 搜索 ────────────────────────────────────────────────────────────

    @Test
    fun `search returns title and guid`() {
        val result = run("search", "测试")
        assertTrue(result.success)
        assertTrue(result.output.contains("笔记一"))
        assertTrue(result.output.contains("note-guid-1"))
    }

    @Test
    fun `search honors limit flag`() {
        run("search", "x", "--limit", "5")
        assertEquals(5, fake.lastLimit)
    }

    @Test
    fun `search maps auth error to chinese message`() {
        fake.searchError = EDAMUserException().apply { errorCode = EDAMErrorCode.INVALID_AUTH }
        val result = run("search", "x")
        assertFalse(result.success)
        assertTrue(result.error?.contains("认证失败") == true)
        assertTrue(result.error?.contains("7 天") == true)
    }

    // ── 读 ─────────────────────────────────────────────────────────────

    @Test
    fun `get returns plain text content`() {
        val result = run("get", "note-guid-1")
        assertTrue(result.success)
        assertTrue(result.output.contains("第一段"))
        assertFalse(result.output.contains("<en-note>"))
    }

    @Test
    fun `get downloads attachments to out dir`() {
        val outDir = File(tmpBase, "out").absolutePath
        val result = run("get", "note-guid-2", "--out", outDir)
        assertTrue(result.success)
        val saved = File(outDir, "note-guid-2/附件.txt")
        assertTrue(saved.isFile)
        assertEquals("hello-bytes", saved.readText())
        assertTrue(result.output.contains("附件.txt"))
    }

    @Test
    fun `get invalid guid maps not found`() {
        fake.getError = com.evernote.edam.error.EDAMNotFoundException().apply {
            identifier = "note-guid-404"
        }
        val result = run("get", "note-guid-404")
        assertFalse(result.success)
        assertTrue(result.error?.contains("不存在") == true)
    }

    // ── 写 ─────────────────────────────────────────────────────────────

    @Test
    fun `create passes title content and returns guid`() {
        val result = run("create", "新笔记", "正文内容", "--notebook", "工作")
        assertTrue(result.success)
        assertEquals("新笔记", fake.lastCreated?.title)
        assertTrue(fake.lastCreated?.content?.startsWith("<en-note>") == true)
        assertEquals("nb-work", fake.lastCreated?.notebookGuid)
        assertTrue(result.output.contains("new-note-guid"))
    }

    @Test
    fun `create requires title and content`() {
        val result = run("create", "只有标题")
        assertFalse(result.success)
        assertTrue(result.error?.contains("用法") == true)
    }

    @Test
    fun `create unknown notebook fails`() {
        val result = run("create", "标题", "内容", "--notebook", "不存在的笔记本")
        assertFalse(result.success)
        assertTrue(result.error?.contains("找不到笔记本") == true)
    }

    @Test
    fun `update modifies title and content`() {
        val result = run("update", "note-guid-1", "--title", "改后标题", "--content", "改后内容")
        assertTrue(result.success)
        assertEquals("改后标题", fake.lastUpdated?.title)
        assertTrue(fake.lastUpdated?.content?.contains("改后内容") == true)
    }

    @Test
    fun `update without changes fails`() {
        val result = run("update", "note-guid-1")
        assertFalse(result.success)
        assertTrue(result.error?.contains("未提供变更参数") == true)
    }

    @Test
    fun `delete calls deleteNote`() {
        val result = run("delete", "note-guid-1")
        assertTrue(result.success)
        assertEquals("note-guid-1", fake.lastDeleted)
        assertTrue(result.output.contains("废纸篓"))
    }

    // ── 列表 ────────────────────────────────────────────────────────────

    @Test
    fun `notebooks lists names and guids`() {
        val result = run("notebooks")
        assertTrue(result.success)
        assertTrue(result.output.contains("工作"))
        assertTrue(result.output.contains("nb-work"))
    }

    @Test
    fun `tags lists names and guids`() {
        val result = run("tags")
        assertTrue(result.success)
        assertTrue(result.output.contains("重要"))
        assertTrue(result.output.contains("tag-1"))
    }

    // ── Fake ────────────────────────────────────────────────────────────

    private class FakeNoteStoreGateway : NoteStoreGateway {
        var lastLimit: Int = 0
        var lastCreated: Note? = null
        var lastUpdated: Note? = null
        var lastDeleted: String? = null
        var searchError: Exception? = null
        var getError: Exception? = null

        override suspend fun listNotebooks(): List<Notebook> = listOf(
            Notebook().apply { guid = "nb-work"; name = "工作" },
            Notebook().apply { guid = "nb-life"; name = "生活" }
        )

        override suspend fun listTags(): List<Tag> = listOf(
            Tag().apply { guid = "tag-1"; name = "重要" }
        )

        override suspend fun findNotesMetadata(
            filter: NoteFilter,
            offset: Int,
            maxNotes: Int,
            spec: NotesMetadataResultSpec
        ): NotesMetadataList {
            lastLimit = maxNotes
            searchError?.let { throw it }
            return NotesMetadataList().apply {
                totalNotes = 1
                notes = listOf(
                    NoteMetadata().apply {
                        guid = "note-guid-1"
                        title = "笔记一"
                        updated = 1_700_000_000_000L
                        notebookGuid = "nb-work"
                    }
                )
            }
        }

        override suspend fun getNote(guid: String, withContent: Boolean, withResourcesData: Boolean): Note {
            getError?.let { throw it }
            return if (guid == "note-guid-2") {
                Note().apply {
                    this.guid = guid
                    title = "带附件笔记"
                    content = "<en-note><div>正文</div></en-note>"
                    resources = listOf(
                        Resource().apply {
                            this.guid = "res-1"
                            data = Data().apply { body = "hello-bytes".toByteArray() }
                            attributes = ResourceAttributes().apply { fileName = "附件.txt" }
                        }
                    )
                }
            } else {
                Note().apply {
                    this.guid = guid
                    title = "笔记一"
                    content = "<en-note><div>第一段</div><div>第二段</div></en-note>"
                    updated = 1_700_000_000_000L
                }
            }
        }

        override suspend fun createNote(note: Note): Note {
            lastCreated = note
            return Note().apply {
                guid = "new-note-guid"
                title = note.title
            }
        }

        override suspend fun updateNote(note: Note): Note {
            lastUpdated = note
            return note
        }

        override suspend fun deleteNote(guid: String) {
            lastDeleted = guid
        }
    }
}
