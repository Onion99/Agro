
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
- `ChatMessageContent` 当前包含 `Text`、`RasterImage`、`SvgImage` 与
  `Unsupported`。每个实现都必须携带 `schemaVersion`。
- `Unsupported` 是前向兼容边界：未知类型、更高版本或无效载荷必须保留原始内容，
  不得让整段会话反序列化失败。
- 纯文本消息优先通过 `ChatMessage.text(...)` 创建，避免调用端重复构造单元素列表。

## 4. 聊天会话上下文

- `ChatSessionMode` 表达可持久化的聊天对象/协议模式，当前支持普通助手与 SVG 图像生成器。
- `ConversationContextState` 是 UI 与 ViewModel 共享的当前上下文状态；
  `isApplied` 只在原生模型 conversation 成功创建后为 `true`。
- system instruction 的完整文本由持久化层保存快照，不应仅从模式名在 UI 中推断。
