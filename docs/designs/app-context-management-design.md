# App 上下文管理架构设计规范

## 1. 概述与设计目标

在端侧移动设备（Android / iOS / Desktop）上运行大语言模型（如基于 LiteRT-LM 引擎）时，受限于设备内存（RAM/VRAM）、算力及功耗，**上下文（Context）与 KV Cache 的管理策略直接决定了推理首字延迟（TTFT）、生成吞吐（TPS）、内存峰值与多轮交互体验**。

本设计基于 **LiteRT-LM 运行时特性**（增量差分模板渲染、KV Cache 检查点与回滚、通道过滤、前缀缓存、写时复制等），结合 App 业务场景，针对 **常规会话模式（Chat Mode）** 与 **AIGC 结构化生成模式（Structured Generation Mode）** 的本质差异，构建统一、高效且资源可控的上下文管理架构。

```
                              ┌───────────────────────────────────┐
                              │       App ViewModel / UI 层       │
                              └─────────────────┬─────────────────┘
                                                │
                                                ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                    上下文调度与策略管理器 (ContextCoordinator)                   │
├──────────────────────────────────────────────────┬───────────────────────────────────────────────┤
│             常规会话模式 (Chat Mode)             │          AIGC 结构化生成模式 (AIGC Mode)      │
│  - 模式: DEFAULT / Agent Loop                    │  - 模式: SVG_IMAGE / CHIPTUNE_MML / LOTTIE    │
│  - 特性: 多轮长历史、思考链过滤、滑动摘要、Branch │  - 特性: 单轮/少轮强约束、零历史污染、快速重试│
└──────────────────────────────────────────────────┴───────────────────────────────────────────────┘
                                                │
                                                ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                      LiteRT-LM 原生桥接与管理层                                  │
│  - LmEngine (模型权重与静态内存)                 - LmConversation / Session (KV Cache 实例)      │
│  - Checkpoint & Rewind (KV 回滚)                 - Channel Content Filtering (思考过程剔除)      │
│  - Differential Rendering (增量差分 Prefill)     - Constrained Decoding (LLGuidance 语法约束)    │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 模式对比与需求矩阵

| 维度 | 常规会话模式 (`DEFAULT`) | AIGC 结构化生成模式 (`SVG_IMAGE` / `CHIPTUNE_BGM_MML` / `LOTTIE_ANIMATION`) |
| :--- | :--- | :--- |
| **交互形态** | 多轮持续交互，强依赖前文语境 | 单轮生成或针对特定目标的多轮局部微调 |
| **KV Cache 策略** | 累积追加 + 思考链回滚 + 滑窗压缩 | 独立隔离 / 单次生成后即弃或重置，防止污染主会话 |
| **System Prompt** | 通用助手人格、工具定义（Tools） | 极长且严格的 DSL/Schema 规范、few-shot 样本 |
| **前缀预热 (Prefill)** | `prefillPrefaceOnInit = true` | 针对专用 System Instruction 做前缀固化 |
| **约束解码** | 通常关闭或仅在 Tool Call 时局部开启 | 当前桥接关闭；待 request-level JSON Schema / Grammar 真正绑定后再启用 |
| **Token 预算分配** | 平衡输入与输出（如 Input 70%, Output 30%） | **高输出预算（Max Output Tokens 占比极高）** |
| **“重新生成”处理** | 回滚上一轮 Checkpoint，保留更早前文 | 丢弃旧输出，重新单轮 Prefill 或错误反馈微调 |

---

## 3. 常规会话模式（Chat Mode）上下文管理

### 3.1 增量差分 Prefill 与会话累积
* **机制**：利用 LiteRT-LM `Conversation` 的两次渲染求差分机制（Differential Rendering），每次用户发送新消息时，只计算并 Prefill 新增的 Delta 文本，底层 KV Cache 保持递增，避免全量重新 Prefill。
* **冷启动预热**：创建 `Conversation` 时启用 `prefillPrefaceOnInit = true`，在后台预先将 `systemInstruction` 和 `tools` 注入 KV Cache，使首轮对话首字延迟（TTFT）降低 50% 以上。

### 3.2 思考链（Thinking/Reasoning）的上下文瘦身
* **痛点**：深度推理模型（如 DeepSeek-R1、Qwen-Thinking）单轮产生大量思维链 Token（`<think>...</think>`），若留在 KV Cache 中会导致 2~3 轮即耗尽 `max_num_tokens`。
* **设计方案**：
  1. 开启 `filter_channel_content_from_kv_cache = true`；
  2. 每一轮模型生成时，思考内容通过 `Channel("thinking", "<think>", "</think>")` 实时流式派发给 UI 独立存储并折叠展示；
  3. 当用户发送下一轮消息时，底层自动触发 `RewindToCheckpoint(kChannelContentCheckpoint)`，将 KV Cache 精确回滚至上一轮用户消息结束处，**在底层物理剔除思考 Token，而在 UI 数据库保留完整记录**。

```
[用户提问 1] ──> [模型思考 (Channel: 暂留 KV)] ──> [模型正式回答 1]
                                                          │
  ┌───────────────────────────────────────────────────────┘
  ▼ 下一轮用户输入到达时自动 Rewind
[用户提问 1] ──> [模型正式回答 1] ──> [用户提问 2]  (KV Cache 中思考 Token 被抹除)
```

### 3.3 检查点（Checkpoint）与多分支/重试
* **重新生成（Regenerate）**：
  * 无需销毁会话或重跑全部历史；
  * 调用会话回滚接口恢复至当前轮次提问前的 Step，直接触发 `runDecode()` 重新采样生成。
* **分支对话（Branching/Tree Chat）**：
  * 利用 `SessionAdvanced::Clone()` 的 **Copy-on-Write (COW)** 机制瞬时派生新分支，共享主干历史的 KV Cache 内存。

### 3.4 上下文超限防护（Context Overflow Strategy）
* **动态读取上限**：启动前通过 `LiteRtLmModelMetadata` 解析 `.litertlm` Header 中的 `max_num_tokens`（如 8192）。
* **水位监控与分级策略**：
  1. **安全区（< 70%）**：正常增量追加。
  2. **预警区（70% ~ 85%）**：UI 展示上下文容量指示器，建议精简对话。
  3. **超限保护（> 85%）**：触发**滚动压缩策略**：
     * 保持 System Instruction 与最新 $K$ 轮（如最近 4 轮）完整对话；
     * 将早期历史在后台启动轻量会话生成结构化摘要（Summary）；
     * 重建 `LmConversation` 并将摘要作为初始上下文载入，平滑重置 KV Cache。

---

## 4. AIGC 结构化生成模式（Structured Generation Mode）上下文管理

结构化生成模式包含 **SVG 矢量图像生成**、**8-bit 芯片音乐 MML**、**Lottie 复杂动画 JSON** 等。

### 4.1 独立上下文沙箱与零污染
* **隔离原则**：AIGC 生成严禁复用常规聊天的上下文对象。每次切换到 AIGC 模式或执行独立生成任务时，使用专用的 `LmConversation` 实例。
* **生命周期隔离**：
  * 生成完成后，产出的代码（如 SVG 源码、MML 序列、Lottie JSON）只沉淀为业务消息中的媒体/结构化 Payload；
  * 不将冗长生成代码（可能包含数千 Token）无休止地堆叠在后续对话的 KV Cache 中。

### 4.2 专用 System Prompt 预热与语法约束边界
* **当前桥接边界**：
  * Kotlin/JNI 的 `enableConversationConstrainedDecoding` 布尔值只会启用基于 Tool Description 的 tool-call 约束，并不会把 `ContextStrategy.StructuredGeneration.schemaOrGrammar` 绑定到本次请求。
  * Structured slot 为避免模型调用普通聊天工具会传入空工具列表 `[]`。在这种条件下开启上述布尔值会进入没有可用工具契约的约束路径，实测表现为首轮只产生 `"{"`、换行后即结束。
  * 因此当前实现必须保持 `enableConstrainedDecoding = false`，使用专用 System Instruction、单请求会话隔离以及业务 parser/sanitizer 做结构校验。只有在 request-level `ConstraintProvider` / JSON Schema / EBNF 已真实接入且有测试覆盖后，才允许开启语法约束。
* **专用前缀固化**：
  * AIGC 模式拥有独立的 DSL 规范提示词（如 `SVG_IMAGE_SYSTEM_INSTRUCTION`、`LOTTIE_ANIMATION_SYSTEM_INSTRUCTION`）；
  * 通过 `prefillPrefaceOnInit = true` 将专用规则一次性 Prefill 进该模式专属的会话缓存中。

### 4.3 迭代微调与错误重试（Iterative Refinement without KV Bloat）
当生成的结构化内容校验失败（如 SVG 解析语法错误、Lottie 缺少关键图层属性）：

1. **单轮自修复（Self-Correction）**：
   * 向生成会话注入一条 `user` 修正提示（例如：`"生成的 Lottie JSON 缺少 assets 字段，请修复并重新输出完整代码"`）；
   * 仅在当前 AIGC 沙箱内进行单轮修复。
2. **多轮重置（Fresh Retry）**：
   * 若重试 2 次仍失败，直接释放当前 AIGC `Conversation`，以全新状态重建会话，避免脏上下文干扰模型注意力。

### 4.4 高输出 Token 预算与流式解析拦截
* **Token 预算倾斜**：
  * 普通会话：限制 `maxOutputToken = 1024`，防止模型过长复读；
  * AIGC 模式：放宽 `maxOutputToken = 4096` 或 `maxNumTokens - promptTokens`，确保完整输出大型 Lottie JSON 或密集 MML 代码。
* **增量语法探测（Streaming Validator）**：
  * 配合 `sendMessageAsync`，在流式接收 Token 时进行前缀探测。一旦探测到不可恢复的结构性截断或错误，立即触发 `cancelProcess()` 终止生成，节省电量与算力。

---

## 5. 架构实现与核心接口设计

### 5.1 上下文策略配置定义

```kotlin
/**
 * 上下文运行策略
 */
sealed interface ContextStrategy {
    val prefillPrefaceOnInit: Boolean
    val filterChannelContent: Boolean
    val maxOutputTokens: Int?
    val enableConstrainedDecoding: Boolean
    
    /** 常规多轮会话策略 */
    data class ChatSession(
        override val prefillPrefaceOnInit: Boolean = true,
        override val filterChannelContent: Boolean = true,
        override val maxOutputTokens: Int? = 1024,
        override val enableConstrainedDecoding: Boolean = false,
        val historyRetainWindow: Int = 10,
        val autoSummarizeThresholdRatio: Float = 0.85f
    ) : ContextStrategy

    /** AIGC 结构化生成策略 */
    data class StructuredGeneration(
        val schemaOrGrammar: String? = null,
        override val prefillPrefaceOnInit: Boolean = true,
        override val filterChannelContent: Boolean = false,
        override val maxOutputTokens: Int? = 4096,
        // 当前 JNI 开关是 tool-call 约束，不能绑定 schemaOrGrammar。
        override val enableConstrainedDecoding: Boolean = false,
        val maxRetryCount: Int = 2
    ) : ContextStrategy
}
```

### 5.2 上下文调度管理器（ContextCoordinator）

```kotlin
class ContextCoordinator(
    private val engine: LmEngine,
    private val metadata: LiteRtLmModelMetadata?
) {
    private var activeChatConversation: LmConversation? = null
    private var activeAigcConversation: LmConversation? = null
    
    /**
     * 根据 SessionMode 获取或创建适配的 Conversation
     */
    suspend fun getOrCreateConversation(
        mode: ChatSessionMode,
        systemInstruction: String,
        toolsJson: String = "[]"
    ): LmConversation {
        return when {
            mode.isStructuredGenerationMode() -> {
                // AIGC 模式：使用独立沙箱与大 Token 预算；当前不启用空工具约束
                activeAigcConversation?.close()
                val aigcStrategy = ContextStrategy.StructuredGeneration()
                val conv = engine.createConversation(
                    systemInstruction = systemInstruction,
                    initialMessages = emptyList(),
                    toolsDescriptionJsonString = "[]",
                    enableConversationConstrainedDecoding = aigcStrategy.enableConstrainedDecoding,
                    // 绑定 AIGC 专用参数
                )
                activeAigcConversation = conv
                conv
            }
            else -> {
                // 常规会话模式：复用或重建长期上下文，开启通道过滤
                if (activeChatConversation == null) {
                    val chatStrategy = ContextStrategy.ChatSession()
                    activeChatConversation = engine.createConversation(
                        systemInstruction = systemInstruction,
                        initialMessages = emptyList(),
                        toolsDescriptionJsonString = toolsJson,
                        enableConversationConstrainedDecoding = chatStrategy.enableConstrainedDecoding
                    )
                }
                activeChatConversation!!
            }
        }
    }

    /**
     * 模式切换或会话退出时的上下文资源回收
     */
    fun onModeSwitched(targetMode: ChatSessionMode) {
        if (!targetMode.isStructuredGenerationMode()) {
            // 切回常规会话时，主动释放 AIGC 占用的瞬态内存
            activeAigcConversation?.close()
            activeAigcConversation = null
        }
    }

    fun releaseAll() {
        activeChatConversation?.close()
        activeChatConversation = null
        activeAigcConversation?.close()
        activeAigcConversation = null
    }
}
```

---

## 6. 异常控制与内存安全约束

1. **模型上下文硬上限（Hardware Boundary）**：
   * 必须确保 `current_context_tokens + max_output_tokens <= metadata.max_num_tokens`。若超出硬上限，在触发推理前主动进行截断或报错提示，严禁引发底层 NPU/GPU 崩溃。
2. **多模态 Token 预算隔离**：
   * 图像与音频占用独立 Visual Token 预算（通过 `visualTokenBudget` 限制，如 576 tokens/image）；
   * 在 AIGC 模式下，默认屏蔽或严格限制多模态输入，确保将最大显存与 Token 预算留给输出代码生成。
3. **取消状态安全（Cancellation Safety）**：
   * 用户打断生成时，调用 `cancelProcess()` 后需注意：若底层会话状态损坏，调度器应捕获异常并自动重建 `Conversation`，以干净的 History 恢复会话。

---

## 7. 实现落地（2026-08-20，2026-08-23 修订）

本设计已从 ViewModel 内部约定升级为可执行的运行时边界，落地位置如下：

| 设计能力 | 实现 | 关键行为 |
| --- | --- | --- |
| 模式策略 | `ContextStrategy` | DEFAULT 使用 1024 输出预算、通道过滤与普通对话；SVG/BGM/Lottie 使用 4096 输出预算与独立沙箱。当前 Structured 不启用未绑定 Schema 的 tool-call constrained decoding。 |
| 会话所有权 | `ContextCoordinator` | Chat 与 Structured 各自拥有 KV slot；session id、模式或 system instruction 变化时重建，禁止跨模式污染。 |
| 历史恢复 | `ContextTranscript` | 打开历史或重建会话时，将持久化消息重放为 LiteRT-LM `Message`，不再出现“UI 有历史、KV 为空”的分裂状态。 |
| 超限保护 | `ContextBudgetPolicy` + ViewModel preflight | 使用原生 `GetTokenCount()` 检查 `used + incoming + reservedOutput`；达到 85% 或硬上限前，以旧消息摘要 + 最近 turns 重建 KV，持久化历史不删除。 |
| 原生参数 | `LiteRtLmJni` expect/actual | Android/Desktop 传递 `prefillPrefaceOnInit` 与每轮 `maxOutputToken`，并暴露 KV token count；iOS 通过 C API 的 optional args 传递输出预算与 token count。 |
| UI 可观测性 | `ConversationContextState` | 暴露已用 token、容量、预计 token、比例、预算等级和压缩次数，供上下文头部显示或诊断。 |

### 7.1 兼容性边界

LiteRT-LM 的 Android/Desktop JNI 接口已经提供 prefill 开关；当前 iOS C API 仅提供 system/messages/prompt/filter 等配置，没有 `prefill_preface_on_init` setter，因此 iOS actual 保留参数但使用 C API 默认值。若后续要让 iOS 也强制预热 system preface，需要先在 `cpp/lite-rt-lm/c/conversation.h/.cc` 增加该 C setter，再接入 cinterop；本轮不修改已有 native submodule 工作树。

### 7.2 资源生命周期

`ChatViewModel` 不再直接持有 `LmConversation`/`LmEngine`。模型初始化、模式切换、后端降级、取消和 ViewModel 销毁统一通过 `ContextCoordinator` 回收；CPU fallback 会创建全新的 engine 与 conversation，并从当前 durable transcript 重建上下文。
### 7.3 Structured 会话恢复约束

结构化生成模式（SVG、BGM、Lottie）虽然保留历史消息用于 UI 展示和持久化，但创建或恢复 `LmConversation` 时必须传入空的 `initialMessages`。历史生成 JSON 不能作为 KV Cache 中的对话历史，否则可能污染下一次生成并导致不完整 JSON 前缀。普通 DEFAULT 会话仍通过 `ContextTranscript` 回放历史；该约束同时适用于普通重建和上下文压缩重建。
### 7.4 AIGC 多轮请求隔离

AIGC 每次请求前都必须以 `forceRecreate = true` 创建新的 `LmConversation`，即使属于同一个持久化会话。历史输出只用于 UI 和持久化，不得让上一轮 JSON 留在下一轮生成的 KV Cache 中。

### 7.5 首轮发送与 SystemInstruction 切换事务

上下文切换遵循 `deactivate old slot → select context → clear/reload durable transcript → create conversation → mark isApplied → READY` 的顺序：

1. `ContextCoordinator.onModeSwitched()` 先清空 active slot，旧 conversation 不再可发送，防止 Library 跳转后快速点击把请求送入旧 SystemInstruction。
2. 应用新的 SystemInstruction 时先清空当前 UI/数据库消息，再强制重建 native conversation，避免“UI 已清空但旧消息已进入 KV”的隐形历史。
3. UI 和 `ChatViewModel.sendMessage()` 都只在 `ConversationContextState.isApplied == true`、native conversation 存在且运行态为 `READY` 时接受发送。
4. 当前 user message 与生成中的 assistant placeholder 使用同一 `turn_id`。重建、压缩或 CPU fallback 回放时，`ContextTranscript` 排除整个 in-flight turn，防止当前 prompt 被 Prefill 后又由 Agent Loop 重发一次。
5. 取消、空响应、解析失败的 turn 标记为 `exclude_from_context`；它们保留为可诊断的持久化记录，但不再污染后续 KV Cache。
6. native 回调使用有序的无界 channel 缓冲；其实际上限仍由 `maxOutputTokens` 约束。`onDone` 关闭流后会排空已接收 chunk，避免尾部 token 因异步转发竞态而丢失。
7. 只有 `{`、`[]` 或纯标点的终止输出视为不可用响应，展示可重试错误并以干净 transcript 重建会话。

### 7.6 生成取消屏障

`ChatViewModel` 将生成取消作为会话切换前的同步屏障：

1. 所有停止请求通过同一个 `Mutex` 串行化，首次请求会立即将 `isGenerating` 和 `isInferenceOn` 置为 `false`，后续重复请求不会再次清理消息。
2. 仅定位带有 `is_generating=true` 的 assistant 占位消息，并通过相同 `turn_id` 定位对应 user 消息；手动停止时将这一完整 turn 从 UI 与数据库删除，不再假设列表最后一项一定属于当前生成。
3. 调用 `ContextCoordinator.cancelActive()` 后，必须通过 `responseGenerationJob.cancelAndJoin()` 等待旧推理协程结束，才允许初始化模型、应用设置、打开会话或重建 conversation。
4. Compose 状态和消息列表只在 Main dispatcher 修改；取消 turn 的删除与持久化只在 IO dispatcher 执行。
5. LiteRT-LM 不支持取消后继续复用同一个 conversation。若停止操作本身未触发其他上下文切换，则旧任务退出并删除取消 turn 后，必须从清理后的 durable transcript 强制重建 conversation，再恢复为 `READY`；重建期间使用 `APPLYING_CONTEXT`，失败则进入 `ERROR`。
6. Chat 输入区在 `isGenerating=true` 时将发送按钮切换为停止按钮，点击事件必须直接调用 `ChatViewModel.stopGeneration()`，不得再次进入 `sendMessage()` 的就绪状态校验分支。
7. Chat 与 Benchmark 共用同一个 `LmInferenceGate`：两者在任何可挂起操作之前必须原子获取推理租约，并持有到 native 生成 Job 完全结束。独立 `LmConversation` 只隔离 KV 上下文，不代表底层 `LmEngine` 支持并发执行。
8. Benchmark 运行期间将 `LlmEngineStatus` 置为 `GENERATING`，使 Chat 入口不再误判为 `READY`；模型重载、上下文切换和会话切换通过统一停止屏障取消并 `join` Benchmark Job，然后才能回收 engine/conversation。

### 7.7 LLM 运行态与 Gris 水彩反馈

`LlmEngineStatus` 描述当前 native engine/conversation 的瞬时生命周期，不写入会话数据库。导航栏、Library 状态 Chip 与 Chat 上下文头共享同一个状态源和 `GrisWatercolorStatusIndicator`：

| 状态 | 含义 | Gris 动效语义 |
| --- | --- | --- |
| `UNINITIALIZED` | 尚未加载模型 | Slate 低透明静泊 |
| `INITIALIZING` | 创建并初始化 engine | Dusty Blue 轨道式晕染 |
| `APPLYING_CONTEXT` | 切换/重建 SystemInstruction 与 conversation | Sage / Blue 双层扩散 |
| `READY` | 上下文已提交，可发送 | Sage 慢呼吸 |
| `GENERATING` | 正在流式推理 | Blue / Sage 较快流动 |
| `ERROR` | 初始化、恢复或生成失败 | Error / Slate 低频潮汐，不闪烁 |

颜色、间距、形状与玻璃表面均来自 `AppTheme` token；动画只表达状态变化，不替代本地化文字标签。

### 7.8 工具历史重放契约

Room 继续在同一条 assistant 消息中保存最终正文、有序 `toolCalls` 与有序 `toolResponses`；
公共模型中的 `PersistentToolCall.arguments` 与 `PersistentToolResponse.response` 均为
`JsonObject`。Room 的 TEXT 列只在 repository 边界编码整个列表，业务层和 native 边界不再持有或
解析嵌套 JSON 字符串。`ContextTranscript` 按以下规则恢复原生消息：

1. 仅当最终 assistant 正文非空、调用与响应数量相同且名称逐项匹配时，才认为工具交换完整。
2. 完整交换固定重放为 `model(tool_calls) → tool(tool_response) → model(final text)`，不得把
   最终正文与 `tool_calls` 合并到同一条 model 消息，也不得把响应排在最终正文之后。
3. 工具参数与响应直接从公共模型映射为 LiteRT-LM 的 `JsonObject`，不得增加字符串化 API、
   旧响应解析器或类型猜测 fallback。
4. 元数据缺失、数量不等、名称错位或缺少最终正文时，不向 native 注入残缺工具交换；有最终正文时
   只回放该正文，以保证恢复后的角色序列有效。
5. `LmEngine.createConversation()` 在调用 `nativeCreateConversation` 前递归清洗完整 preface，覆盖
   system instruction、普通历史正文以及嵌套 tool response，规则与后续 send 路径一致。
6. Room Schema v3 是工具协议断代点：v1/v2 数据库升级时直接删除全部旧表并按 v3 重建；旧会话
   不参与转换，仓库也不保留 v1/v2 migration 与 schema 快照。
