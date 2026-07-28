# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2026-07-28] - GRIS 风格会话上下文标题
- [修改] 按聚焦、克制与直觉交互原则重设计 `ChatScreen.ConversationContextHeader`，收敛为单层身份画幅、状态化模式图标、GRIS 水彩地平线和独立 Ghost 历史入口。
- [修改] 移除渐变徽章、状态点、箭头底座和嵌套指令卡片；展开区域保留柔和动画、系统指令选择、滚动、复制及会话切换自动收起行为。
- [文档] 新增 `docs/designs/conversation-context-header.md`，记录乔布斯式产品原则、视觉减法、响应式策略、主题 token 与交互边界。

## [2026-07-28] - 音频消息 UI 同步
- [修改] 更新 `ChatScreen` 中 `ChatMessageContent.Audio` 的消息卡片，改为单键播放/暂停、渐变进度条、标题/音频元数据显示，并保留复制源 JSON、保存 WAV 和播放错误提示。
- [新增] 为新版音频消息 UI 补充 `bgm_audio_default_title`、`bgm_audio_metadata`、`bgm_copy_spec`、`bgm_save_wav` 与时长格式化多语言资源。
- [文档] 更新 `docs/specs/Gemma4 8bit BGM JSON + MML Tracks.md`，记录音频消息 UI 行为与未改动的播放/持久化边界。

## [2026-07-27] - 8-bit BGM 生成与播放
- [新增] 新增 `CHIPTUNE_BGM_MML` 专用聊天模式、JSON + MML parser、轨道校验、确定性 pulse/triangle/noise 合成器和 8-bit unsigned PCM WAV writer。
- [修改] 将 `LibraryScreen` 的 Creative Nebula 入口替换为可点击的 `EightBitBgmCard`，生成结果以 `ChatMessageContent.Audio` 持久化并保留源乐谱 JSON。
- [新增] 接入 `composemediaplayer-audio:0.11.1`，在 `ChatScreen` 音频气泡提供播放、暂停、停止、seek、保存 WAV、复制源 JSON 和错误反馈。
- [测试] 新增 `ChiptuneBgmMmlParserTest`，覆盖协议校验、MML repeat/noise 解析、tempo 拒绝、WAV header、时长与确定性渲染。
- [文档] 更新 `docs/specs/Gemma4 8bit BGM JSON + MML Tracks.md` 与 `docs/agents/data-model.md`，记录实现结构、持久化边界和验证状态。

## [2026-07-26] - LiteRT-LM 模型 Context 上限读取
- [新增] 在主仓库 commonMain 增加 `LiteRtLmModelMetadata`，通过随机读取 `.litertlm` header、`LlmMetadataProto` section 与 protobuf 字段 5 获取模型 `max_num_tokens`，不修改 `cpp/lite-rt-lm` submodule。
- [修改] `ChatViewModel` 在创建或重建 `LmEngine` 前使用模型声明的 Context 上限更新 `lmMaxNumTokens`，设置页 token 调整上限同步改为模型值，读取失败时保留 8192 回退。
- [测试] 新增 `LiteRtLmModelMetadataTest`，覆盖 Context 上限读取、字段缺失与非法容器 magic。
- [文档] 新增 `docs/designs/litertlm-model-context-limit.md`，记录 submodule 只读边界、容器解析流程与回退策略。

## [2026-07-25] - 可扩展聊天消息与 SVG 图片消息
- [新增] 引入 `ChatMessageContent` 类型化内容模型，支持文本、位图、SVG 与未知内容降级；`ChatBubble` 改为按有序内容列表分发独立渲染器。
- [新增] 基于 `coil-svg` 渲染经安全校验的 SVG 消息，并提供源码复制、跨端 `.svg` 保存、渲染失败提示和 1 MiB/外部资源/可执行内容限制。
- [修改] Room 数据库升级到 v2，新增 `chat_message_contents`、会话 `mode` 与 `system_instruction` 快照，并提供 v1 文本及旧 SVG JSON 的迁移。
- [新增] Chat 页增加遵循 Ethereal Minimalism 主题的当前聊天对象提示条，可显示上下文应用状态、展开查看并复制完整 system instruction。
- [修复] 取消生成时同步删除 Room 助手占位记录，并阻止已取消协程覆盖最后一条用户消息。
- [测试] 新增 `SvgMessageParserTest` 与 `ChatHistoryMigrationTest`，覆盖合法 SVG、Markdown 包裹、畸形 XML、脚本/外链、超限载荷，以及 v1 文本/旧 SVG 数据迁移。
- [文档] 新增 `docs/designs/extensible-chat-messages.md`，并更新聊天持久化、SVG 会话和数据模型规范。

## [2026-07-23] - SVG JSON 内容字段复制渲染修复
- [修复] 收紧 `ChatViewModel` 中 SVG 图像生成专用 `systemInstruction`，要求 `svg` 字段使用单引号属性、单行 markup、严格 XML 标签配对，并避免 `rgba(...)` 兼容性问题，修复从 JSON 中复制 SVG 内容后无法正常渲染的风险。
- [文档] 更新 `docs/specs/svg-image-library-card.md`，记录 `svg` 字段可复制、可渲染的 JSON 输出约束。

## [2026-07-22] - SVG 图像生成资源库卡片
- [新增] 在 `ChatViewModel` 中新增 SVG 图像生成专用 `systemInstruction` 和 `startSvgImageConversation()`，点击入口会用专用 prompt 调用 `createConversation` 并开启 constrained decoding。
- [修改] 将 `LibraryScreen` 中原 Data Crystal 卡片替换为 `SvgImageCard`，新增 SVG 预览风格与点击跳转 Chat 的交互。
- [文档] 新增 `docs/specs/svg-image-library-card.md` 记录 SVG 专用会话行为、输出 JSON 结构和安全边界。

## [2026-07-22] - Chat streaming bottom scroll fix
- [Fixed] Updated `ChatMessagesList` in `composeApp/src/commonMain/kotlin/org/onion/agro/ui/screen/ChatScreen.kt` so streaming LLM responses scroll to the bottom of the growing assistant message after layout remeasurement instead of stopping at the top of the last item.
- [Changed] Preserved manual upward scrolling during generation, restored stick-to-bottom behavior when new messages arrive or the user taps the scroll button, and aligned `ScrollToBottomButton` modifier ordering with the documented circular rendering rule.

## [2026-07-20] - iOS IPA Gradle property injection fix
- [Fixed] Updated `composeApp/build.gradle.kts` so `BuildIpaTask` receives `app.name` through an `@Input Property<String>` and no longer reads `project.property("app.name")` during task execution.
- [Changed] Reused a single Gradle Provider-derived release archive path for `buildReleaseArchive` and `buildReleaseIpa`, keeping the `.xcarchive` and `.ipa` naming logic aligned.
- [Docs] Updated `docs/specs/ios-litertlm-platform.md` to record the configuration-cache-safe property boundary for iOS IPA packaging.

## [2026-07-19] - iOS LiteRT LM native static link fix
- [修复] iOS LiteRT LM archive 任务改为将 `cpp/lite-rt-lm` submodule rsync 到 `composeApp/build/litertlm-ios-workspace`，再应用父仓库 patch `cpp/patches/lite-rt-lm-ios-native-link.patch` 后构建，避免直接修改 submodule 导致后续同步失效。
- [修复] 在临时 Bazel workspace 中将 iOS LiteRT LM 默认目标切换为 `//c:engine_fully_linked`，由 `apple_static_library` 生成包含传递 C/C++/Rust 依赖的静态 archive，修复 Kotlin/Native 链接 `absl::log_internal::LogMessage::CopyToEncodedBuffer` 等符号缺失。
- [修复] 为 iOS Bazel 构建启用 Apple toolchain resolution，并显式映射 Rust iOS triples 到 `apple_support` 的 `arm64` device/simulator constraints，避免 `//runtime/components/tool_use/rust:parsers` 在 Apple split transition 下找不到 Rust toolchain。
- [修复] iOS Bazel 构建启用 `LITERT_LM_FST_CONSTRAINTS_DISABLED=1`，移除静态 archive 对预编译 `GemmaModelConstraintProvider` dylib 符号的依赖，修复 Kotlin/Native framework 链接 `LiteRtLmGemmaModelConstraintProvider_*` 缺失。
- [文档] 更新 `docs/specs/ios-litertlm-platform.md`，记录 fully-linked archive、iOS Rust/Apple 平台约束和 FST constraints 禁用边界。

## [2026-07-18] - Bazelisk executable resolution for native builds
- [修复] 更新 `composeApp/build.gradle.kts`，让 LiteRT LM Bazel 原生构建任务通过 `-Pbazelisk.path`、`BAZELISK`、`BAZELISK_PATH`、PATH 与 Homebrew 常见路径解析 Bazelisk，修复 iOS simulator archive 任务启动 `command 'bazelisk'` 失败的问题。
- [修复] 移除 iOS LiteRT LM archive 任务对仓库根 `.bazelrc.user` 的显式传入，避免本机 Windows 输出根 `G:/_b` 污染 macOS/iOS Bazel 构建。
- [修复] iOS LiteRT LM archive 任务通过 Gradle `providers.exec` 自动传入当前 Xcode 的 macOS/iOS SDK 版本，避免 Bazel Apple support 回退到不可用的 `macosx10.11`。
- [修复] 为 `cpp/lite-rt-lm/BUILD.miniaudio` 增加 `miniaudio_decoder` 目标，并让 iOS audio preprocessor 依赖 decoder-only C 目标，避免 iOS Simulator 编译 `miniaudio_objc` 时错误拉取 macOS `AVFoundation/CoreImage` 头。
- [修复] 将 `validateIosLiteRtLmNativeLibs` 改为配置缓存安全的自定义任务，避免 `doLast` 闭包捕获 `Project/rootProject`。
- [文档] 更新 `docs/specs/ios-litertlm-platform.md`，记录 iOS Bazel archive 任务的 Bazelisk 查找顺序与本机 IDE/Gradle daemon PATH 边界。

## [2026-07-15] - iOS cinterop header include path fix
- [Fixed] Updated `composeApp/build.gradle.kts` so the LiteRT LM Kotlin/Native cinterop includes `cpp/lite-rt-lm/c` directly, allowing `litertlm.def` to resolve `engine.h` during GitHub Actions/Xcode archive builds.
- [Docs] Updated `docs/specs/ios-litertlm-platform.md` to record the required cinterop header directory boundary.

## [2026-07-15] - Mobile iOS Bazel CI setup
- [Fixed] Updated `.github/workflows/build.yml` so the mobile `ios` matrix entry installs Bazelisk, restores the Bazel disk cache, and rewrites the CI `.bazelrc.user` before `buildReleaseIpa`, matching the LiteRT LM iOS Bazel task chain.
- [Docs] Updated `docs/specs/ios-litertlm-platform.md` and `docs/specs/bazel-windows-android-rc.md` to record the shared mobile Bazel setup and the macOS runner path boundary.

## [2026-07-15] - iOS simulator native build simplification
- [Changed] Removed `iosX64` from `build-logic/convention/src/main/kotlin/ext/KotlinMultiplatformExt.kt` and `shared/build.gradle.kts` so the Gradle target matrix matches the supported iOS platforms.
- [Changed] Simplified `composeApp/build.gradle.kts` by removing the iOS simulator x64 Bazel task and `lipo` merge task; `buildIosLiteRtLmNativeLibs` now builds only device arm64 and simulator arm64 LiteRT LM archives.
- [Docs] Updated `docs/specs/ios-litertlm-platform.md` to document the current iOS target matrix and the absence of Intel iOS Simulator support.

## [2026-07-14] - iOS LiteRT LM native 构建修复
- [修复] 调整 `composeApp/build.gradle.kts` 中 `BuildIosLiteRtLmNativeArchiveTask` 的 Bazel 启动命令，macOS iOS 构建不再显式加载仓库根 `.bazelrc.user`，避免 Windows `G:/_b` 输出根破坏 Bazel 内嵌 JDK 路径。
- [修复] iOS Bazel 构建显式传入当前 Xcode 的 `macosx` 与 `iphoneos` SDK 版本，避免 Bazel Apple support 回退到不可用的 `macosx10.11`。
- [修复] 新增 `cpp/lite-rt-lm/BUILD.miniaudio` 的 `miniaudio_decoder` 目标，并让 iOS 音频预处理依赖该 decoder-only 目标，避免 `miniaudio_objc` 或完整 Apple 设备后端拉取 AVFoundation/CoreImage 头导致 iOS 编译失败。
- [修复] 将 `validateIosLiteRtLmNativeLibs` 改为配置缓存安全的自定义任务，避免执行阶段闭包捕获 `Project/rootProject` 导致 configuration cache 存储失败。
- [修改] 将 iOS simulator 原生库产物收敛为当前 KMP 已注册的 `iosSimulatorArm64()` slice，移除未注册 `iosX64` slice 的强制 `dependsOn` 与 `lipo` 聚合链路。
- [文档] 更新 `docs/specs/ios-litertlm-platform.md`，记录 iOS native archive 产物、`.bazelrc.user` 边界与 simulator 架构约束。

## [2026-07-14] - Kotlin 工具链升级
- [修改] 将 `gradle/libs.versions.toml` 中 Kotlin 版本从 `2.2.0` 升级到 `2.3.20`，使 Kotlin/Native 能消费由 Kotlin `2.3.20` 编译的 Navigation3 iOS KLIB。
- [修改] 将 KSP 插件版本从 `2.2.0-2.0.2` 升级到 Maven Central 当前可用的 `2.3.10`，避免继续绑定旧 Kotlin 2.2 工具链。
- [修改] 将 Android Gradle Plugin 从 `8.9.1` 升级到 `8.10.0`，满足 KSP `2.3.10` 对 AGP 的最低版本要求。
- [修复] 移除 `build-logic/convention/build.gradle.kts` 中显式 apply 的 Kotlin JVM 插件，让 `kotlin-dsl` 使用 Gradle 内嵌 Kotlin 编译 convention plugins，避免 Kotlin `2.3.20` 与 Gradle Kotlin DSL 语言版本冲突。
- [修复] 为 `data-network/src/iosMain/kotlin/com/onion/network/http/PlatformFileWriter.ios.kt` 增加文件级 `ExperimentalForeignApi` opt-in，适配 Kotlin `2.3.20` 对 Kotlin/Native POSIX/cinterop API 的检查。
- [修复] 在 `composeApp/build.gradle.kts` 中显式连接 `iosMain` 到 `iosArm64Main`/`iosSimulatorArm64Main`，确保 Kotlin `2.3.20` 下 iOS actual 实现参与 Native 编译。
- [修复] 调整 `composeApp/src/iosMain/kotlin/com/google/ai/edge/litertlm/LiteRtLmJni.ios.kt` 的 cinterop forward declaration 与字符串参数调用方式，适配 Kotlin `2.3.20` 生成的 LiteRT LM C API 绑定。
- [修复] 删除 `iosArm64Main` 与 `iosSimulatorArm64Main` 下重复的 `MainViewController.kt`，保留 `iosMain` 共享入口，避免 iOS source set 接入后函数重载冲突。

## [2026-07-14] - iOS LiteRtLmJni platform boundary cleanup
- [Changed] Removed the legacy `sdloader.def` Kotlin/Native cinterop setup, stable-diffusion iOS linker options, and `buildIosNativeLibs` task from `composeApp/build.gradle.kts`.
- [Added] Added `composeApp/src/nativeInterop/cinterop/litertlm.def` and wired iOS targets to the LiteRT LM C API in `cpp/lite-rt-lm/c/engine.h`.
- [Added] Implemented `composeApp/src/iosMain/kotlin/com/google/ai/edge/litertlm/LiteRtLmJni.ios.kt` with C API engine/conversation lifecycle, synchronous send, streaming send, cancellation, and release handling.
- [Changed] iOS linking now expects `liblitertlm_c_api.a` or `liblitertlm_c_api.dylib` under `cpp/libs/ios-device` and `cpp/libs/ios-simulator`, with `validateIosLiteRtLmNativeLibs` failing early on macOS if artifacts are missing.
- [Docs] Added `docs/specs/ios-litertlm-platform.md` to record the iOS LiteRT LM bridge, native library contract, and unsupported common API fields.


## [2026-07-14] - Windows Bazel Rust 链接路径修复
- [修复] 更新 `.github/workflows/build.yml`，将 Windows CI 的 Bazel 输出基准目录从 `$RUNNER_TEMP/bazel-output` 改为 `startup --output_base=C:/b`，避免 `rules_rust` proc-macro 对象文件路径过长导致 MSVC `link.exe` 报 `LNK1181`。
- [文档] 更新 `docs/specs/bazel-windows-android-rc.md`，补充 Windows CI 必须使用短 Bazel 输出根的约束与故障原因。

## [2026-07-14] - CI NDK 版本与 Windows Bazel 环境隔离修复
- [修复] 将 Android NDK 版本收敛到 `gradle/libs.versions.toml` 的 `android-ndk=27.0.12077973`，并由 `build-logic/convention/src/main/kotlin/ext/AndroidExt.kt` 显式写入 `android.ndkVersion`，避免 AGP 默认值与 CI 安装版本漂移。
- [修复] 更新 `.github/workflows/build.yml`，Android release 构建安装 `27.0.12077973` 并写入 `local.properties`，修复 `ndk.dir` 与 `android.ndkVersion` 不一致导致的 `CXX1104`。
- [修复] 更新 `.github/workflows/build.yml`，Windows desktop 构建执行 Gradle 时清空 `ANDROID_NDK_HOME`/`ANDROID_NDK_ROOT`，避免 Bazel 注册 hosted runner 预置 NDK 后触发 `Cannot write outside of the repository directory`。
- [文档] 更新 `docs/specs/bazel-windows-android-rc.md`，补充 Windows NDK 环境隔离和 Android NDK 版本对齐约束。

## [2026-07-14] - 多平台 CI 原生构建前置条件修复
- [修复] 更新 `.github/workflows/build.yml`，为 Android release 构建安装 Android NDK 并写入 `local.properties`，修复 `buildAndroidNativeLib` 创建阶段 `NDK is not installed`。
- [修复] Windows Desktop 构建改为下载真实 `bazelisk.exe` 并加入 `PATH`，避免 Gradle `ExecOperations` 无法启动 npm `.cmd` shim 导致 `command 'bazelisk'` 失败。
- [修复] Linux Desktop 构建在 CI `.bazelrc.user` 中禁用 `xnn_enable_avxvnniint8`，规避 Ubuntu 22.04 clang 14 不支持 `-mavxvnniint8` 的 XNNPACK 编译失败。
- [修复] Desktop 与 Mobile 构建 checkout 启用 Git LFS，并对子模块执行 `git lfs pull`，避免 macOS 链接到 LFS pointer 导致 `ld: unknown file type`。
- [文档] 更新 `docs/specs/bazel-windows-android-rc.md`，补充 Android NDK、Windows Bazelisk、Linux XNNPACK 和子模块 LFS 的 CI 约束。

## [2026-07-14] - Linux Gradle Wrapper 权限修复
- [修复] 将 `gradlew` 的 Git 可执行位调整为 executable，并在 `.github/workflows/build.yml` 的 Unix runner 中增加 `chmod +x ./gradlew` 前置步骤，修复 Linux 包构建执行 `./gradlew` 时 `Permission denied` 的问题。
- [文档] 更新 `docs/specs/bazel-windows-android-rc.md`，补充 GitHub Actions 中 Gradle Wrapper 执行权限约束。

## [2026-07-14] - GitHub Actions 原生构建链路优化
- [修复] 优化 `.github/workflows/build.yml`，为 Desktop 三平台和 Android release 构建显式安装 Bazelisk，修复 CI 中 `buildNativeLibForWindows` 启动 `bazelisk` 失败的问题。
- [修改] 在 CI 中按平台生成临时 `.bazelrc.user`，为 Bazel 配置独立输出目录、磁盘缓存和 Windows Visual Studio C++ toolchain 自动发现，避免复用本机固定路径。
- [修改] 扩展 workflow 触发路径与 `pull_request`/`workflow_dispatch` 入口，覆盖 `cpp/`、KMP 子模块、`build-logic/`、iOS 工程和桌面图标资源变更。
- [文档] 更新 `docs/specs/bazel-windows-android-rc.md`，记录 GitHub Actions 与本机 Bazel RC 配置的边界。

## [2026-07-14] - Desktop App Icon 资源生成
- [新增] 基于 `composeApp/src/androidMain/res/drawable/ic_launcher_round.xml` 生成 `docs/AppIcon.png`、`docs/AppIcon.ico`、`docs/AppIcon.icns` 与中间 SVG，匹配 Compose Desktop Linux、Windows、macOS 打包配置。
- [新增] 新增 `scripts/generate_desktop_icons.py` 与 `docs/specs/desktop-icon-assets.md`，记录桌面图标生成流程、输出文件与 Gradle 接入路径。

## [2026-07-14] - Android App Icon GRIS 风格重构
- [修改] 全局重构 `composeApp/src/androidMain/res/drawable/ic_launcher.xml` 与 `composeApp/src/androidMain/res/drawable/ic_launcher_round.xml`，放弃萌宠卡通路线，改为受 GRIS 艺术方向启发的空灵水彩人物剪影、流动斗篷、种子光点与羊皮纸留白。

## [2026-07-13] - 包名替换脚本资源导入适配
- [修改] 优化 `package_replace.kts`，新增 Compose generated resources 包前缀替换配置，支持 `oldappname.ui_theme.generated.resources.Res` 到 `newappname.xxxxxx.generated.resources.Res` 这类资源导入迁移。
- [修改] 增强脚本目录过滤，跳过所有模块级 `build`、`.gradle`、`.git` 等生成目录，并在替换后清理重复 `import` 行。
- [文档] 新增 `docs/specs/package-replace-script.md`，记录脚本配置项、Compose 资源导入迁移规则和遍历边界。

## [2026-07-10] - Chat 工具日志外键修复
- [修复] 将 `ChatHistoryDao` 中 `chat_sessions`、`chat_messages`、`chat_tool_logs` 的写入从 SQLite `REPLACE` 语义改为 Room `@Upsert`，避免更新 session 或 message 时触发外键级联删除。
- [修复] `ChatHistoryRepository.upsertToolLog()` 写入前检查父消息是否仍存在，防止生成过程中会话被删除或状态切换时工具日志写入导致后台协程崩溃。
- [文档] 更新 `docs/specs/chat-history-room-persistence.md`，记录 `REPLACE` 与外键级联删除的风险及后续约束。

## [2026-07-10] - Agent Loop 与工具运行时重构
- [新增] 新增 `AgentLoopRunner`、`AgentLoopState`、`AgentLoopEvent` 与 `LmChatSession`，将工具调用循环从 `ChatViewModel.getTextTalkerResponse()` 抽离为可测试的 harness 层。
- [修改] 将 `AgentTools` 改为工具注册表模式，统一 LiteRT tools schema 生成和执行分发，并将工具结果标准化为 `ToolExecutionResult` JSON。
- [修改] 下线 `loadSkill`、`runMcpTool`、`runIntent` 三个未接入真实系统的模型可见占位工具，旧调用会返回结构化失败而不是伪成功。
- [修改] `ChatViewModel` 改为消费 `AgentLoopEvent` 更新 UI 与工具日志，并在助手消息 metadata 中记录 `agent_turn_count` 与 `agent_transition`，降低任务状态漂移风险。
- [新增] 新增 `AgentLoopRunnerTest` 与 `AgentToolsTest`，覆盖工具回灌循环、无工具结束、禁用工具和 schema 暴露边界。
- [文档] 新增 `docs/designs/agent-loop-tool-runtime.md`，并更新 `docs/specs/llm-agent-iteration-roadmap.md` 的 2026-07-10 状态记录。

## [2026-07-01] - Bazel Android NDK UTF-8 参数修复
- [修复] 调整 `.bazelrc.user`，移除全局 `build --copt=/utf-8` 与 `build --cxxopt=/utf-8`，避免 Android NDK `clang.exe` 将 MSVC 风格 `/utf-8` 解析为输入文件。
- [修改] 为 Windows desktop Bazel 构建新增显式 `--config=msvc_target_utf8`，并保留 `--config=win_host` 下的 host-only UTF-8 参数，确保 MSVC host 工具链仍按 UTF-8 编译。
- [文档] 新增 `docs/specs/bazel-windows-android-rc.md`，记录 Windows host、Windows target 与 Android target 的 Bazel RC 参数边界及验证方式。

## [2026-06-25] - Chat 会话持久化与历史记录
- [新增] 引入 KMP Room、KSP 与 bundled SQLite，新增 `AgentDatabase`、`ChatHistoryDao`、`ChatHistoryRepository` 及 Android/Desktop/iOS 数据库 builder，实现跨端会话持久化。
- [新增] 新增 `chat_sessions`、`chat_messages`、`chat_tool_logs` 三张表，持久化会话标题、创建/更新时间、消息 role/content/tool_calls/tool_responses/metadata 及工具调用日志关联。
- [新增] `ChatViewModel` 接入 Room，会在启动时恢复最近会话，发送消息时自动创建/更新会话，并在工具调用开始/完成时写入日志。
- [新增] `ChatScreen.kt` 增加历史面板，支持搜索、打开、重命名、删除和导出到剪贴板；`LibraryScreen.kt` 的 Living Memory 改为展示真实最近会话并可跳转 Chat。
- [修改] 扩展 `ChatMessage` 数据载体，新增 `ChatRole`、`PersistentToolCall`、`PersistentToolResponse` 以承载持久化所需结构化字段。
- [文档] 新增 `docs/specs/chat-history-room-persistence.md`，并更新 `docs/specs/llm-agent-iteration-roadmap.md` 的 4.1 状态。

## [2026-06-25] - LLM Agent 迭代路线文档
- [新增] 在 `docs/specs/llm-agent-iteration-roadmap.md` 中沉淀当前 LiteRT LLM Agent 能力扫描、缺口分析、优先级路线、建议迭代顺序与验收标准，用于后续 Agent 化功能迭代。
- [修改] 补充 LLM Agent 关键缺口索引，并将 `runIntent` 真实平台动作独立列为 P0 迭代项，覆盖上下文管理、长上下文压缩、长期记忆/RAG、真实 MCP、路由提示词、工具可靠性与会话持久化等遗漏项。

## [1.0.1] - 2026-06-24
### Added
- 在 `AGENTS.md` 中新增「智能体任务交付标准 (Definition of Done - DoD)」与「任务执行三步走协议」，强制智能体在编程任务中同步更新设计文档及 `CHANGELOG.md`。
- 在 `docs/code-style-guide.md` 的开发流程中嵌入文档与 `CHANGELOG.md` 同步步骤，保证后续对话中严格落实「仓库即记录系统」核心原则。
