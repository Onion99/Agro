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
| **约束解码** | 通常关闭或仅在 Tool Call 时局部开启 | **强制开启（Constrained Decoding / JSON Schema）** |
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

### 4.2 专用 System Prompt 预热与语法约束绑定
* **Schema / Grammar 绑定**：
  * 在创建 AIGC 会话时，强制开启 `enableConversationConstrainedDecoding = true`，并将对应的 JSON Schema / EBNF 语法规则传入 `ConstraintProvider`（LLGuidance），确保生成的 JSON 或代码 100% 符合解析器格式。
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
        override val enableConstrainedDecoding: Boolean = true,
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
                // AIGC 模式：使用独立沙箱，配置强约束与大 Token 预算
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

## 7. 本轮实现落地（2026-08-20）

本设计已从 ViewModel 内部约定升级为可执行的运行时边界，落地位置如下：

| 设计能力 | 实现 | 关键行为 |
| --- | --- | --- |
| 模式策略 | `ContextStrategy` | DEFAULT 使用 1024 输出预算、通道过滤与普通对话；SVG/BGM/Lottie 使用 4096 输出预算、独立沙箱与约束解码。 |
| 会话所有权 | `ContextCoordinator` | Chat 与 Structured 各自拥有 KV slot；session id、模式或 system instruction 变化时重建，禁止跨模式污染。 |
| 历史恢复 | `ContextTranscript` | 打开历史或重建会话时，将持久化消息重放为 LiteRT-LM `Message`，不再出现“UI 有历史、KV 为空”的分裂状态。 |
| 超限保护 | `ContextBudgetPolicy` + ViewModel preflight | 使用原生 `GetTokenCount()` 检查 `used + incoming + reservedOutput`；达到 85% 或硬上限前，以旧消息摘要 + 最近 turns 重建 KV，持久化历史不删除。 |
| 原生参数 | `LiteRtLmJni` expect/actual | Android/Desktop 传递 `prefillPrefaceOnInit` 与每轮 `maxOutputToken`，并暴露 KV token count；iOS 通过 C API 的 optional args 传递输出预算与 token count。 |
| UI 可观测性 | `ConversationContextState` | 暴露已用 token、容量、预计 token、比例、预算等级和压缩次数，供上下文头部显示或诊断。 |

### 7.1 兼容性边界

LiteRT-LM 的 Android/Desktop JNI 接口已经提供 prefill 开关；当前 iOS C API 仅提供 system/messages/prompt/filter 等配置，没有 `prefill_preface_on_init` setter，因此 iOS actual 保留参数但使用 C API 默认值。若后续要让 iOS 也强制预热 system preface，需要先在 `cpp/lite-rt-lm/c/conversation.h/.cc` 增加该 C setter，再接入 cinterop；本轮不修改已有 native submodule 工作树。

### 7.2 资源生命周期

`ChatViewModel` 不再直接持有 `LmConversation`/`LmEngine`。模型初始化、模式切换、后端降级、取消和 ViewModel 销毁统一通过 `ContextCoordinator` 回收；CPU fallback 会创建全新的 engine 与 conversation，并从当前 durable transcript 重建上下文。
