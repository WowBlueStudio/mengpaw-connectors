// SPDX-FileCopyrightText: 2026 深圳哇蓝文化科技有限公司 (ShenZhen wowblue culture and technology CO.,LTD.)
// SPDX-License-Identifier: MIT
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mengpaw.plugin.connector.yinxiang"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.github.WowBlueStudio.MengPaw:mengpaw-kernel:${rootProject.extra["kernelVersion"]}")
    implementation(project(":plugin-connector-common"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // EDAM 核心 (自带 thrift 运行时, 原生内置 YINXIANG 中国版端点, 网络走标准库)
    implementation("com.evernote:evernote-api:1.25.1")
    // ENML→纯文本 解析 (纯 Java, 可 fat 打包)
    implementation("org.jsoup:jsoup:1.17.2")

    // JVM 单测
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20240303")
}
