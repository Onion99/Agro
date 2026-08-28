# Chat 会话持久化与历史记录

> 日期: 2026-06-25（2026-08-28 更新至 Schema v3）
> 范围: `composeApp` Chat/Library 会话历史能力

## 1. 目标

补齐 Chat 会话的长期保存能力，替代仅存在内存中的 `currentChatMessages`，并让 Chat 页和 Library 页共享同一份历史记录。

## 2. 技术方案

采用 KMP Room 作为跨端持久化层。

- Room/KMP 配置依据 Android 官方 Room KMP 文档: https://developer.android.com/kotlin/multiplatform/room
- 数据库定义位于 commonMain: `org.onion.agro.database.AgentDatabase`
- 平台差异仅保留 database builder:
  - Android: `AndroidAgentDatabase.kt`
  - Desktop: `DesktopAgentDatabase.kt`
  - iOS: `IosAgentDatabase.kt`
- 使用 `BundledSQLiteDriver`，避免各平台系统 SQLite 版本差异。

## 3. Schema

### `chat_sessions`

保存会话索引。

- `id`: 会话主键。
- `title`: 会话标题，默认由首条消息截断生成。
- `mode`: 会话模式，当前为 `default` 或 `svg_image`。
- `system_instruction`: 已成功应用到原生模型会话的 system instruction 快照。
- `created_at_millis`: 创建时间。
- `updated_at_millis`: 更新时间。
- `message_count`: 消息数量。
- `last_message_preview`: 最近消息摘要，用于列表与搜索。

### `chat_messages`

保存消息实体。

- `id`: 消息主键，对应运行时 `ChatMessage.id`。
- `session_id`: 所属会话。
- `role`: `system`、`user`、`assistant`；工具交换归属于 assistant 消息。
- `content`: 文本检索与预览兼容列；完整正文以 `chat_message_contents` 为准。
- `tool_calls`: 工具调用数组 JSON；每项 `arguments` 必须是 JSON object，不接受嵌套 JSON 字符串。
- `tool_responses`: 工具响应数组 JSON；每项 `response` 必须是 JSON object，不接受纯文本或嵌套 JSON 字符串。
- `metadata`: 消息元数据 JSON。
- `created_at_millis`: 消息创建时间。

### `chat_message_contents`（v2）

保存一条消息下按顺序排列的类型化内容块。

- `id`: 内容主键，格式为 `<message_id>:content:<position>`。
- `message_id`: 所属消息，父消息删除时级联删除。
- `position`: 内容在消息内的位置，与 `message_id` 组成唯一索引。
- `type`: `text`、`raster_image`、`svg_image` 或 `unsupported`。
- `schema_version`: 单个内容载荷的版本，当前为 `1`。
- `payload_json`: 文本或媒体元数据的 JSON。
- `payload_blob`: 可空二进制载荷，当前用于位图。

### `chat_tool_logs`

保存工具调用审计日志，并通过 `message_id` 关联到助手消息。

- `id`: 工具日志主键。
- `session_id`: 所属会话。
- `message_id`: 关联的助手消息。
- `tool_name`: 工具名称。
- `arguments`: 工具参数对象序列化后的 JSON，仅作为审计存储边界。
- `response`: 工具响应对象序列化后的 JSON，仅作为审计存储边界。
- `status`: `running` / `completed` / `failed`。
- `started_at_millis`: 开始时间。
- `completed_at_millis`: 完成时间。

## 4. 行为

- 应用启动后，`ChatViewModel` 自动加载最近更新的会话并恢复消息列表。
- 发送消息前若无活跃会话，则自动创建新会话。
- 用户消息立即写入 Room。
- 助手消息先写入占位记录，生成完成或错误时覆盖同一条消息。
- 父消息与全部内容块在一个 Room transaction 中 upsert，避免读到半更新状态。
- 用户取消生成时同时删除内存与 Room 中的助手占位消息。
- 打开历史会话时恢复 `mode` 与 `system_instruction`，重新创建对应的原生 LLM 会话。
- 工具调用开始和完成时写入 `chat_tool_logs`，并在最终助手消息中保存 `tool_calls` 与 `tool_responses`。
- Chat 页历史面板支持搜索、打开、重命名、删除、导出。
- Library 页 Living Memory 卡片展示最近会话，点击后打开该会话并跳转 Chat。

## 5. Schema v3 断代策略

Schema v3 将工具调用协议统一为结构化 `JsonObject`，不兼容 v1/v2 中的字符串字段形态：

1. `AgentDatabase` 不再注册 `MIGRATION_1_2`，也不保留 v1/v2 schema 快照和迁移测试。
2. 数据库从 v1 或 v2 打开时，Room 使用 `fallbackToDestructiveMigrationFrom(true, 1, 2)` 删除
   所有旧表并按 v3 重建；旧会话、消息、内容块与工具日志都会被清空。
3. 当前 schema 只导出到
   `composeApp/schemas/org.onion.agro.database.AgentDatabase/3.json`。
4. 该 destructive fallback 仅允许从 v1/v2 触发。v3 之后若再次修改 schema，必须明确选择新迁移
   或新的断代版本，不允许无边界继承历史兼容代码。

## 6. 当前限制

- 导出当前实现为 Markdown 文本复制到剪贴板，尚未接入跨端文件保存流程。
- 应用重启后会恢复 Schema v3 历史消息；DEFAULT 模式通过 `ContextTranscript` 按合法角色顺序
  重建本地 LLM conversation，结构化生成模式仍保持空历史隔离。
- Windows 环境已验证 Desktop 与 Android Kotlin 编译；iOS target 在当前机器被 Gradle 禁用，未做本机编译验证。
## 2026-07-10 写入语义修复

`chat_sessions`、`chat_messages`、`chat_tool_logs` 的 DAO 写入必须使用 Room `@Upsert`，不能使用 `@Insert(onConflict = OnConflictStrategy.REPLACE)`。

原因:

- SQLite `REPLACE` 实际语义是删除旧行再插入新行。
- `chat_messages.session_id` 外键引用 `chat_sessions.id`，`chat_tool_logs.message_id` 外键引用 `chat_messages.id`。
- 如果更新 session 摘要时使用 `REPLACE`，会先删除 session 行，从而通过 `ON DELETE CASCADE` 删除其 messages 和 tool logs。
- 如果更新 assistant message 时使用 `REPLACE`，会先删除 message 行，从而级联删除该 message 下的 tool logs。
- Agent 工具执行期间随后写入 `chat_tool_logs` 时，就可能引用已经被级联删除的 assistant message，触发 `FOREIGN KEY constraint failed`。

当前修复:

- `ChatHistoryDao.upsertSession()`、`upsertMessage()`、`upsertToolLog()` 改为 `@Upsert`。
- `ChatHistoryRepository.upsertToolLog()` 在写入前检查父 message 是否仍存在；如果用户在生成中删除会话或切换状态导致父记录消失，则跳过该审计日志，避免后台生成协程崩溃。
