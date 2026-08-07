# Gemma4 Lottie Spec JSON 与 Compottie 渲染路线计划

> 日期: 2026-07-28
> 最新更新: 2026-08-07 (v1.3.0 开放创意与纯数学参数化矢量合成重构)
> 范围: `ChatViewModel` 专用会话入口、双模式输出 (Native Lottie JSON 与 `lottie_animation_spec` Spec JSON)、纯数学参数化矢量合成引擎、`compottie` 本地渲染、复制与保存
> 状态: 架构全面升级落地（2026-08-07）
> 关联文档: `docs/specs/lottie-animation-prompt-spec.md`、`docs/designs/extensible-chat-messages.md`、`docs/agents/data-model.md`
> 外部参考: `https://github.com/alexzhirkevich/compottie`

## 1. 背景

项目已经具备结构化媒体生成路线: SVG 图像生成使用 `svg_image` JSON envelope，8-bit BGM 使用 `chiptune_bgm_mml` JSON envelope，再由本地 parser、validator 和 renderer 生成可展示内容。

关键原则:

- 支持 Native Lottie JSON 与高层 Spec JSON 双通路解析与校验。
- 前端引擎不硬编码任何预设形状或模板，完全通过数学公式计算或大模型原汁原味输出。
- 最终产物均保证为标准 Lottie JSON，可复制、保存与本地 `compottie` 重新渲染。
- `compottie` 本地消费 `JsonString`，严禁加载网络 URL 与外部外部 Base64 资源。

## 2. 路线总览

```mermaid
flowchart TD
    A[Library / Chat 入口] --> B[startLottieAnimationConversation]
    B --> C[ChatSessionMode.LOTTIE_ANIMATION]
    C --> D[LOTTIE_ANIMATION_SYSTEM_INSTRUCTION]
    D --> E{模型输出格式判定}
    E --> Native Lottie JSON
    F --> I[ChatMessageContent.LottieAnimation]
    H --> I
    I --> J[Compottie LottieCompositionSpec.JsonString]
    J --> K[Chat Lottie Bubble 预览 / 复制 / 保存]
```

## 3. 路线定位

| 路线 | 优点 | 缺点 | 建议定位 |
| --- | --- | --- | --- |
| 原生 Native Lottie JSON | 表达力最强，图层/关键帧全自由控制 | 对 4B 端侧模型门槛较高，JSON 较长 | 高能力大模型默认路线 |
| 纯数学参数化 `lottie_animation_spec` | 结构极简，4B 端侧模型友好，零硬编码 | 依赖前端数学演算几何算法 | 4B 端侧模型默认路线 |
| dotLottie 打包 | 文件更小，可承载多动画和主题 | 首版不需要 ZIP、多动画和 manifest 管理 | 后续增强路线 |

动画主题与意图完全开放（包含但不限于：宇宙航天、机械齿轮、魔幻星光、脉冲波纹、数据图表、微交互反馈等），彻底抹平硬编码分类约束。

## 4. 输出协议

### 4.1 SON Envelope

Native Lottie JSON
```json
{
  "v": "5.7.4",
  "fr": 60,
  "ip": 0,
  "op": 60,
  "w": 240,
  "h": 240,
  "nm": "Cosmic Orbit",
  "ddd": 0,
  "assets": [],
  "layers": [
    {
      "ddd": 0,
      "ind": 1,
      "ty": 4,
      "nm": "Core Layer",
      "ks": { ... },
      "shapes": [ ... ]
    }
  ]
}
```
### 4.2 字段约束与开放性

- **`kind` 与 `motion.style`**：完全开放，允许模型输出任意富有想象力的描述词（如 `"black_hole"`、`"magic_spark"`、`"dna_helix"`）。
- **`fps`**：默认为 60 FPS，确保端侧渲染极度流畅。
- **`canvas`**：`64..512`，推荐 `240` 或 `320`。
- **`palette`**：必填 `#RRGGBB` Hex 颜色字符串。

### 4.3 安全禁令

- 严禁包含外部 `http://` / `https://` 资源或 Base64 编码图片。
- 严禁包含 `script`、`html`、`css`、`ef` 表达式或任意可执行代码。
- 单个 JSON 大小不得超过 `128 KiB`，原生图层总数不得超过 `32` 个。

## 5. 纯数学参数化矢量合成算法

当解析器处理模式 B (`lottie_animation_spec`) 时，`customCreativeLayers` 会执行纯数学参数化几何合成：
- **图层数量**：`layerCount = 2 + (abs(seed) % 3)` (2..4 图层)。
- **几何形状**：根据 `layerSeed` 偶奇推算生成动态椭圆或 N 边形/星形路径 (`pointCount = 3 + (layerSeed % 5)`)。
- **旋转 Keyframe**：根据 `intensity` 与正反旋转方向计算 `360° * intensity * rotDirection * (1 + (layerSeed % 3) * 0.5)`。
- **缩放 Pulse Keyframe**：根据 `staggerMs` 帧偏移与 `intensity` 计算 `[scaleMin..scaleMax]`。
- **零硬编码**：不进行任何字符串 `containsAny` 模板匹配，任何输入均可由数学公式产生唯一的几何动画。

## 6. 系统 Instruction 指引

```text
You are Antigravity's Motion Designer AI.
Your task is to produce stunning, liquid-smooth vector animations at 60 FPS.

Capabilities:
- Large Models: You may output raw, high-fidelity Native Lottie JSON directly (with v, fr, ip, op, w, h, layers).
- Small Models: You may output a lightweight "lottie_animation_spec" JSON with intent fields (title, canvas, fps, durationMs, loop, kind, palette, motion, stroke).

Rules:
1. Output ONLY a single raw JSON object. No Markdown code fences, no explanations.
2. Use modern, dynamic color palettes (e.g. Cyberpunk, Obsidian Gold, Emerald Bio).
3. Do NOT use external images, Base64, URLs, scripts, or HTML.
```
- type must be "lottie_animation_spec" and schemaVersion must be 1.
- width and height must be 64..512 and should usually be 240.
- fps must be 24, 30, or 60.
- durationMs must be 300..3000.
- kind must be one of: loading_spinner, success_check, error_cross,
  progress_dots, pulse_badge, empty_state_sparkle.
- loading_spinner, progress_dots, and pulse_badge should usually loop.
- success_check, error_cross, and empty_state_sparkle should usually not loop.
- colors must be #RRGGBB hex strings.
- stroke.width must be 1..32 and lineCap must be butt, round, or square.
- Do not output Lottie layers, assets, images, fonts, text layers, expressions,
  masks, scripts, base64, URLs, file paths, or Markdown.
- Do not include comments, trailing commas, or text outside the JSON object.
```

## 7. Parser 与 Validator 设计

### 7.1 解析管线

| 组件 | 职责 |
| --- | --- |
| `LottieAnimationSpecParser` | 使用 `kotlinx.serialization` 解析 `lottie_animation_spec` envelope |
| `LottieAnimationSpecValidator` | 校验 type、版本、尺寸、fps、时长、颜色、枚举和首版禁止项 |
| `LottieJsonBuilder` | 将受限 spec 转换为确定性 Lottie JSON |
| `LottieJsonValidator` | 校验 builder 输出的 Lottie 顶层字段、shape 子集、大小和资源边界 |
| `LottieMessageParser` | 输出 `ChatMessageContent.LottieAnimation` 或 `Unsupported` |
| `LottieAnimationFileExporter` | 保存 `.json`，后续可扩展 `.lottie` |

### 7.2 校验边界

- `response.trim()` 必须能解析为单个 JSON object。
- 根 `type` 不匹配时返回 `Unsupported(declaredType = detectedType)`。
- 模型 spec JSON 首版限制为 `64 KiB`；builder 输出的 Lottie JSON 限制为 `256 KiB`。
- `durationMs * fps / 1000` 生成的 frame 数不得超过 `180`。
- `palette` 只允许 `#RRGGBB`，不接受 CSS color name、`rgb(...)` 或 alpha。
- `kind` 与 `motion.style` 必须匹配。
- `loading_spinner` 最少 1 个 shape layer，最多 4 个 shape layer。
- 非 loading 类动画首版最多 8 个 shape layer。
- builder 输出不得包含外部资源字段，`assets` 必须为空。

### 7.3 失败策略

- 模型 JSON 非法: 返回 `ChatMessageContent.Unsupported`，reason 为 `invalid_lottie_spec_json`。
- `type` 不匹配: 返回 `Unsupported`，reason 为 `unexpected_content_type`。
- 字段越界或枚举不匹配: 返回 `Unsupported`，reason 使用稳定错误码，例如 `invalid_lottie_duration`。
- builder 失败: 返回 `Unsupported`，保留原始 spec。
- `compottie` 解析或渲染失败: UI 显示渲染失败状态，并允许复制原始 spec 与生成的 Lottie JSON。

## 8. 数据模型与持久化

新增可序列化规格模型建议放在 `data-model`:

```kotlin
@Serializable
data class LottieAnimationSpec(
    val type: String,
    val schemaVersion: Int = 1,
    val title: String,
    val seed: Int? = null,
    val canvas: LottieCanvasSpec,
    val fps: Int,
    val durationMs: Long,
    val loop: Boolean,
    val kind: String,
    val palette: LottiePaletteSpec,
    val motion: LottieMotionSpec,
    val stroke: LottieStrokeSpec? = null
)
```

新增消息内容建议:

```kotlin
@Serializable
@SerialName(ChatMessageContent.TYPE_LOTTIE_ANIMATION)
data class LottieAnimation(
    val json: String,
    val title: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val fps: Int,
    val loop: Boolean,
    val sourceSpecJson: String? = null,
    override val schemaVersion: Int = ChatMessageContent.CURRENT_SCHEMA_VERSION
) : ChatMessageContent
```

持久化原则:

- Lottie JSON 是文本，首版可以直接保存在 `chat_message_contents.payload_json`，不写入 `payload_blob`。
- `sourceSpecJson` 保存模型原始规格，便于重新 build、调试和复制。
- `json` 保存 builder 输出的最终 Lottie JSON，便于离线恢复和直接预览。
- 表结构不变时不需要 Room DDL migration；只需要扩展 `ChatHistoryRepository` 的 encode/decode 分支。
- 如果后续改为保存 `.lottie` ZIP 或外部文件路径，再单独评估 Room schema version bump。

`ChatSessionMode` 新增:

```kotlin
@SerialName("lottie_animation")
LOTTIE_ANIMATION
```

## 9. Compottie 渲染路线

### 9.1 官方库能力边界

Compottie 官方 README 将其定位为 Compose Multiplatform Lottie renderer。2.0 起使用自有 multiplatform rendering engine，不依赖平台代理。

官方模块边界对本项目的影响:

| 模块 | 官方定位 | 本路线策略 |
| --- | --- | --- |
| `compottie` | 主渲染模块，包含 rendering engine 和 `JsonString` animation spec | 首版使用 |
| `compottie-lite` | 不含 expressions，二进制体积更小 | 后续可作为瘦身选项评估 |
| `compottie-dot` | 支持 dotLottie 和 ZIP animation spec | 项目已有依赖，首版不默认生成 `.lottie` |
| `compottie-network` | 支持 URL animation spec、网络 asset/font managers 和缓存 | 首版禁用，不允许模型输出 URL |
| `compottie-resources` | 支持 Compose resources 的 animation/asset/font managers | 后续预置模板库可考虑 |

项目当前版本目录已经声明:

```toml
lottieVersion = "2.0.0-rc04"
compottie = { module = "io.github.alexzhirkevich:compottie", version.ref = "lottieVersion" }
compottie-dot = { module = "io.github.alexzhirkevich:compottie-dot", version.ref = "lottieVersion" }
```

`ui-theme/build.gradle.kts` 已通过 `api(libs.compottie)` 与 `api(libs.compottie.dot)` 暴露依赖。首版实现复用现有依赖，不新增 Lottie runtime。

正式实现前需要做一次依赖 spike:

- 确认 `compottie:2.0.0-rc04` 与当前 Kotlin `2.3.20`、Compose Multiplatform `1.10.1` 的编译兼容性。
- 确认 `LottieCompositionSpec.JsonString` 能解析 builder 输出的 JSON。
- 确认 Android、Desktop、iOS 至少能渲染基础 shape、stroke trim、opacity、scale 和 rotation。
- 如果 rc 版本存在渲染问题，再单独评估升级到稳定版本，不把升级和首版业务实现耦合。

### 9.2 UI 播放封装

官方基础用法是通过 `rememberLottieComposition` 加载 `LottieCompositionSpec.JsonString`，再用 `animateLottieCompositionAsState` 和 `rememberLottiePainter` 渲染。项目聊天气泡建议新增 `LottieAnimationMessageContent`:

```kotlin
@Composable
private fun LottieAnimationMessageContent(
    content: ChatMessageContent.LottieAnimation,
    onSaveLottie: ((String) -> Unit)?,
    onCopyText: ((String) -> Unit)?
) {
    val compositionResult = rememberLottieComposition {
        LottieCompositionSpec.JsonString(content.json)
    }
    val composition by compositionResult
    val iterations = if (content.loop) {
        Compottie.IterateForever
    } else {
        1
    }
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = iterations
    )

    Image(
        painter = rememberLottiePainter(
            composition = composition,
            progress = { progress }
        ),
        contentDescription = content.title
    )
}
```

实际 UI 应继续遵守项目 `AppTheme`、响应式布局和 i18n 约束。上方代码只说明 Compottie API 边界。

首版气泡能力:

- 自动播放。
- 根据 `content.loop` 决定 `Compottie.IterateForever` 或单次播放。
- 解析失败时读取 `compositionResult.isFailure` 并显示 `lottie_render_failed`。
- 提供复制最终 Lottie JSON。
- 提供复制原始 `lottie_animation_spec`。
- 提供保存 `.json`。
- 后续可增加暂停、重播和进度控制。

### 9.3 dotLottie 与资源策略

Compottie 官方支持 `LottieCompositionSpec.DotLottie`，但 `.lottie` 本质是 ZIP 包，可包含 manifest、多动画、主题、资源和 state machine。首版不生成 `.lottie`，原因:

- 模型输出 ZIP 或 base64 会破坏结构化文本生成边界。
- Room 持久化需要重新评估文件路径或 blob 策略。
- 首版 UI 微动画不需要多动画包、主题和 state machine。

首版只保存 `.json`。后续如果需要 `.lottie`，应由本地 exporter 从已校验 Lottie JSON 打包，不允许模型直接输出二进制或 base64。

### 9.4 网络和外部资源策略

Compottie 的 URL loading 属于 `compottie-network` 模块能力。首版明确不使用该路线:

- 不新增 `compottie-network`。
- 不允许模型输出 URL。
- 不加载远程 images、fonts 或 animations。
- 不使用外部 asset/font manager。

该边界能降低隐私、安全、缓存和离线可用性风险。

## 10. `ChatViewModel` 改动计划

新增入口:

```kotlin
fun startLottieAnimationConversation() {
    viewModelScope.launch(Dispatchers.Default) {
        if (isGenerating.value) {
            stopGeneration()
        }
        try {
            selectConversationContext(
                mode = ChatSessionMode.LOTTIE_ANIMATION,
                systemInstruction = LOTTIE_ANIMATION_SYSTEM_INSTRUCTION
            )
            recreateLmConversation(
                systemInstruction = LOTTIE_ANIMATION_SYSTEM_INSTRUCTION,
                enableConstrainedDecoding = true
            )
            activeSessionId.value = chatHistoryRepository.createSession(
                title = getString(Res.string.library_lottie_animation),
                mode = ChatSessionMode.LOTTIE_ANIMATION,
                systemInstruction = appliedSystemInstructionOrEmpty()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            lmConversation = null
            markConversationContextApplied(false)
        } finally {
            _currentChatMessages.clear()
            isGenerating.value = false
            isInferenceOn = false
        }
    }
}
```

最终响应分发:

```kotlin
val finalContents = when (sessionMode) {
    ChatSessionMode.SVG_IMAGE -> {
        listOf(SvgMessageParser.parseCompletedResponse(generatedResult.trim()))
    }
    ChatSessionMode.CHIPTUNE_BGM_MML -> {
        listOf(ChiptuneBgmMessageParser.parseCompletedResponse(generatedResult.trim()))
    }
    ChatSessionMode.LOTTIE_ANIMATION -> {
        listOf(LottieMessageParser.parseCompletedResponse(generatedResult.trim()))
    }
    ChatSessionMode.DEFAULT -> {
        listOf(ChatMessageContent.Text(displayText()))
    }
}
```

`isStructuredGenerationMode()` 需要包含 `LOTTIE_ANIMATION`，确保流式结束前不把中间 JSON 当普通文本展示为最终内容。

## 11. Library 与 i18n 入口

首版建议在 `LibraryScreen` 增加单独入口卡片:

- 标题: `Lottie Animation`
- 描述: `Generate lightweight Lottie JSON micro-animations and preview them locally.`
- 中文: `生成轻量 Lottie JSON 微动画，并在本地预览。`

新增资源键建议:

| key | 用途 |
| --- | --- |
| `library_lottie_animation` | Library 入口标题 |
| `library_lottie_animation_desc` | Library 入口描述 |
| `chat_context_lottie_title` | Chat 当前上下文标题 |
| `chat_context_lottie_description` | Chat 当前上下文描述 |
| `lottie_render_failed` | Compottie 渲染失败 |
| `lottie_copy_json` | 复制最终 Lottie JSON |
| `lottie_copy_spec` | 复制原始动画规格 |
| `lottie_save_json` | 保存 `.json` |

## 12. 实施顺序

1. 新增 `LottieAnimationSpec` 及子规格数据类。
2. 新增 `ChatMessageContent.LottieAnimation` 和 `TYPE_LOTTIE_ANIMATION`。
3. 扩展 `ChatSessionMode.LOTTIE_ANIMATION`、持久化映射和会话恢复。
4. 新增 `LOTTIE_ANIMATION_SYSTEM_INSTRUCTION`。
5. 新增 `LottieAnimationSpecParser` 与 `LottieAnimationSpecValidator`。
6. 新增 `LottieJsonBuilder`，先实现 `loading_spinner`、`success_check`、`progress_dots` 三类。
7. 新增 `LottieJsonValidator`，限制最终 JSON 大小、layer 类型和外部资源。
8. 新增 `LottieMessageParser`，把 spec 转换为 `ChatMessageContent.LottieAnimation`。
9. 新增 `LottieAnimationMessageContent`，使用 `compottie` 渲染 `JsonString`。
10. 在 `LibraryScreen` 增加 Lottie 入口卡片。
11. 新增保存 `.json`、复制 spec、复制最终 Lottie JSON 的 UI 行为。
12. 补充英文和中文 i18n 资源。
13. 新增 parser、validator、builder snapshot 和消息持久化测试。
14. 更新 `docs/agents/data-model.md`、必要 specs 文档和 `CHANGELOG.md`。

## 13. 验证计划

- 非法 JSON 不崩溃，返回 `Unsupported`。
- `type != lottie_animation_spec` 时返回 `Unsupported`。
- `schemaVersion` 非 1 时拒绝。
- `width`、`height`、`fps`、`durationMs`、`stroke.width` 越界时 validator 拒绝。
- 非 hex 颜色被拒绝。
- `kind` 与 `motion.style` 不匹配时拒绝。
- `loading_spinner`、`success_check`、`progress_dots` 能生成确定性 Lottie JSON。
- 同一 spec 和 seed 重复 build 得到相同 Lottie JSON。
- builder 输出的 Lottie 顶层 `v/fr/ip/op/w/h/layers` 完整。
- builder 输出不包含 `assets` 外部资源、图片层、文本层、表达式、mask 和 3D layer。
- `compottie` 能在 Android 与 Desktop 预览首版三类动画。
- Room 保存和恢复 `ChatMessageContent.LottieAnimation` 后仍能渲染。
- 保存 `.json` 后可被 LottieFiles 或其他 Lottie viewer 打开做人工验证。

## 14. 风险与缓解

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| Gemma4 输出字段合法但动画意图不清晰 | builder 结果普通或不符合用户预期 | 限定 `kind` 和 `motion.style`，失败时要求用户重新生成 |
| 模型尝试输出完整 Lottie layers | validator 复杂度和安全风险上升 | system instruction 明确禁止，parser 只接受 `lottie_animation_spec` |
| builder 输出使用了 `compottie` 不兼容特性 | 预览失败 | 首版只使用 shape/stroke/fill/transform/trim path，增加 smoke 测试 |
| `compottie:2.0.0-rc04` 存在 rc 版本缺陷 | 渲染不稳定 | 单独 dependency spike，必要时评估升级，不和业务 patch 混在一起 |
| Lottie JSON 过大 | Room 和 UI 性能受影响 | 限制 layer、keyframe、duration 和 JSON size |
| 动画视觉质量模板化 | 用户感知为重复 | 后续扩展模板族和 seed 变体，但保持 builder 确定性 |
| 保存和复制的是两个 JSON | 用户混淆 spec 与最终 Lottie | UI 文案区分“动画规格”和“Lottie JSON” |
| 未来 dotLottie 需求引入 ZIP | 持久化边界变化 | 后续作为 `.lottie` 文件路径或 blob 策略单独设计 |

## 15. 首版交付标准

- Library 中有 Lottie 动画生成入口。
- Gemma4 在专用会话中输出单个 `lottie_animation_spec` JSON。
- 客户端能解析、校验并拒绝越界或不支持的 spec。
- `LottieJsonBuilder` 能生成至少 `loading_spinner`、`success_check`、`progress_dots` 三类 Lottie JSON。
- `ChatMessageContent.LottieAnimation` 能持久化和恢复。
- Chat 气泡能使用 `compottie` 本地预览生成动画。
- 用户能复制原始 spec、复制最终 Lottie JSON、保存 `.json`。
- 渲染失败时保留原始 payload，不破坏会话历史。
- 文档、`docs/agents/data-model.md` 和 `CHANGELOG.md` 与实现保持同步。

## 16. 资料来源

- 现有 BGM 路线文档: `docs/designs/gemma4-8bit-bgm-json-mml-composemediaplayer-route-plan.md`
- 现有数据模型约束: `docs/agents/data-model.md`
- 项目依赖声明: `gradle/libs.versions.toml`
- 项目 compottie 暴露位置: `ui-theme/build.gradle.kts`
- Compottie README: `https://github.com/alexzhirkevich/compottie`
- Lottie 官方社区与格式说明: `https://lottie.github.io/`

## 17. 2026-07-28 首版实施记录

已落地范围:

- `data-model` 新增 `LottieAnimationSpec` 及 canvas、palette、motion、stroke 子规格，并扩展
  `ChatMessageContent.LottieAnimation` 与 `ChatSessionMode.LOTTIE_ANIMATION`。
- `composeApp` 新增 `LottieAnimationSpecParser`、`LottieAnimationSpecValidator`、
  `LottieJsonBuilder`、`LottieJsonValidator` 与 `LottieMessageParser`。
- `ChatViewModel` 新增 `startLottieAnimationConversation()`。

## 18. 2026-08-07 重构与升级记录 (v1.3.0)

针对动画丰富度不足、硬编码输出绑定死板等问题，进行了全局架构与 Prompt 重构：
- **测试与文档**：
  - `LottieMessageParserTest.kt` 补齐 Native Lottie JSON 解析与数学参数化动态合成校验。
  - 同步更新规范 `docs/specs/lottie-animation-prompt-spec.md` 至 v1.3.0。

