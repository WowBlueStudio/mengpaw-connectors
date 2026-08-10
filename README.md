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
| `plugin-connector-qwenpaw` | `qwenpaw` | REST :8088 (默认) + SSH ACP | agentscope-ai/QwenPaw | Apache-2.0 |

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

### 宿主可加载产物（Android 插件市场用）

连接器以 `remote` 插件分发（经 plugins.json 下载安装）。Android 插件加载器
（`PluginRuntimeLoader`）只接受 **含 `classes.dex` 的 JAR**，且通过
`META-INF/plugin-class` 清单或固定候选类名定位主类——标准 AAR（内含 `classes.jar`
字节码）无法被 DexClassLoader 加载。

发布前用打包脚本生成宿主可加载的 fat dex JAR：

```bash
powershell -ExecutionPolicy Bypass -File scripts/package-connectors.ps1
```

产物输出 `releases/connectors/*-plugin.jar`（内含 `classes.dex` + `META-INF/plugin-class`），
配套 `scripts/plugin-class.txt` 声明每个插件的主类。上传这些 JAR 到 `plugins-v*` tag，
并把 plugins.json 的 downloadUrl 指向 `.jar` 产物即可在真机安装激活。

> 依赖说明：fat 打包把 jsch/okhttp/okio 一并 dex 化（宿主 APK 未内置 jsch），
> kotlin-stdlib/coroutines 依赖宿主（APK 已含）不重复打包。

> 注意：连接器**不得复制内核源码**，只依赖构件；编译期依赖 AGPL 内核仅为实现 SPI 接口（独立作品，MIT 许可不受影响）。

## 许可证

[MIT](LICENSE) — Copyright (c) 2026 深圳哇蓝文化科技有限公司 (ShenZhen wowblue culture and technology CO.,LTD.)

提交 PR 即表示你同意你的贡献按本仓库 MIT 许可授权（inbound = outbound，无需 CLA）。

## 贡献

见 [CONTRIBUTING.md](CONTRIBUTING.md)。Bug 报告与功能请求走 [GitHub Issues](https://github.com/WowBlueStudio/mengpaw-connectors/issues)。

## 联系

商用咨询 / 其他：1138018324@qq.com
