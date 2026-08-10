// SPDX-FileCopyrightText: 2026 深圳哇蓝文化科技有限公司 (ShenZhen wowblue culture and technology CO.,LTD.)
// SPDX-License-Identifier: MIT

plugins {
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}

// ── 内核构件版本 (JitPack) ─────────────────────────────
// 对应 MengPaw 主仓库 git tag (com.github.WowBlueStudio.MengPaw:mengpaw-kernel:<tag>)。
// FrameworkAdapter SPI 随内核演进 — 升级内核时同步修改此处, 并验证全部模块编译通过。
extra["kernelVersion"] = "v0.35.5"
