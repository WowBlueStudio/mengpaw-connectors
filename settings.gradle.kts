// SPDX-FileCopyrightText: 2026 深圳哇蓝文化科技有限公司 (ShenZhen wowblue culture and technology CO.,LTD.)
// SPDX-License-Identifier: MIT

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack — 解析 MengPaw 内核构件 (com.github.WowBlueStudio.MengPaw:mengpaw-kernel:<tag>)
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "mengpaw-connectors"

// ── 外置插件模块 (自 MengPaw 主仓库 plugins/ 迁入, MIT) ──
// 连接器
include(":plugin-connector-common")
include(":plugin-connector-openclaw")
include(":plugin-connector-claude-code")
include(":plugin-connector-reasonix")
include(":plugin-connector-trae")
include(":plugin-connector-qwenpaw")
include(":plugin-connector-yinxiang")
// 普通外置插件 (原主仓库 remote 插件)
include(":plugin-update")
include(":plugin-translate")
include(":plugin-error-report")
include(":plugin-render")
include(":plugin-comfy")
include(":plugin-browser-push")
include(":plugin-browser-search")
include(":plugin-browser-mcp")

project(":plugin-update").projectDir = File(rootDir, "plugin-update")
project(":plugin-translate").projectDir = File(rootDir, "plugin-translate")
project(":plugin-error-report").projectDir = File(rootDir, "plugin-error-report")
project(":plugin-render").projectDir = File(rootDir, "plugin-render")
project(":plugin-comfy").projectDir = File(rootDir, "plugin-comfy")
project(":plugin-browser-push").projectDir = File(rootDir, "plugin-browser-push")
project(":plugin-browser-search").projectDir = File(rootDir, "plugin-browser-search")
project(":plugin-browser-mcp").projectDir = File(rootDir, "plugin-browser-mcp")
