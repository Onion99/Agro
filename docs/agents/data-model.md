
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
- `LottieAnimation` 直接持久化模型生成并经 sanitizer/validator 处理后的标准 Lottie JSON、
  标题、画布尺寸、FPS、时长、循环标记和可选原始 payload。客户端不根据意图字段或模板
  本地生成动画；首版不写入 `.lottie` ZIP、外部资源 URL 或 blob。
- `Unsupported` 是前向兼容边界：未知类型、更高版本或无效载荷必须保留原始内容，
  不得让整段会话反序列化失败。
- 纯文本消息优先通过 `ChatMessage.text(...)` 创建，避免调用端重复构造单元素列表。

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

## 5. 8-bit BGM 规格

- `ChiptuneBgmMmlSpec` 是 `chiptune_bgm_mml` JSON envelope 的可序列化模型，承载
  BPM、循环小节、采样率、位深、主音量与轨道集合。
- `ChiptuneMmlTrack` 只承载 channel、可选 duty cycle 与 MML；MML 事件和渲染状态
  属于 `composeApp` 内部模型，不进入持久化公共数据层。

## 6. Lottie 动画数据边界

- Gemma4 直接输出 Native Lottie JSON；根对象包含 `v`、`fr`、`ip`、`op`、`w`、`h`、`nm`、
  `ddd`、`assets` 和 `layers`。
- `composeApp` 的 parser 只负责 JSON 清洗、资源/图层安全校验和元数据提取，不包含模板、
  `kind/style/seed` 分支或本地几何合成器。
- 公共数据层不再定义 `LottieAnimationSpec` 意图模型；Compottie 渲染、保存 `.json` 和复制
  原始 payload 属于 `composeApp` UI/消息解析层。
