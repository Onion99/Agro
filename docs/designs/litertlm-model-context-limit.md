# LiteRT-LM 模型 Context 上限读取

## 目标

在加载 `.litertlm` 模型前读取模型声明的 `LlmMetadata.max_num_tokens`，并将该值作为：

- `ChatViewModel.lmMaxNumTokens` 的初始值；
- `LmEngine` 的 `maxNumTokens` 初始化参数；
- 设置页 Context token 调整控件的动态上限。

## 仓库边界

`cpp/lite-rt-lm` 是上游 submodule，本功能不在 submodule 中增加 C、C++ 或 JNI API，也不修改其构建文件。

读取逻辑位于主仓库：

```text
composeApp/src/commonMain/kotlin/org/onion/agro/native/llm/
└── LiteRtLmModelMetadata.kt
```

该实现只依据 LiteRT-LM 已定义的容器格式访问模型文件：

1. 校验 32-byte preamble 中的 `LITERTLM` magic；
2. 读取 preamble 的 `header_end_offset`；
3. 解析 header FlatBuffer 的 `section_metadata.objects`；
4. 定位 `AnySectionDataType.LlmMetadataProto` section；
5. 随机读取该 section，并解析 protobuf 字段 5 `max_num_tokens`。

模型权重和其他 section 不会被加载到内存。随机读取使用 Okio `FileHandle`，解析器由 Android、Desktop 与 iOS 共用。系统文件系统实例通过 `org.onion.agro.io.systemFileSystem` 的 expect/actual 边界注入；`commonMain` 不直接引用 Okio 在 Native/JVM source set 中细化的 `FileSystem.SYSTEM`，从而保证 common metadata 与 iOS Kotlin/Native 都能解析该 API。

## 状态与回退

`ChatViewModel` 在模型初始化或重新应用会话设置前解析 Context 上限，并按模型路径缓存结果。

- 读取到正整数时，`lmMaxNumTokens` 更新为模型值。
- 设置页仍以 128 tokens 为步长调整，但不能超过模型值。
- 模型没有该字段、文件不可读或格式不合法时，记录诊断信息并回退到 8192，不阻断原有模型加载错误处理。
- 选择不同模型时会清除上一个模型的元数据状态，避免沿用旧上限。
- “重置设置”恢复到当前模型上限；当前模型未声明上限时恢复到 8192。

## 安全约束

解析器对 header 大小、section offset、metadata 大小、FlatBuffer 边界与 protobuf wire type 做边界检查。最大 header 与 LiteRT-LM loader 保持为 16 KiB，单独读取的 metadata section 最大允许 16 MiB。

## 验证

`LiteRtLmModelMetadataTest` 使用最小合法 LiteRT-LM 容器覆盖：

- 正确读取 `max_num_tokens = 8192`；
- 字段缺失时返回 `null`；
- 非 LiteRT-LM magic 被拒绝。
