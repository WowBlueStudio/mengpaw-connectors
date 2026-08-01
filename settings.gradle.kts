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

// ── 连接器模块 (与 MengPaw 主仓库 plugins/ 目录同源) ──
include(":plugin-connector-common")
include(":plugin-connector-openclaw")
include(":plugin-connector-claude-code")
include(":plugin-connector-reasonix")
include(":plugin-connector-trae")
include(":plugin-connector-qwenpaw")
