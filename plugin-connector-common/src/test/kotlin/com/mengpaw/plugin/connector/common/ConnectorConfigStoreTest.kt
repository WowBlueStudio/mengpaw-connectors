// SPDX-FileCopyrightText: 2026 深圳哇蓝文化科技有限公司 (ShenZhen wowblue culture and technology CO.,LTD.)
// SPDX-License-Identifier: MIT

package com.mengpaw.plugin.connector.common

import com.mengpaw.kernel.DataPaths
import com.mengpaw.kernel.cli.ExecutionContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * ConnectorConfigStore 单元测试 — 原子读写回环 / 损坏回退 / 脱敏 / 覆盖写。
 * 注意: DataPaths 是全局 object, 每次测试用独立临时目录避免串扰。
 */
class ConnectorConfigStoreTest {

    private fun ensureDataPaths() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "mengpaw-connector-cfg-${System.currentTimeMillis()}")
        tmp.mkdirs()
        DataPaths.initialize(tmp.absolutePath)
    }

    @Test
    fun `write then read roundtrip`() {
        ensureDataPaths()
        val cfg = ConnectorConfigStore.ConnectorConfig(
            user = "dev", password = "secret123", sshPort = 2222, cliPath = "C:\\tools\\claude.cmd"
        )
        ConnectorConfigStore.write("connector-test-plugin", cfg)
        val loaded = ConnectorConfigStore.read("connector-test-plugin")
        assertEquals("dev", loaded.user)
        assertEquals("secret123", loaded.password)
        assertEquals(2222, loaded.sshPort)
        assertEquals("C:\\tools\\claude.cmd", loaded.cliPath)
        assertEquals("rest", loaded.channel)
    }

    @Test
    fun `absent file returns empty config`() {
        ensureDataPaths()
        assertEquals("", ConnectorConfigStore.read("connector-none-plugin").user)
    }

    @Test
    fun `corrupted file falls back to empty config`() {
        ensureDataPaths()
        val f = File(DataPaths.CONFIG, "connector-test-plugin-connector.json")
        f.parentFile?.mkdirs()
        f.writeText("{invalid json!!")
        val loaded = ConnectorConfigStore.read("connector-test-plugin")
        assertEquals("损坏文件回退空配置", "", loaded.user)
    }

    @Test
    fun `atomic write replaces existing value`() {
        ensureDataPaths()
        ConnectorConfigStore.write("connector-test-plugin", ConnectorConfigStore.ConnectorConfig(user = "a"))
        ConnectorConfigStore.write("connector-test-plugin", ConnectorConfigStore.ConnectorConfig(user = "b", password = "p2"))
        val loaded = ConnectorConfigStore.read("connector-test-plugin")
        assertEquals("覆盖写取新值", "b", loaded.user)
        assertEquals("p2", loaded.password)
    }

    @Test
    fun `describe masks password and passphrase`() {
        ensureDataPaths()
        val cfg = ConnectorConfigStore.ConnectorConfig(user = "dev", password = "topsecret")
        val desc = ConnectorConfigStore.describe("connector-test-plugin", cfg)
        assertFalse("描述不得含明文密码", desc.contains("topsecret"))
        assertTrue("密码以星号呈现", desc.contains("****"))

        val keyCfg = ConnectorConfigStore.ConnectorConfig(user = "dev", keyPath = "/home/dev/id_rsa", keyPassphrase = "passphrase-xyz")
        val keyDesc = ConnectorConfigStore.describe("connector-test-plugin", keyCfg)
        assertFalse("描述不得含密钥口令", keyDesc.contains("passphrase-xyz"))
    }

    @Test
    fun `token roundtrip and describe masks token`() {
        ensureDataPaths()
        val cfg = ConnectorConfigStore.ConnectorConfig(user = "dev", token = "tok-abc-123")
        ConnectorConfigStore.write("connector-test-plugin", cfg)
        assertEquals("token 回环", "tok-abc-123", ConnectorConfigStore.read("connector-test-plugin").token)
        val desc = ConnectorConfigStore.describe("connector-test-plugin", cfg)
        assertFalse("描述不得含明文 token", desc.contains("tok-abc-123"))
        assertTrue("token 以星号呈现", desc.contains("****"))
    }

    @Test
    fun `default channel is rest for qwenpaw`() {
        ensureDataPaths()
        val loaded = ConnectorConfigStore.read("connector-qwenpaw-plugin")
        assertEquals("默认通道应为 rest (QwenPaw REST 直连)", "rest", loaded.channel)
    }

    @Test
    fun `config handler accepts ssh-acp channel`() {
        ensureDataPaths()
        val result = runBlocking {
            ConnectorCommand.configHandler("connector-qwenpaw-plugin")(
                listOf("--yes", "--channel", "ssh-acp", "--user", "dev"),
                ExecutionContext(sessionId = "test")
            )
        }
        assertTrue("ssh-acp 应配置成功: ${result.output}", result.success)
        assertEquals("ssh-acp", ConnectorConfigStore.read("connector-qwenpaw-plugin").channel)
    }

    @Test
    fun `config handler maps legacy ssh channel to ssh-acp`() {
        ensureDataPaths()
        runBlocking {
            ConnectorCommand.configHandler("connector-qwenpaw-plugin")(
                listOf("--yes", "--channel", "ssh"), ExecutionContext(sessionId = "test")
            )
        }
        assertEquals("旧默认 ssh 应映射为 ssh-acp", "ssh-acp", ConnectorConfigStore.read("connector-qwenpaw-plugin").channel)
    }

    @Test
    fun `cliOf strips surrounding quotes`() {
        ensureDataPaths()
        val quoted = ConnectorConfigStore.ConnectorConfig(cliPath = "\"C:\\tools\\claude.cmd\"")
        assertEquals("C:\\tools\\claude.cmd", ConnectorConfigStore.cliOf(quoted, "claude"))

        val unquoted = ConnectorConfigStore.ConnectorConfig(cliPath = "C:\\Program Files\\claude.cmd")
        assertEquals("C:\\Program Files\\claude.cmd", ConnectorConfigStore.cliOf(unquoted, "claude"))

        assertEquals("默认名回退", "claude", ConnectorConfigStore.cliOf(ConnectorConfigStore.ConnectorConfig(), "claude"))
    }
}
