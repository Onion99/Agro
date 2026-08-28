
> 本模块定义了系统的数据载体

## 1. 结构与生命周期
- **无状态/纯数据**：本模块只包含 Kotlin 核心 `data class`、`enum` 与 `sealed class/interface`。
- **纯 Kotlin/Common 模块**：不允许依赖平台特定库（如 Android Context / JVM/iOS 专有包）。
- **零依赖原则**：不依赖 `composeApp`、`ui-theme`、`data-network`。它作为基础依赖被所有其他模块所共用。

## 2. 序列化规范
- 数据类需要通过网络传输或在 JNI 边界传递时，必须加 `@Serializable` 注解（`kotlinx.serialization`）。
- 优先为可能缺失的字段设置默认值，保障反序列化的稳定性。

## 3. 聊天消息内容

- `ChatMessage` 是消息信封，使用 `role` 表达发送者，使用有序
  `contents: List<ChatMessageContent>` 表达正文。
- 禁止继续在 `ChatMessage` 上增加某一种媒体专属的可空字段；新增内容形态必须实现
  `ChatMessageContent`。
- `ChatMessageContent` 当前包含 `Text`、`RasterImage`、`SvgImage`、`Audio`、
  `LottieAnimation` 与 `Unsupported`。每个实现都必须携带 `schemaVersion`。
- `Audio` 只持久化本地文件路径、MIME、标题、时长、采样信息、循环区间和可选
  `sourceSpecJson`，不得把 WAV/PCM 作为 Room blob 保存。BGM 缓存丢失后可依据
  `sourceSpecJson` 重新渲染。
- `LottieAnimation` 持久化由 scene compiler 生成的标准 Lottie JSON、标题、画布尺寸、FPS、
  时长、循环标记和可选原始 payload。新生成消息的原始 payload 必须是 `lottie_scene`；历史
  消息的 `sourceSpecJson` 可能仍是 Native Lottie，但它只用于审计，不会在恢复时重新解析。
  持久化内容不写入 `.lottie` ZIP、外部 URL 或 blob。
- `Unsupported` 是前向兼容边界：未知类型、更高版本或无效载荷必须保留原始内容，
  不得让整段会话反序列化失败。
- 纯文本消息优先通过 `ChatMessage.text(...)` 创建，避免调用端重复构造单元素列表。
- `ChatRole` 只表达 durable transcript 中的 `SYSTEM`、`USER` 与 `ASSISTANT`；工具调用和响应
  归属于最终 assistant 消息，不创建脱离调用上下文的持久化 `TOOL` 消息。
- `PersistentToolCall.arguments` 与 `PersistentToolResponse.response` 必须是 `JsonObject`。公共模型、
  Agent Loop 和 LiteRT-LM 边界共用结构化 JSON 语义，不提供 JSON 字符串构造器、解析 fallback
  或纯文本响应兼容层；Room TEXT 列的编码仅属于 repository 存储适配器。

## 4. 聊天会话上下文

- `ChatSessionMode` 表达可持久化的聊天对象/协议模式，当前支持普通助手、SVG 图像
  生成器、`CHIPTUNE_BGM_MML` 8-bit BGM 作曲器与 `LOTTIE_ANIMATION` Lottie
  微动画规划器。
- `ConversationContextState` 是 UI 与 ViewModel 共享的当前上下文状态；
  `isApplied` 只在原生模型 conversation 成功创建后为 `true`。
- `LlmEngineStatus` 表达当前 native engine/conversation 的瞬时生命周期，包含
  `UNINITIALIZED`、`INITIALIZING`、`APPLYING_CONTEXT`、`READY`、`GENERATING`
  与 `ERROR`。该枚举供 ViewModel 与 UI 共享，但不序列化、不写入 Room，也不替代
  `ConversationContextState.isApplied` 的上下文提交语义。
- system instruction 的完整文本由持久化层保存快照，不应仅从模式名在 UI 中推断。
- 同一条 assistant 消息中的有序 `toolCalls`、有序 `toolResponses` 与最终正文构成一个可恢复工具轮次。
  只有数量、顺序名称和最终正文都完整时才能向 native 重建为
  `model(tool_calls) → tool(tool_response) → model(final text)`；否则只恢复可用的最终正文。
- 工具协议自 Room Schema v3 起断代；v1/v2 数据不会进入当前公共模型，也不要求模型层长期携带
  旧字段形态的兼容代码。

## 5. 8-bit BGM 规格

- `ChiptuneBgmMmlSpec` 是 `chiptune_bgm_mml` JSON envelope 的可序列化模型，承载
  BPM、循环小节、采样率、位深、主音量与轨道集合。
- `ChiptuneMmlTrack` 只承载 channel、可选 duty cycle 与 MML；MML 事件和渲染状态
  属于 `composeApp` 内部模型，不进入持久化公共数据层。

## 6. Lottie 动画数据边界

- Gemma4 输出 composeApp 私有的 `lottie_scene` v1 场景对象图；协议只承载通用图元、颜色、
  坐标与归一化运动轨，不把 Bodymovin 的 `layers/ks/shapes` 暴露给 4B 模型。
- `composeApp` 的 `LottieSceneResponseParser` 只接受 `lottie_scene` v1，并将输入严格解析一次；
  malformed 或 Native Lottie 模型响应不会再进入猜测性 sanitizer。
- `LottieSceneCompiler` 将场景逐项编译为标准 Native Lottie；它不按用户关键词或
  `kind/style/seed` 选择固定模板。编译器采用闭集映射，未知 Native/外部字段不会进入输出。
- 公共数据层不定义 scene/compiler 的内部模型，也不恢复旧 `LottieAnimationSpec`。公共边界仍
  只有最终 `ChatMessageContent.LottieAnimation`；Compottie 渲染、保存与复制属于 composeApp。
- `LottieAnimation.json` 始终是可直接交给 Compottie 的 compiler-owned Native JSON，
  `sourceSpecJson` 则保留未经编译的模型原始响应，二者不得在持久化恢复时互换。历史
  `LottieAnimation.json` 直接渲染，不重新经过 response parser。
