
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
- `LottieAnimation` 直接持久化本地 builder 生成的标准 Lottie JSON、标题、画布尺寸、
  FPS、时长、循环标记和可选 `sourceSpecJson`。`sourceSpecJson` 保存模型输出的
  `lottie_animation_spec`，`json` 保存可离线预览和导出的最终 Lottie JSON；首版不写入
  `.lottie` ZIP、外部资源 URL 或 blob。
- `Unsupported` 是前向兼容边界：未知类型、更高版本或无效载荷必须保留原始内容，
  不得让整段会话反序列化失败。
- 纯文本消息优先通过 `ChatMessage.text(...)` 创建，避免调用端重复构造单元素列表。

## 4. 聊天会话上下文

- `ChatSessionMode` 表达可持久化的聊天对象/协议模式，当前支持普通助手、SVG 图像
  生成器、`CHIPTUNE_BGM_MML` 8-bit BGM 作曲器与 `LOTTIE_ANIMATION` Lottie
  微动画规划器。
- `ConversationContextState` 是 UI 与 ViewModel 共享的当前上下文状态；
  `isApplied` 只在原生模型 conversation 成功创建后为 `true`。
- system instruction 的完整文本由持久化层保存快照，不应仅从模式名在 UI 中推断。

## 5. 8-bit BGM 规格

- `ChiptuneBgmMmlSpec` 是 `chiptune_bgm_mml` JSON envelope 的可序列化模型，承载
  BPM、循环小节、采样率、位深、主音量与轨道集合。
- `ChiptuneMmlTrack` 只承载 channel、可选 duty cycle 与 MML；MML 事件和渲染状态
  属于 `composeApp` 内部模型，不进入持久化公共数据层。

## 6. Lottie 动画规格

- `LottieAnimationSpec` 是 `lottie_animation_spec` JSON envelope 的可序列化模型，只承载
  动画意图和受限参数，不承载完整 Lottie `layers`、`assets`、字体、图片、表达式或外部资源。
- `LottieCanvasSpec`、`LottiePaletteSpec`、`LottieMotionSpec` 与 `LottieStrokeSpec` 分别承载
  画布、色板、运动和描边参数；所有字段必须先经 `composeApp` 内部 validator 校验，再交给
  `LottieJsonBuilder` 生成最终 Lottie JSON。
- 公共数据层只定义可序列化载体，不依赖 Compottie；Compottie 渲染、保存 `.json` 和复制行为
  属于 `composeApp` UI/消息解析层。
