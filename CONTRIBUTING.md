# Contributing to MengPaw Connectors

感谢你愿意帮助迭代 MengPaw 外置插件生态（8 个普通外置插件 + 5 个连接器）！本仓库 **MIT 许可、社区开放贡献**。

## 开发流程

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feat/amazing-feature`)
3. 提交改动 (`git commit -m 'feat: add amazing feature'`)
4. 推送到分支 (`git push origin feat/amazing-feature`)
5. 创建 Pull Request

**贡献许可**：提交 PR 即表示你同意你的贡献（代码/文档）按本仓库的 [MIT 许可](LICENSE) 授权（inbound = outbound）。无额外 CLA。

## 环境搭建

- JDK 17 + Android SDK 35（`local.properties` 写 `sdk.dir=...`）
- `./gradlew :plugin-connector-common:testDebugUnitTest` → `./gradlew assembleRelease`

## 代码规范

- 包命名：连接器 `com.mengpaw.plugin.connector.{模块}`；普通外置插件 `com.mengpaw.plugin.{模块}`
- 类大驼峰，函数小驼峰
- 注释中文
- **禁止 `!!` 强制解包** — 用 `?.let {}` 或 `?: return` 替代
- **所有文件 IO 必须 try/catch**
- SPDX 版权头：新建 `.kt` / `.kts` 文件首两行（MIT 许可）
  ```
  // SPDX-FileCopyrightText: 2026 深圳哇蓝文化科技有限公司 (ShenZhen wowblue culture and technology CO.,LTD.)
  // SPDX-License-Identifier: MIT
  ```
- 每个文件不超过 400 行
- 新增逻辑尽量带单元测试（连接器共享逻辑放 `plugin-connector-common`）

## 行为准则

- 保持与内核 SPI 的兼容：`kernelVersion` 对应主仓库 [MengPaw](https://github.com/WowBlueStudio/MengPaw) 的 git tag
- 外置插件只依赖内核构件（JitPack），不复制内核源码
- 连接器仅做协议互操作，不包含被对接方的专有代码

## 发布

版本号格式 `plugins-vX.Y.Z`，tag 即发布（dex JAR 随 GitHub/Gitee Releases 双源分发，由维护者执行；
本地 `scripts/package-plugins.ps1` 打包 13 个外置插件）。
