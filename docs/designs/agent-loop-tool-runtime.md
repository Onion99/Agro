# LiteRT Agent Loop 与工具运行时设计记录

> 日期: 2026-07-10
> 范围: `composeApp` 内本地 LiteRT LLM 的 agent loop、工具注册表、工具执行结果与聊天层状态记录。

## 1. 目标

本次优化参考 `shareAI-lab/learn-claude-code/s01_agent_loop` 的最小 agent harness，将“模型请求工具 -> 宿主执行工具 -> 工具结果回灌模型 -> 模型继续推理”的循环从 `ChatViewModel` 中抽离出来，避免 UI 层同时承担模型会话、工具执行、状态流转和日志持久化职责。

同时将工具 schema 与工具执行逻辑收敛到同一个注册表，防止模型可见工具和实际可执行工具出现状态漂移。

## 2. 核心组件

- `LmChatSession`: 会话抽象接口，封装 `sendMessageAsync()` 与 `cancelProcess()`，便于生产环境使用 `LmConversation`，测试环境使用 fake session。
- `AgentLoopRunner`: agent harness 核心循环。它只负责消费模型流、检测 `toolCalls`、调用 `AgentToolExecutor`、构造 `Message.tool()` 并继续循环。
- `AgentLoopState`: 运行时状态快照，包含 `turnCount`、`transition`、`lastToolCallCount`。
- `AgentLoopEvent`: loop 对外事件流，包含文本增量、思考增量、工具开始、工具完成、普通完成与最大轮数终止。
- `AgentTools`: 工具注册表与执行器，统一生成 LiteRT LM tools schema 并分发执行。
- `AgentToolDefinition` / `AgentTool` / `ToolExecutionResult`: 工具定义、工具执行接口和结构化工具结果。

## 3. Loop 行为

`AgentLoopRunner` 的循环规则如下:

1. 将当前 `Message` 发给 `LmChatSession.sendMessageAsync()`。
2. 收集模型流式输出:
   - `content` 变为 `TextDelta`。
   - `channels["thought"]` 变为 `ThoughtDelta`。
   - `toolCalls` 变为 `ToolCallsReceived`。
3. 若本轮没有工具调用，发送 `Completed` 并退出。
4. 若工具轮数达到 `AgentLoopConfig.maxToolTurns`，发送 `MaxTurnsReached` 并退出。
5. 逐个执行工具:
   - 执行前发送 `ToolStarted`。
   - 通过 `AgentToolExecutor.executeTool()` 获取结构化结果。
   - 通过 `ToolExecutionResult.toJson()` 直接构造 `ToolResponse.response: JsonObject`。
   - 执行后发送 `ToolFinished`。
6. 用所有工具结果构造 `Message.tool(responses)`，回到第 1 步。

默认最大工具轮数为 10，与旧实现保持一致。

## 4. 工具注册表

当前暴露给模型的工具只有真实可执行项:

- `analyzeUrl`: 对 HTTP/HTTPS URL 发起请求，返回 URL 分析、请求、响应头、状态码、耗时和内容预览。
- `searchWeb`: 使用 Bing 搜索并返回结构化搜索结果，可选抓取结果页正文。

`runJs` 的 QuickJS 实现仍保留在代码中，但当前未注册，因此不会进入 tools schema，调用时按禁用工具返回结构化失败。

以下旧占位工具不再进入 tools schema:

- `loadSkill`
- `runMcpTool`
- `runIntent`

如果历史上下文或模型仍调用这些禁用工具，`AgentTools.executeTool()` 会返回结构化失败，而不是返回伪成功。

## 5. 结构化工具结果

所有工具结果统一序列化为 JSON:

```json
{
  "success": true,
  "tool": "searchWeb",
  "data": {},
  "error": null,
  "metadata": {
    "startedAtMillis": 0,
    "completedAtMillis": 0,
    "durationMs": 0
  }
}
```

该 `JsonObject` 同时用于模型回灌和聊天消息 `toolResponses`；repository 只在写入 Room TEXT 列与
`chat_tool_logs.response` 审计列时执行一次 JSON 编码。运行时不提供字符串结果 API，也不在历史
重放阶段重新解析嵌套 JSON。

## 6. ViewModel 边界

`ChatViewModel.getTextTalkerResponse()` 不再直接执行工具循环。它现在只负责:

- 创建 `AgentLoopRunner`。
- 消费 `AgentLoopEvent` 更新聊天 UI。
- 在工具开始时写入 `chat_tool_logs(status = "running")`。
- 在工具结束时写入 `completed` 或 `failed`。
- 在最终助手消息 metadata 中记录:
  - `agent_turn_count`
  - `agent_transition`

这两个字段用于会话回放、问题排查和防止后续任务误判 agent 当前状态。

## 7. 已知限制

- `ToolCall` 仍缺少 native 层透传的 tool call id，同轮同名工具结果仍依赖顺序匹配。
- 尚未实现工具权限确认、域名策略、SSRF 完整防护和网络超时策略。
- `runJs` 当前未注册；若未来重新启用，应先改为“已注册脚本”执行模型并补齐权限确认。
- 尚未实现上下文压缩、token budget 恢复、工具结果摘要和长会话重放。
- MCP、Skill、Intent 仍未接入真实系统，因此当前不暴露给模型。

## 8. 验证

- `:composeApp:compileKotlinMetadata`
- `:composeApp:desktopTest`

新增测试覆盖:

- 工具 schema 不暴露禁用占位工具。
- 禁用工具返回结构化失败。
- 空 JS 在执行前失败。
- agent loop 在无工具调用时结束。
- agent loop 在工具调用后将 `Message.tool()` 回灌给模型并继续到最终完成。
