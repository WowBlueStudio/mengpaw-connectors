# MengPaw Connectors 🐾🔌

MengPaw（檬爪）[外置连接器插件](https://github.com/WowBlueStudio/MengPaw)集合 — 通过内核 `FrameworkAdapter` SPI 把第三方 Agent 框架接入 MengPaw。

> **MIT 许可 · 社区开放贡献** — 欢迎 PR，欢迎迭代。连接器与 MengPaw 框架本体（AGPL 双许可）相互独立，仅依赖内核构件编译。

## 模块

| 模块 | 命名空间 | 通道 | 对接框架 | 上游许可 |
|------|---------|------|---------|---------|
| `plugin-connector-common` | —（共享库） | SSH / 交互式通道 / 配置存储 | 供各连接器复用 | jsch (MIT) |
| `plugin-connector-openclaw` | `openclaw` | WebSocket :18789 | OpenClaw | — |
| `plugin-connector-claude-code` | `claude-code` | SSH → `claude -p` | Anthropic Claude Code | 闭源商业 CLI（仅互操作调用） |
| `plugin-connector-reasonix` | `reasonix` | SSH → `reasonix run` | esengine/DeepSeek-Reasonix | MIT |
| `plugin-connector-trae` | `trae-ide` | SSH → `trae-cli run` | bytedance/trae-agent | MIT |
| `plugin-connector-qwenpaw` | `qwenpaw` | REST :8088 + SSH ACP | agentscope-ai/QwenPaw | Apache-2.0 |

连接器实现内核 `spi.FrameworkAdapter`（`frameworkName` / `connect` / `callTool` / `isOnline`），安装进 MengPaw 后注册到连接器注册表，`framework.adapters` 可见、`framework.connect <peer>` 即可对接。完整接入指南见主仓库 [PROTOCOL.md](https://github.com/WowBlueStudio/MengPaw/blob/master/docs/PROTOCOL.md)。

## 构建

要求：JDK 17 + Android SDK 35（`local.properties` 配置 `sdk.dir`）。

```bash
./gradlew assembleRelease    # 全部 6 个 AAR
./gradlew :plugin-connector-common:testDebugUnitTest   # 共享库单测
```

AAR 输出：`<模块>/build/outputs/aar/*-release.aar`

**内核依赖**：各模块通过 JitPack 依赖 MengPaw 内核构件
（`com.github.WowBlueStudio.MengPaw:mengpaw-kernel:<tag>`）。版本由根 `build.gradle.kts` 的 `kernelVersion` 统一控制，随主仓库 tag 演进——**升级内核时修改此处并验证全部模块编译通过**。

> 注意：连接器**不得复制内核源码**，只依赖构件；编译期依赖 AGPL 内核仅为实现 SPI 接口（独立作品，MIT 许可不受影响）。

## 许可证

[MIT](LICENSE) — Copyright (c) 2026 深圳哇蓝文化科技有限公司 (ShenZhen wowblue culture and technology CO.,LTD.)

提交 PR 即表示你同意你的贡献按本仓库 MIT 许可授权（inbound = outbound，无需 CLA）。

## 贡献

见 [CONTRIBUTING.md](CONTRIBUTING.md)。Bug 报告与功能请求走 [GitHub Issues](https://github.com/WowBlueStudio/mengpaw-connectors/issues)。

## 联系

商用咨询 / 其他：1138018324@qq.com
