## JNI 与大模型交互架构

为了确保内存安全与异步数据流的稳定性，JNI 通信必须遵守以下面向对象封装准则：

- **引擎生命周期指针管理**：
    - 由 Kotlin 包装类 `LmEngine` 负责底层的原生 C++ 引擎初始化、资源释放，必须保障指针在生命周期结束时正确析构，避免内存泄漏。
    - 桥接层 `LmConversation` 负责管理会话的上下文与流式消息的调度。

- **消息传递机制**：
    - **禁止**直接跨 JNI 传递裸指针或裸原生 JSON 字符串，应在 Kotlin 侧将对象（`Message`, `Content`, `ToolCall`）转换为规范的 `kotlinx.serialization.json` 结构后再行操作。
    - 流式应答（Stream Response）：原生回调应转化为 Kotlin `Flow` 的异步数据流抛出，提供平滑非阻塞的 UI 消费方案。

- **平台 Native 库加载边界**：
    - 运行时库清单必须以 `cpp/lite-rt-lm/prebuilt/<platform>` 为准。Desktop JVM 当前支持 `windows_x86_64`、`linux_x86_64`、`linux_arm64`、`macos_arm64`；Android 当前同步 `android_arm64` 到 `jniLibs/arm64-v8a`；iOS 当前同步 `ios_arm64` 到 `cpp/libs/ios-device`，同步 `ios_sim_arm64` 到 `cpp/libs/ios-simulator`。
    - 桌面端使用 LiteRT-LM GPU 后端时，JNI 库必须与 `libLiteRt.*` 采用动态 runtime 链接，避免 JNI 内静态 LiteRT runtime 与 WebGPU/Metal sampler 或 accelerator 依赖的动态 LiteRT runtime 混用。
    - 桌面端 native 构建完成后，`cpp/lite-rt-lm/prebuilt/<platform>` 中的 `libLiteRt.*`、GPU accelerator/sampler、Dawn/Metal 等运行时库必须覆盖同步到 `cpp/libs` 与 JVM resources；Bazel 产出的 `liblitertlm_jni.*` 只能作为 JNI 桥接库，不能用同名 Bazel runtime 覆盖 prebuilt GPU runtime。
    - Windows 启动阶段必须先创建并注册 native 临时目录到 DLL 搜索路径，再将 `dxil.dll`、`dxcompiler.dll`、`libwebgpu_dawn.dll`、`libLiteRtWebGpuAccelerator.dll`、`libLiteRtTopKWebGpuSampler.dll` 解压到该目录。
    - Desktop JVM 必须在 `liblitertlm_jni.*` 前加载或准备平台依赖：`libLiteRt.*`、`libGemmaModelConstraintProvider.*` 以及平台 GPU 插件。Android 侧 GPU/OpenCL/WebGPU 插件为可选加载，缺失时不得阻断 CPU/JNI 路径。
    - iOS 不使用 `System.loadLibrary`；`LiteRtLmJni.ios.kt` 通过 Kotlin/Native cinterop 直接调用 C API，`composeApp/build.gradle.kts` 必须在链接阶段加入 `libLiteRt.dylib`、`libLiteRtMetalAccelerator.dylib`、`libLiteRtTopKMetalSampler.dylib` 与 `libGemmaModelConstraintProvider.dylib`，并在 Xcode 构建阶段将匹配 device/simulator SDK 的 dylib 嵌入 App `Frameworks` 目录。

- **JNI ABI 对齐**：
    - Kotlin `external fun` 的参数数量、顺序、可空装箱类型必须与 `cpp/lite-rt-lm/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc` 的 `JNI_METHOD(...)` 导出函数逐项一致。
    - 当上游 native 函数新增可选能力参数时，平台 actual 层可以维持公共 expect API 不变，但私有 external 声明必须补齐参数，并显式传入 `null`、`false`、`-1` 或 `0` 等禁用默认值。
    - 特别注意 Windows JNI 短符号名绑定不会校验 Kotlin 声明与 C++ 函数的完整 ABI；签名漂移会在 `nativeCreateConversation`、`nativeSendMessage` 等调用中表现为 JVM `EXCEPTION_ACCESS_VIOLATION`。

- **Kotlin/Native C API 对齐**：
    - iOS `LiteRtLmJni.ios.kt` 通过 cinterop 调用 `cpp/lite-rt-lm/c/engine.h` 与 `cpp/lite-rt-lm/c/conversation.h`，必须随 C 头文件新增参数同步更新调用点。
    - C API 引入 `LiteRtLmConversationOptionalArgs*` 后，未启用的 per-turn 选项应显式传 `NULL`；启用 `visualTokenBudget` 时必须创建 optional args，调用返回后用 `litert_lm_conversation_optional_args_delete` 释放。
    - C API 流式回调中的 `LiteRtLmStreamChunk` 只在回调期间有效，Kotlin/Native 层必须在 callback 内立即复制 text/error 字符串，禁止保存 native chunk 或内部字符串指针。
