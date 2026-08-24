# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2026-08-24] - 重构 Gemma4 4B Lottie 场景生成与确定性编译路线
- [重构] 将 `ChatViewModel` 的 Lottie 主生成协议从完整 Native Bodymovin AST 改为浅层 `lottie_scene` v1；新增 `LottieSceneContract`，让 Gemma4 4B 按“对象→几何/颜色→运动轨”短链输出，并禁止直接生成 `layers/ks/shapes/a/k/s/e`。
- [新增] 新增通用 `LottieSceneCompiler`，将 ellipse/rect/star/path、fill/stroke 及 position/scale/rotation/opacity/trim 归一化轨确定性编译为 240×240、30 FPS 的标准 Native Lottie；无关键词、`kind/style/seed` 或固定模板分支。
- [修改] Lottie 模式启用 `temperature<=0.25`、`topP<=0.9`、`topK<=20` 与 1536-token 输出预算，并在恢复旧 Lottie 会话时迁移到当前 scene contract，避免历史 Native 提示词继续生效。
- [修复] `LottieJsonSanitizer` 支持提取模型回复中的首个 JSON object、根据组级尺寸与匿名颜色向量恢复 ellipse/fill，并补齐 animated position/opacity/rotation 的 `a=1` 和关键帧 `s→e` 连续性，使用户报告的水滴 JSON 可通过 Compottie 渲染。
- [测试] 扩展 `LottieMessageParserTest` 与 `ContextStrategyTest`，覆盖 compact scene、path Trim Path、报告的 malformed 水滴 Native JSON、Compottie 解析以及 Lottie 输出预算；Java 21 下 Desktop Kotlin 编译和定向测试通过。
- [文档] 更新 `docs/specs/lottie-animation-prompt-spec.md`、`docs/designs/gemma4-lottie-json-compottie-route-plan.md` 与 `docs/agents/data-model.md`，记录 v2 协议、编译不变量、兼容边界和持久化语义。

## [2026-08-23] - Gris 艺术水彩与乔布斯极简美学：重塑 LLM 运行态指示器与状态胶囊
- [重构] 深度融合《Gris》艺术语言与乔布斯极简哲学，全面重写 `GrisWatercolorStatusIndicator` 与 `GrisWatercolorStatusChip`。
- [新增] 指示器引入神圣几何星轨仪（0.75dp 极细星轨微环、四象限微星芒锚点、巡游星尘卫星 Comet Particle）、多层有机水彩墨晕（Lissajous 调和漂移）与发光纯澈星核。
- [新增] 状态胶囊引入流光微晶毛玻璃材质（微悬浮、迎光面渐变高光倒角微边框、底层环境水彩溢晕透染），配合平滑字间距与状态淡入淡出动画。
- [文档] 更新 `docs/agents/ui-theme.md`，沉淀神圣几何星轨、多层水彩墨晕与微晶胶囊设计规范。

## [2026-08-23] - 修复 LLM 首轮上下文并统一 Gris 水彩运行态
- [修复] 关闭 Structured 会话中未绑定 JSON Schema 的 tool-call constrained decoding，消除空工具约束导致首轮仅输出 `{` 与换行后结束的问题。
- [修复] SystemInstruction/模式切换先停用旧 conversation，再清空旧消息并强制重建；回放时排除生成中、取消或失败的完整 turn，避免旧 KV 污染与当前 prompt 重复注入。
- [修复] LiteRT-LM 流式回调改为有序缓冲投递，并拦截纯括号/标点终止响应；初始化失败不再误报模型加载完成。
- [新增] 引入 `LlmEngineStatus` 运行态与 Gris 水彩指示器/Chip，在 Chat、Library 和主导航统一展示待加载、初始化、应用上下文、就绪、生成和错误状态，并仅在 `READY` 时开放发送。
- [测试] 扩展 `ContextStrategyTest`，覆盖 Structured 约束开关、生成中 prompt 不回放、失败 turn 隔离与无效终止输出。
- [文档] 更新 `docs/designs/app-context-management-design.md`、`docs/agents/ui-theme.md` 与 `docs/agents/data-model.md`，记录桥接边界、切换事务、状态模型及视觉规范。

## [2026-08-22] - 修复结构化生成会话恢复后的上下文污染
- [修复] SVG、BGM、Lottie 会话恢复或重建时不再将持久化历史消息注入 constrained decoding 的 KV Cache，避免生成仅输出 `{` 等不完整 JSON 片段。
- [测试] 增加结构化模式历史不回放的回归测试。
- [文档] 更新 `docs/designs/app-context-management-design.md`，补充结构化会话恢复约束。


## [2026-08-20] - 重构 LLM 上下文编排与 LiteRT-LM 运行时边界
- [新增] 引入 `ContextStrategy`、`ContextCoordinator` 与 `ContextTranscript`，按 Chat/Structured 双模式隔离 KV cache，按 session/system contract 复用或重建 conversation。
- [修改] `ChatViewModel` 改为通过协调器管理 engine/conversation；打开历史时重放持久化消息，后端 fallback、取消、模式切换和销毁统一回收 native 资源。
- [新增] 接入原生 KV token count、prefill 配置和每轮 max output token；在推理前执行 `used + incoming + reservedOutput` 硬上限检查，达到阈值自动摘要压缩最近上下文。
- [修改] 扩展 `ConversationContextState`，提供 token 使用量、容量、预算等级与压缩次数，支持 UI/诊断层观察上下文水位。
- [测试] 新增 `ContextStrategyTest`，覆盖结构化生成策略、输出预算边界和历史压缩行为。
- [文档] 更新 `docs/designs/app-context-management-design.md`，记录实现映射与 iOS C API 兼容性边界。

## [2026-08-16] - 针对 Gemma 4B 模型加固 SVG、8-bit BGM 与 Lottie 动效提示词与解析器自愈能力
- [修复] 重构 `ChatViewModel.SVG_IMAGE_SYSTEM_INSTRUCTION`，针对 4B 级小模型在发光/科技感生成时容易回忆破损语料的问题，增加对 `<filter>`、`<feMergeIn>` 等复杂滤镜标签的绝对负向禁令，强制改用渐变叠加与半透明图元实现光效。
- [修复] 在 `SVG_IMAGE_SYSTEM_INSTRUCTION` 中强化扁平化坐标约束与单标签自闭合规范，禁止深层 `<g>` 嵌套，消除小模型自回归末尾标签栈失衡与 `</g>` 连环溢出问题。
- [修复] 重构 `ChatViewModel.CHIPTUNE_BGM_MML_SYSTEM_INSTRUCTION`，补充 4/4 拍节拍时值算术指引（1 小节 = 4x L4 或 8x L8）、各通道音域与职责规范，以及强制轨道与顶层 `bpm` 一致的同步约束。
- [修复] 重构 `ChatViewModel.LOTTIE_ANIMATION_SYSTEM_INSTRUCTION`，补充局部坐标系（禁止 shape 内部写 `[120,120]` 造成二次坐标偏移）、尺寸范围限制（禁止输出 1000px 巨型遮罩）以及关键帧插值规则（`s` 与 `e` 不等），从根本上消除“黑屏/空洞背景”现象。
- [新增] 在 `SvgMessageParser` 中引入 `sanitizeSvg` 自动容错清洗流水线，包含 `filter='url='...'` 嵌套引号修复、破损 filter 标签归一化、孤立多余 `</g>` 自动过滤剔除以及未闭合容器元素自动平衡闭合。
- [新增] 在 `ChiptuneBgmMessageParser` 中引入 `sanitizeBgmPayload`，支持 Markdown 代码块提取、注释/尾随逗号清理、轨道 `T<bpm>` 自动同步对齐与重复块语法规范化。
- [优化] 增强 `LottieJsonSanitizer`，支持畸形孤立双引号行（`",`）清理、零/负数图元退化尺寸（如 `s=[60, 0]` 扁平图元）自动修复为可视圆形、二次坐标偏移自动复位（`p=[120,120]` $\to$ `[0,0]`）、超大图元（$\ge 400\text{px}$）自动压制、关键帧 $s \to e$ 自动补全插值及极低不透明度（$o < 20$）自愈提升。
- [优化] 增强 `ChiptuneBgmMmlParser.parseTrack`，当 4B 模型生成的音符时值轻微超出循环小节时自动平滑截断至 `requiredTicks`，避免因节拍微小漂移抛出 `track_exceeds_loop_length` 异常。
- [测试] 覆盖并运行 `SvgMessageParserTest`、`ChiptuneBgmMmlParserTest` 与 `LottieMessageParserTest`（包含 `repairsGemmaBlankScreenChipFlowJson` 及 `repairsGemmaWaterDropJsonWithStrayQuotesAndZeroDimension` 回归测试），验证 SVG、8-bit BGM 及 Lottie 动效三类生成响应均可稳定解析与渲染。
- [文档] 同步更新 `docs/specs/svg-image-library-card.md`，记录面向 4B 模型的防幻觉约束与解析器自愈机制。

## [2026-08-10] - 修复 Windows Android Bazel host C++ 标准参数
- [修复] `composeApp/build.gradle.kts` 的 `buildAndroidNativeLib` 在 Windows 主机上为 Bazel host 工具链追加 `/Zc:__cplusplus` 与 `/std:c++20`，避免 Abseil host 工具编译继续按低于 C++17 处理。
- [修改] 同步 `.bazelrc.user` 与 `.github/workflows/build.yml` 的 `win_host` 配置，保留 MSVC host C++20、Protobuf MSVC allow 与 Git Bash `shell_executable`，且不污染 Android NDK target 编译。
- [文档] 更新 `docs/specs/bazel-windows-android-rc.md`，记录 Windows Android cross-build 的 host-only MSVC 参数边界与验证期望。

## [2026-08-10] - 修复畸形 Lottie JSON 结构边界与多余引号
- [修复] 优化 `LottieJsonSanitizer`，新增 `strayQuotesBeforeKeyRegex` 属性键前多余引号清理及 `repairUnclosedShapesArrayBeforeLayerProperties` 文本层级修复逻辑，自动闭合未封闭的 `shapes` 数组。
- [修复] 优化 `repairUnbalancedBrackets` 根对象闭合检测，在根 JSON 结构完备闭合后可靠截断舍弃 AI 生成的尾部垃圾字符（如末尾多余双引号与无效结构），恢复极度损坏的 Lottie 动效渲染能力。
- [测试] 在 `LottieMessageParserTest` 中挂载 `repairsCrashingEffectJsonWithCorruptedAnimatedKeys` 单元测试，验证极度畸形的 Crashing Effect JSON 可被成功修复并正确解析为 `ChatMessageContent.LottieAnimation`。

## [2026-08-10] - 修复 Lottie JSON 损坏 Key 与动画标记
- [修复] `LottieJsonSanitizer` 新增 `malformedAnimatedKeyRegex` 正则，识别并修复 AI 生成的 `"a0,"` 和 `"a1,"` 语法损坏，正确修正为标准 `"a": 0` / `"a": 1` 属性。
- [修复] 修正 `sanitizeScale` 与 `sanitizePosition` 转换逻辑，当属性包含静态数值/坐标数组（而非 Keyframe JsonObject 列表）时强制重置 `"a": 0`，防止 Compottie 解析 Keyframe 抛出 `JsonDecodingException` 异常。
- [测试] 在 `LottieMessageParserTest` 中补齐 `repairsCubeRotationJsonWithCorruptedAnimatedKeys` 回归测试用例，验证包含 `"a0,"` / `"a1,"` 及带空格数字的畸形 Cube Rotation JSON 可被正常修复并成功解析为 `ChatMessageContent.LottieAnimation`。

## [2026-08-10] - 修复 Lottie path sanitizer 渲染边界
- [修复] `LottieJsonSanitizer` 支持将 `ty="sh"` 的逐点 `{v,i,o,c}` 数组归一化为 Bodymovin path object，并为缺失 group transform 的 shape group 补齐默认 `tr`。
- [修复] 修正 animated `p.k` keyframe object 被当作静态数字数组读取导致 sanitizer 回退原始 JSON 的问题，同时规范 `r` 标量 transform。
- [修复] `LottieJsonValidator` 新增可绘制内容校验，缺少 geometry + fill/stroke 的 Native Lottie JSON 返回 `empty_lottie_drawable_content`。
- [测试] 补齐 `LottieMessageParserTest` 的最小 Native Lottie helper，并新增用户提供的 Fire Flame 点数组 path payload 到 `LottieComposition.parse` 的回归验证。
- [文档] 更新 `ChatViewModel.LOTTIE_ANIMATION_SYSTEM_INSTRUCTION`、`docs/specs/lottie-animation-prompt-spec.md` 和 `docs/designs/gemma4-lottie-json-compottie-route-plan.md`，修正最小示例并记录 sanitizer/validator 边界。

## [2026-08-09] - 修复 iOS/Android/Linux 原生平台构建
- [修复] Linux CI 安装并显式使用 LLVM/Clang 18，避免 Ubuntu 22.04 默认 Clang 14 编译 LiteRT-LM Abseil `std::source_location` 失败。
- [修复] `BuildNativeLibTask` 将 GPU sampler 兼容补丁限制为 macOS，Android/Linux/Windows 不再读取未配置的补丁输入或修改非 macOS 原生源码。
- [修复] 重新对齐 `cpp/patches/lite-rt-lm-ios-native-link.patch` 与 LiteRT-LM `20ccf461` 的 `WORKSPACE`、`c/BUILD` 和 Apple Rust toolchain 配置，移除已失效的旧 preprocessor hunk。
- [文档] 更新 `docs/specs/bazel-windows-android-rc.md` 与 `docs/specs/ios-litertlm-platform.md`，记录跨平台工具链和 iOS patch 验证边界。

## [2026-08-09] - 增强 Lottie JSON sanitizer
- [修复] 扩展 LottieJsonSanitizer 对 Gemma4 常见损坏 token 的修复，覆盖未加引号 key/value、缺失冒号、数字误带引号、.2 前导小数、相邻数组对象漏逗号和缺失对象边界。
- [修复] 规范化 shape transform 的 s/a/p/r/o 属性包装与缩放单位，避免生成裸数组导致 Compottie Invalid vector 渲染失败。
- [测试] 新增用户提供的 Fire malformed JSON 回归用例，并直接通过 LottieComposition.parse 验证 parser 到 Compottie 的完整链路。
- [文档] 更新 docs/specs/lottie-animation-prompt-spec.md 与 docs/designs/gemma4-lottie-json-compottie-route-plan.md，记录 sanitizer 修复边界和验证标准。

## [2026-08-09] - 移除本地 Lottie 固定动画生产
- [重构] `LottieAnimationSpecParser.kt` 改为只清洗、校验和解析模型直接输出的 Native Lottie JSON，删除 `LottieJsonBuilder` 及所有 `kind/style/seed` 本地模板和数学几何生成逻辑。
- [修改] 重构 `ChatViewModel.LOTTIE_ANIMATION_SYSTEM_INSTRUCTION`，加入单圆形呼吸动画最小示例、根对象/图层/形状/关键帧参数字典和明确的动画编排步骤。
- [测试] 更新 `LottieMessageParserTest`，验证旧 `lottie_animation_spec` 被拒绝，模型直接生成的最小 Native Lottie JSON 可以解析。
- [修改] 更新 Lottie 原始 JSON 复制按钮的中英文文案，避免继续显示已移除的“动画规格”概念。
- [文档] 新增 `docs/specs/lottie-animation-prompt-spec.md`，同步更新 Lottie 路线和 `docs/agents/data-model.md`。

## [2026-08-09] - 强化 Gemma4 Lottie 动画指导
- [修改] 全面扩充 `ChatViewModel.LOTTIE_ANIMATION_SYSTEM_INSTRUCTION`，明确 `lottie_animation_spec` 默认输出契约、字段参数含义、六类动画样式、分阶段时序、毫秒到帧的换算，以及 Native Lottie 根对象、图层、形状和关键帧字段规则。
- [修改] 将 Gemma4 的默认 Lottie 输出路径调整为高层 Spec，保留用户明确要求 raw/native JSON 时的原生 Lottie 路径，减少端侧模型直接生成复杂图层 AST 的错误。
- [文档] 新增 `docs/specs/lottie-animation-prompt-spec.md`，并同步更新 `docs/designs/gemma4-lottie-json-compottie-route-plan.md` 的提示词契约。

## [2026-08-09] - 修复 macOS LiteRT-LM GPU sampler 初始化
- [修复] 放宽 `runtime/components/sampler_factory.cc` 对旧版 WebGPU Top-K sampler 的动态符号要求，仅将 Create/Destroy/Sample 作为必需 ABI，避免缺少可选扩展符号时错误回退 CPU sampling。
- [修改] 新增 `cpp/patches/lite-rt-lm-gpu-sampler-compatibility.patch` 并接入 `composeApp/build.gradle.kts`，桌面 Bazel 构建前自动应用兼容补丁。
- [修复] 更新 `LiteRtLmJni.desktop.kt`，GPU native engine 初始化失败时不再静默创建 CPU engine，保持实际 backend 与 UI 状态一致。
- [文档] 新增 `docs/specs/litertlm-macos-gpu-mode.md`，并更新 `docs/agents/native-cpp.md`，记录 macOS WebGPU-on-Metal 路径与 sampler ABI 边界。

## [2026-08-09] - 修复 macOS Bazel 启动输出根
- [修复] 更新 `composeApp/build.gradle.kts` 的 Bazel 启动参数，在 macOS/Linux 上覆盖 Windows 的 `G:/_b` 输出根，避免从 `cpp/lite-rt-lm/G:/_b` 加载内嵌 JDK 并修复 `libjimage.dylib` 加载失败。
- [修改] 保留 Windows 短输出根和 `BAZEL_VC` 配置，并让 CI 通过 `BAZEL_OUTPUT_ROOT` 将绝对输出根传递给 Gradle。
- [文档] 更新 `docs/specs/bazel-windows-android-rc.md`，记录跨平台 Bazel rc 的启动参数边界，以及 macOS prebuilt LFS pointer 的恢复和校验步骤。

## [2026-08-07] - Lottie Prompt 优化
- [重构] `LottieAnimationSpecParser.kt` 彻底抹平所有写死输出与关键词匹配（移除 `containsAny` 与模板分支逻辑）。对于 Spec，完全采用纯数学公式（基于 `seed`、`intensity`、`staggerMs`、`palette`）参数化合成 N 边形/椭圆与动态 Keyframe，零硬编码。
- [新增] `ChatViewModel.kt` 系统 Prompt 重构：大模型拥有 100% 自由直接写 Native Lottie JSON，4B 模型只需输出轻量 Intent，且前端不做任何预设形状绑死。
- [测试] 运行并通过 `LottieMessageParserTest.kt`，验证纯 Native JSON 与纯数学参数化矢量的解析渲染。
- [新增] `LottieJsonSanitizer.kt` 智能容错与补全引擎：全面升级以修复 LLM 产生的严重语法与 AST 格式错误（包含数字空格拆分如 `"h": 2 400` 自动修复、连续冗余逗号修复、数组内部未闭合对象断裂自动拼接、作用域受限栈匹配策略以防止内部游离 `}` 消费外层作用域、嵌套 shape 属性如 `fl`/`st` 递归拆解为独立 shape node 并剥离非标准 shape 属性、scale keyframe 单元素 scale 智能补全、画布与变换坐标归一化 `[1, 1, 1] -> [100, 100, 100]`、自动补齐缺失 bracket 及 Markdown 拆包），彻底解决异常 LLM JSON 导致的解析崩溃。
- [文档] 更新 `docs/specs/lottie-animation-prompt-spec.md` 规范文档至 v1.3.0 并全面同步修缮 `docs/designs/gemma4-lottie-json-compottie-route-plan.md` 设计方案。

## [2026-08-06] - GPU 模式错误 Toast 提示
- [新增] 在 `ChatViewModel` 中新增 `toastEvent` SharedFlow 与 `showToast()` 触发逻辑，当 GPU decode 失败并回退至 CPU 模式时，触发 Toast/Snackbar 提示用户。
- [新增] 在 `strings.xml` 与 `values-zh/strings.xml` 中补齐 `chat_gpu_decode_failed_fallback_cpu` 多语言资源。
- [修改] 在 `ChatScreen.kt` 中监听 `chatViewModel.toastEvent` 并通过 `SnackbarHost` 展示 Toast 提示。

## [2026-08-06] - LiteRT-LM GPU Decode 错误恢复
- [修复] 更新 `LmConversation` 与 `LmEngine`，保留 LiteRT-LM native status code，并在引擎或会话创建返回空指针时立即失败，避免继续使用无效 native 句柄。
- [修复] 更新 `ChatViewModel` 的 LLM 生成流错误处理，GPU decode 返回 `INTERNAL` 时重建 CPU 引擎/会话并仅重试一次，同时避免 `.catch` 吞错后继续执行成功收尾覆盖错误消息。
- [修改] 更新 `LiteRtLmJni.desktop.kt`，移除 desktop native 层静默 CPU fallback，避免 UI 显示 GPU 但实际后端已切换。
- [修复] 更新 `litertlm.cc`，通过 `Status::ToString()` 将完整 native 错误和 source trace 传回 Kotlin。
- [文档] 更新 `docs/agents/native-cpp.md`，记录 JNI status 透传与 desktop GPU decode 失败后的会话重建边界。

## [2026-08-06] - Windows LiteRT-LM GPU 运行时一致性修复
- [修复] 更新 `composeApp/build.gradle.kts` 的 desktop native 产物同步逻辑，始终用 `cpp/lite-rt-lm/prebuilt/<platform>` 覆盖 `cpp/libs` 中的 LiteRT/GPU 运行时库，避免 `liblitertlm_jni.dll` 构建产物旁混入不同版本的 `libLiteRt.dll` 后触发 Windows WebGPU 初始化崩溃。
- [文档] 更新 `docs/agents/native-cpp.md`，记录 desktop GPU 运行时库必须以 prebuilt 集合为准，Bazel 产物只提供 JNI 桥接库。

## [2026-08-05] - LiteRT-LM iOS Native 库嵌入
- [修改] 更新 `composeApp/build.gradle.kts` 的 iOS Kotlin/Native 链接配置，按 `cpp/lite-rt-lm/prebuilt/ios_arm64` 与 `ios_sim_arm64` 同步 `libLiteRt.dylib`、Metal accelerator、TopKMetal sampler 和 Gemma 约束库。
- [修改] 新增 Xcode 构建期 dylib 嵌入任务，将匹配 SDK 的 LiteRT-LM iOS prebuilt 动态库复制到 App `Frameworks` 目录并在允许时执行 codesign，补齐 iOS GPU/Metal 后端运行时依赖。
- [文档] 更新 `docs/agents/native-cpp.md`，记录 iOS 不走 `System.loadLibrary`，而是在 Kotlin/Native 链接与 Xcode 嵌入阶段处理平台库。

## [2026-08-05] - LiteRT-LM 多平台 Native 库加载
- [修改] 将 `composeApp/src/jvmMain/kotlin/org/onion/agro/utils/NativeLibraryLoader.kt` 扩展为按当前 OS/arch 选择 `cpp/lite-rt-lm/prebuilt` 平台库清单，覆盖 Windows、Linux x64/arm64 与 macOS arm64 的 LiteRT runtime、Gemma 约束库和 GPU 插件加载顺序。
- [修改] 简化 `composeApp/src/desktopMain/kotlin/com/google/ai/edge/litertlm/LiteRtLmJni.desktop.kt` 初始化逻辑，由 JVM loader 统一处理平台库计划，Windows actual 仅保留 DLL 搜索目录注册。
- [修改] 更新 `composeApp/src/androidMain/kotlin/com/google/ai/edge/litertlm/LiteRtLmJni.android.kt`，按 Android prebuilt GPU/OpenCL/WebGPU 插件做可选加载，缺失时保持 CPU/JNI 路径可用。
- [修改] 更新 `composeApp/build.gradle.kts` 的 native 构建产物同步逻辑，根据平台复制 `prebuilt/<platform>` 运行时库到 `cpp/libs`，再同步到 JVM resources 或 Android `jniLibs`。
- [文档] 更新 `docs/agents/native-cpp.md`，记录多平台 native 库加载边界与 `prebuilt` 目录映射。

## [2026-08-05] - LiteRT-LM Native ABI 签名对齐
- [修复] 对齐 `composeApp/src/desktopMain/kotlin/com/google/ai/edge/litertlm/LiteRtLmJni.desktop.kt` 与当前 `litertlm.cc` 的 `nativeCreateConversation`、`nativeSendMessage`、`nativeSendMessageAsync` 参数表，避免 Windows JNI 短符号绑定后因参数漂移读取栈垃圾并触发 JVM `EXCEPTION_ACCESS_VIOLATION`。
- [修复] 同步更新 `composeApp/src/androidMain/kotlin/com/google/ai/edge/litertlm/LiteRtLmJni.android.kt` 的私有 external 声明与默认禁用参数，保持 Android/desktop actual 层和 native ABI 一致。
- [修复] 更新 `composeApp/src/iosMain/kotlin/com/google/ai/edge/litertlm/LiteRtLmJni.ios.kt`，对齐 LiteRT-LM C API 新增的 `LiteRtLmConversationOptionalArgs*` 参数和 `LiteRtLmStreamChunk` 流式回调形态，并补充 `overwritePromptTemplate` 到 iOS prompt template 配置的映射。
- [修复] 更新 `composeApp/src/nativeInterop/cinterop/litertlm.def` 与 `composeApp/build.gradle.kts` 的 iOS cinterop 配置，同时导入 `engine.h`、`conversation.h` 并补齐 native root include 路径，确保 Kotlin/Native 生成当前 C API 符号。
- [文档] 更新 `docs/agents/native-cpp.md`，补充 Kotlin external 与 C++ JNI 导出函数的 ABI 对齐要求。
- [文档] 更新 `docs/specs/ios-litertlm-platform.md`，记录 iOS C API optional args、stream chunk 生命周期和仍未暴露的 per-turn 能力边界。

## [2026-08-05] - Windows LiteRT-LM GPU 加速加载修复
- [修复] 调整 `composeApp/src/desktopMain/kotlin/com/google/ai/edge/litertlm/LiteRtLmJni.desktop.kt` 的 Windows native 初始化顺序，先注册 DLL 搜索目录并准备 WebGPU/DXC/Dawn 依赖，再加载动态 `libLiteRt.dll`、约束库和 `liblitertlm_jni.dll`，避免 GPU 后端初始化时混用静态/动态 LiteRT runtime。
- [修改] 扩展 `composeApp/src/jvmMain/kotlin/org/onion/agro/utils/NativeLibraryLoader.kt`，支持只从资源解压 native 库而不立即 `System.load`，让 LiteRT native 侧按需加载 WebGPU accelerator 与 sampler。
- [修改] 为 `composeApp/build.gradle.kts` 的 Windows Bazel native 构建追加 `litert_runtime_link_mode=dynamic` 与 `resolve_symbols_in_exec=false`，使 JNI 与 WebGPU 组件使用同一动态 LiteRT runtime。
- [文档] 更新 `docs/agents/native-cpp.md`，记录 Windows GPU/WebGPU JNI 加载边界与库顺序。

## [2026-08-02] - SVG 图像背景层级修复
- [修复] 更新 `ChatViewModel` 的 SVG 图像生成指令，明确 SVG painter's order、背景位置及响应前覆盖检查，避免模型将不透明全画布背景追加到前景后导致预览只显示背景色。
- [文档] 更新 `docs/specs/svg-image-library-card.md`，记录 SVG 背景与前景的绘制顺序约束。

## [2026-08-02] - iOS 8-bit BGM 本地播放修复
- [修复] `ChatScreen` 通过 `BgmAudioFileStore.playbackUri()` 将缓存 WAV 的裸文件路径转换为 percent-encoded `file://` URI，再交给 ComposeMediaPlayer，修复 iOS AVFoundation 将 `/var/...` 识别为 unsupported URL 并报 `-1002` 的问题。
- [修复] 新增 `BgmAudioPlayer` 平台播放边界；iOS 改用 `AVAudioPlayer` 直接加载本地 WAV，绕过 ComposeMediaPlayer `AVPlayer` 路径对 8-bit PCM 触发的 `FigFile -17913`，Android/Desktop 保持 ComposeMediaPlayer 后端。
- [测试] 扩展 `ChiptuneBgmMmlParserTest`，覆盖 POSIX、Windows 和 Unicode 本地路径到播放 URI 的转换。
- [文档] 更新 `docs/designs/gemma4-8bit-bgm-json-mml-composemediaplayer-route-plan.md`，记录聊天持久化路径与播放器 URI 的边界。

## [2026-08-02] - iOS LiteRT LM 会话重建兼容修复
- [修复] `ChatViewModel` 在 iOS 静态 archive 构建中创建 LiteRT LM 会话时禁用 constrained decoding，避免 SVG、8-bit BGM、Lottie 和应用会话设置重建路径请求已由 `LITERT_LM_FST_CONSTRAINTS_DISABLED` 禁用的 FST provider 而失败。
- [文档] 更新 `docs/specs/ios-litertlm-platform.md`，说明 iOS 结构化模式保留 prompt 和 parser 校验，但不启用原生 constrained decoding 的运行时边界。

## [2026-08-02] - iOS Kotlin/Native KLIB ABI 兼容修复
- [修改] 将 Kotlin 升级至 `2.4.0`，并将 Compose Multiplatform 和 Compose Hot Reload 升级至 `1.11.1`，使 Kotlin/Native 能消费 FileKit `0.14.2` 与 Compose Media Player `0.11.3` 发布的 ABI `2.4.0` iOS KLIB。
- [修改] 保留 FileKit `0.14.2` 和 Compose Media Player `0.11.3`，对齐其声明的 Kotlin `2.4.0`、Compose runtime `1.11.1` 依赖约束。
- [修改] 将 Coil 升级至 `3.5.0`，匹配 FileKit `0.14.2` 所需版本并对齐 Compose Multiplatform `1.11.1` 的 Skiko `0.144.6`。
- [修复] 更新 `build-logic/convention` 的 Compose Compiler DSL，使用 Kotlin `2.4.0` 所需的 `stabilityConfigurationFiles` 配置 `stability_config.conf`。
- [修复] 恢复 `systemFileSystem` expect/actual 边界与 Okio `use` 扩展，避免 commonMain 直接依赖平台细化的 `FileSystem.SYSTEM`，并修复 Kotlin `2.4.0` 下 `FileHandle` 的关闭调用。
- [修复] 恢复被误删的 `SplashArtwork.kt` 和启动页设计文档，重新提供 `SplashScreen` 所引用的 `AnimatedAppIconSeed`。
- [文档] 更新 `docs/specs/ios-litertlm-platform.md`，记录 iOS KLIB ABI `2.4.0` 的 Kotlin、Compose Multiplatform、FileKit 和 Compose Media Player 版本边界。

## [2026-08-02] - AppIcon 主题化创意启动页
- [修改] 重构 `SplashScreen.kt` 并新增 `SplashArtwork.kt`，移除宇航员图片，以“玻璃容器中的本地智能种子”为叙事，结合主题水彩潮汐、GRIS 风格线稿地景、AppIcon 分层生长动画、毛玻璃容器和单栏/双栏响应式构图。
- [文档] 新增 `docs/designs/app-icon-theme-splash-screen.md`，记录品牌叙事、主题 token 落地、动画阶段与跨平台边界。

## [2026-08-02] - 创意反馈入口
- [修改] 更新 `LibraryScreen.kt` 中的 `ForgeNewVesselCard`，点击后展示带背景模糊的创意反馈弹窗，并提供 MineAgent 项目主页跳转入口。
- [新增] 为弹窗补充中英文标题、说明、项目地址和操作按钮资源。

## [2026-08-02] - Library card hover illustration animations
- [修改] 更新 `LibraryScreen.kt` 中的 `EightBitBgmCard` 与 `SvgImageCard`，为均衡器柱与 SVG 预览图加入由 `isHovered` 驱动的 360ms 补间动画，使其与 `LottieAnimationCard` 的悬停反馈保持一致。

## [2026-07-29] - 会话上下文详情浮层展示
- [修改] 更新 `composeApp/src/commonMain/kotlin/org/onion/agro/ui/screen/ChatScreen.kt`，将 `ConversationContextHeader` 的 system instruction 展开内容从主 `Column` 布局流中移出，避免挤占 `ChatMessagesList` 的 `weight(1f)` 空间。
- [新增] 新增 `ConversationContextDetailsOverlay` 根级浮层，复用原有复制、选择、滚动和展开动画，并与 `ChatHistoryPanel` 保持互斥展示。
- [文档] 新增 `docs/designs/conversation-context-header.md`，记录上下文标题栏的浮层层级、响应式宽度和交互边界。
## [2026-07-28] - Lottie JSON 动画生成卡片与本地预览
- [新增] 新增 `LOTTIE_ANIMATION` 专用聊天模式、`lottie_animation_spec` 数据模型、parser/validator、确定性 `LottieJsonBuilder` 与 `LottieMessageParser`，将模型输出转换为可持久化的 `ChatMessageContent.LottieAnimation`。
- [修改] 将 `LibraryScreen` 的 `LogicVesselCard` 改为可点击的 Lottie 动画生成入口，并在 `ChatViewModel` 中接入专用 system instruction 和结构化响应分发。
- [新增] 在 `ChatScreen` 中使用 Compottie `JsonString` 预览本地 Lottie JSON，支持复制最终 JSON、复制原始 spec 与保存 `.json`，并补齐中英文 i18n 资源。
- [测试] 新增 `LottieMessageParserTest`，覆盖合法构建、确定性输出、Markdown 包裹拒绝、完整 layer tree 拒绝、motion style 不匹配和 duration 越界。
- [文档] 更新 `docs/designs/gemma4-lottie-json-compottie-route-plan.md` 与 `docs/agents/data-model.md`，记录首版落地范围、持久化边界和未纳入首版的 dotLottie/外部资源能力。

## [2026-07-28] - Lottie JSON 动画生成路线计划
- [文档] 新增 `docs/designs/gemma4-lottie-json-compottie-route-plan.md`，规划参考 8-bit BGM JSON + MML 路线并基于现有 `compottie` 库实现 Lottie 微动画生成，明确 `lottie_animation_spec`、本地 Lottie JSON builder、Compottie `JsonString` 渲染、dotLottie/网络资源边界、消息持久化、验证计划与风险缓解。

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
