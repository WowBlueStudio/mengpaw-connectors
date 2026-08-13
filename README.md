# MengPaw 外置插件仓库 🐾🔌

MengPaw（檬爪）[全部外置插件](https://github.com/WowBlueStudio/MengPaw)集合 — 源码不入 Shell APK，经插件市场（plugins.json，GitHub/Gitee 双源）以 `remote` 插件分发。包括 8 个普通外置插件（更新/翻译/上报/生图/ComfyUI/推送/转档/MCP）与 5 个连接器（通过内核 `FrameworkAdapter` SPI 把第三方 Agent 框架接入 MengPaw）。

> **MIT 许可 · 社区开放贡献** — 欢迎 PR，欢迎迭代。外置插件与 MengPaw 框架本体（AGPL 双许可）相互独立，仅依赖内核构件编译。

## 模块

### 普通外置插件（8）

| 模块 | 命名空间 | 用途 | 上游许可 |
|------|---------|------|---------|
| `plugin-update` | `update` | WiFi 自动更新（GitHub→Gitee→ghproxy 三级回退） | ktor (Apache-2.0) |
| `plugin-translate` | `translate` | Google 翻译 + 语种自动检测 | ktor (Apache-2.0) |
| `plugin-error-report` | `error` | 官方错误收集与上报 | ktor (Apache-2.0) |
| `plugin-render` | `render` | 多后端 API 生图（GPT Image / Replicate / Stability） | ktor (Apache-2.0) |
| `plugin-comfy` | `comfy` | ComfyUI 工作流搭建与执行 | ktor (Apache-2.0) |
| `plugin-browser-push` | `browser.push` | Agent 跨设备推送网页（ACP） | — |
| `plugin-browser-search` | `search` | 网页转 Markdown 管道 | ktor (Apache-2.0) |
| `plugin-browser-mcp` | `browser` | MP 浏览器能力暴露为 MCP 工具 | coroutines (Apache-2.0) |

### 连接器（6 + 共享库）

| 模块 | 命名空间 | 通道 | 对接框架 | 上游许可 |
|------|---------|------|---------|---------|
| `plugin-connector-common` | —（共享库） | SSH / 交互式通道 / 配置存储 | 供各连接器复用 | jsch (MIT) |
| `plugin-connector-openclaw` | `openclaw` | WebSocket :18789 | OpenClaw | — |
| `plugin-connector-claude-code` | `claude-code` | SSH → `claude -p` | Anthropic Claude Code | 闭源商业 CLI（仅互操作调用） |
| `plugin-connector-reasonix` | `reasonix` | SSH → `reasonix run` | esengine/DeepSeek-Reasonix | MIT |
| `plugin-connector-trae` | `trae-ide` | SSH → `trae-cli run` | bytedance/trae-agent | MIT |
| `plugin-connector-qwenpaw` | `qwenpaw` | REST :8088 (默认) + SSH ACP | agentscope-ai/QwenPaw | Apache-2.0 |
| `plugin-connector-yinxiang` | `connector-yinxiang` | EDAM 云 API (app.yinxiang.com) | Evernote 官方 Java SDK | Apache / Evernote SDK License |

连接器实现内核 `spi.FrameworkAdapter`（`frameworkName` / `connect` / `callTool` / `isOnline`），安装进 MengPaw 后注册到连接器注册表，`framework.adapters` 可见、`framework.connect <peer>` 即可对接。完整接入指南见主仓库 [PROTOCOL.md](https://github.com/WowBlueStudio/MengPaw/blob/master/docs/PROTOCOL.md)。

> 例外：`plugin-connector-yinxiang` 为 EDAM 云 API 直连，不实现 FrameworkAdapter，命令直接以 `connector-yinxiang.*` 调用（token 经 `connector-yinxiang.config --token-file <路径> --yes` 配置）。

## 构建

要求：JDK 17 + Android SDK 35（`local.properties` 配置 `sdk.dir`）。

```bash
./gradlew assembleRelease    # 全部 15 个 AAR
./gradlew :plugin-connector-common:testDebugUnitTest   # 共享库单测
```

AAR 输出：`<模块>/build/outputs/aar/*-release.aar`

**内核依赖**：各模块通过 JitPack 依赖 MengPaw 内核构件
（`com.github.WowBlueStudio.MengPaw:mengpaw-kernel:<tag>`）。版本由根 `build.gradle.kts` 的 `kernelVersion` 统一控制，随主仓库 tag 演进——**升级内核时修改此处并验证全部模块编译通过**。

### 宿主可加载产物（Android 插件市场用）

外置插件以 `remote` 插件分发（经 plugins.json 下载安装）。Android 插件加载器
（`PluginRuntimeLoader`）只接受 **含 `classes.dex` 的 JAR**，且通过
`META-INF/plugin-class` 清单或固定候选类名定位主类——标准 AAR（内含 `classes.jar`
字节码）无法被 DexClassLoader 加载。

发布前用打包脚本生成宿主可加载的 fat dex JAR：

```bash
powershell -ExecutionPolicy Bypass -File scripts/package-plugins.ps1
```

产物输出 `releases/plugins/*-release.jar`（普通插件）与 `releases/plugins/*-plugin.jar`（连接器），
配套 `scripts/plugin-class.txt` 声明每个插件的主类。上传这些 JAR 到 `plugins-v*` tag，
并把 plugins.json 的 downloadUrl 指向 `.jar` 产物即可在真机安装激活。

> 依赖说明：fat 打包把 jsch/okhttp/okio 一并 dex 化（宿主 APK 未内置 jsch），
> kotlin-stdlib/ktor/serialization/coroutines 依赖宿主（APK 已含）不重复打包。

> 注意：连接器**不得复制内核源码**，只依赖构件；编译期依赖 AGPL 内核仅为实现 SPI 接口（独立作品，MIT 许可不受影响）。

## 许可证

[MIT](LICENSE) — Copyright (c) 2026 深圳哇蓝文化科技有限公司 (ShenZhen wowblue culture and technology CO.,LTD.)

提交 PR 即表示你同意你的贡献按本仓库 MIT 许可授权（inbound = outbound，无需 CLA）。

## 贡献

见 [CONTRIBUTING.md](CONTRIBUTING.md)。Bug 报告与功能请求走 [GitHub Issues](https://github.com/WowBlueStudio/mengpaw-connectors/issues)。

## 联系

商用咨询 / 其他：1138018324@qq.com
