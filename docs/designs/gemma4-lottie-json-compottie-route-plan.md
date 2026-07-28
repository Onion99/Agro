# Gemma4 Lottie Spec JSON 与 Compottie 渲染路线计划

> 日期: 2026-07-28
> 范围: `ChatViewModel` 专用会话入口、`lottie_animation_spec` JSON envelope、确定性 Lottie JSON builder、`compottie` 本地渲染、复制与保存
> 状态: 首版已落地（2026-07-28）
> 关联文档: `docs/designs/gemma4-8bit-bgm-json-mml-composemediaplayer-route-plan.md`、`docs/designs/extensible-chat-messages.md`、`docs/agents/data-model.md`
> 外部参考: `https://github.com/alexzhirkevich/compottie`

## 1. 背景

项目已经具备结构化媒体生成路线: SVG 图像生成使用 `svg_image` JSON envelope，8-bit BGM 使用 `chiptune_bgm_mml` JSON envelope，再由本地 parser、validator 和 renderer 生成可展示内容。

Lottie 动画虽然也是 JSON 格式，但完整 Lottie JSON 包含图层、shape、关键帧、trim path、缓动曲线、图片资源、字体、precomp、表达式等复杂结构。Gemma4 2B/4B 直接自由生成完整 Lottie JSON 的失败面较大: 输出可能是合法 JSON，但不是可播放 Lottie；也可能引用 `compottie` 不支持或首版不允许的资源能力。

本路线采用和 8-bit BGM 类似的折中方案: Gemma4 输出受限的动画规格 `lottie_animation_spec`，客户端严格解析和校验后，由本地确定性 `LottieJsonBuilder` 生成标准 Lottie JSON，再交给项目现有 `compottie` 库渲染。

关键原则:

- 模型输出动画意图和参数，不直接输出任意 Lottie layer tree。
- 最终产物仍是标准 Lottie JSON，可复制、保存和重新渲染。
- `compottie` 首版只消费本地 `JsonString`，不走网络 URL 和外部资源。
- 首版定位 UI 微动画，不覆盖复杂插画级 motion design。

## 2. 路线总览

```mermaid
flowchart TD
    A[Library / Chat 入口] --> B[startLottieAnimationConversation]
    B --> C[ChatSessionMode.LOTTIE_ANIMATION]
    C --> D[LOTTIE_ANIMATION_SYSTEM_INSTRUCTION]
    D --> E[Gemma4 输出 lottie_animation_spec JSON]
    E --> F[LottieAnimationSpecParser]
    F --> G[LottieAnimationSpecValidator]
    G --> H[LottieJsonBuilder]
    H --> I[LottieJsonValidator]
    I --> J[ChatMessageContent.LottieAnimation]
    J --> K[Compottie LottieCompositionSpec.JsonString]
    K --> L[Chat Lottie Bubble 预览 / 复制 / 保存]
```

## 3. 路线定位

| 路线 | 优点 | 缺点 | 建议定位 |
| --- | --- | --- | --- |
| 直接生成完整 Lottie JSON | 表达力最强，理论上最灵活 | token 成本高，结构复杂，跨 renderer 兼容性和视觉稳定性差 | 不作为首版主路线 |
| 修改预置 Lottie 模板参数 | 最稳定，质量可控 | 创作自由度低，需要模板库 | fallback 和高质量入口 |
| `lottie_animation_spec` + 本地 builder | 结构稳定，token 成本可控，可强校验 | 首版动画类型受限，需要实现 builder | 首版默认路线 |
| dotLottie 打包 | 文件更小，可承载多动画和主题 | 首版不需要 ZIP、多动画和 manifest 管理 | 后续增强路线 |

首版优先覆盖高频 UI 微动画:

- `loading_spinner`
- `success_check`
- `error_cross`
- `progress_dots`
- `pulse_badge`
- `empty_state_sparkle`

## 4. 输出协议

### 4.1 JSON Envelope

模型输出必须是单个原始 JSON 对象，不允许 Markdown code fence、解释文本、完整 Lottie `layers`、外部 URL、图片 base64、HTML、CSS、脚本、`.lottie` ZIP、文件路径或远程资源引用。

推荐 schema:

```json
{
  "type": "lottie_animation_spec",
  "schemaVersion": 1,
  "title": "Success Check",
  "seed": 18421,
  "canvas": {
    "width": 240,
    "height": 240,
    "background": "transparent"
  },
  "fps": 60,
  "durationMs": 1200,
  "loop": false,
  "kind": "success_check",
  "palette": {
    "primary": "#22C55E",
    "secondary": "#DCFCE7",
    "accent": "#FFFFFF"
  },
  "motion": {
    "style": "draw_then_pop",
    "intensity": 0.72,
    "staggerMs": 120
  },
  "stroke": {
    "width": 10,
    "lineCap": "round"
  }
}
```

### 4.2 字段约束

| 字段 | 约束 |
| --- | --- |
| `type` | 固定为 `lottie_animation_spec` |
| `schemaVersion` | 首版固定为 `1` |
| `title` | 1 到 48 个可显示字符 |
| `seed` | 可选；缺失时由规范化 JSON payload 计算稳定 hash |
| `canvas.width` / `canvas.height` | `64..512`，推荐 `240` 或 `320` |
| `canvas.background` | `transparent` 或 `#RRGGBB` |
| `fps` | 仅允许 `24`、`30`、`60` |
| `durationMs` | `300..3000`，推荐 `800..1600` |
| `loop` | `Boolean`，loading 类默认 `true`，反馈类默认 `false` |
| `kind` | 首版枚举值见 4.3 |
| `palette.primary` | 必填 `#RRGGBB` |
| `palette.secondary` / `palette.accent` | 可选 `#RRGGBB` |
| `motion.style` | 由 `kind` 限定的枚举值 |
| `motion.intensity` | `0.0..1.0` |
| `motion.staggerMs` | `0..600` |
| `stroke.width` | `1..32` |
| `stroke.lineCap` | `butt`、`round`、`square` |

### 4.3 动画类型约束

| `kind` | 默认 `loop` | 允许 `motion.style` | 首版生成策略 |
| --- | --- | --- | --- |
| `loading_spinner` | `true` | `spin_arc`、`orbit_dots` | arc trim + rotation 或多点 orbit |
| `success_check` | `false` | `draw_then_pop`、`circle_then_check` | 圆环 stroke trim + checkmark stroke trim + scale pop |
| `error_cross` | `false` | `draw_then_shake`、`cross_fade_in` | 两条 cross stroke trim + group shake |
| `progress_dots` | `true` | `stagger_bounce`、`stagger_fade` | 3 到 5 个圆点的 scale/opacity stagger |
| `pulse_badge` | `true` | `soft_pulse`、`ripple` | ellipse scale + opacity keyframes |
| `empty_state_sparkle` | `false` | `float_sparkle`、`fade_sparkle` | 简单几何 sparkle 的 scale/opacity stagger |

### 4.4 首版禁止项

- 禁止模型输出 Lottie 底层字段: `layers`、`assets`、`fonts`、`chars`、`ef`、`x`。
- 禁止图片层、文本层、precomposition、表达式、3D layer、mask、merge paths 和外部资源。
- 禁止任意 path morph。首版 path 只能由 builder 内置模板或受控 normalized points 生成。
- 禁止远程 URL、文件路径、base64 和可执行脚本。

## 5. 生成的 Lottie JSON 子集

`LottieJsonBuilder` 输出的 Lottie JSON 必须控制在 `compottie` 易验证、可测试的基础 shape 子集内:

| Lottie 字段 | 首版策略 |
| --- | --- |
| `v` | 固定 builder 支持版本，例如 `5.7.4` |
| `fr` | 来自 `fps` |
| `ip` | 固定 `0` |
| `op` | `ceil(durationMs / 1000 * fps)` |
| `w` / `h` | 来自 `canvas` |
| `nm` | 来自 `title` |
| `ddd` | 固定 `0` |
| `assets` | 固定空数组 |
| `layers` | 仅 shape layer，`ty = 4` |
| shape `ty` | 首版只生成 `el`、`rc`、`sh`、`fl`、`st`、`tr`、`tm` |
| transform | 只生成 position、scale、rotation、opacity、anchor |
| keyframes | 只生成线性或预设 ease keyframe |

首版 builder 不接收模型直接提供的任意 shape tree。所有 layer 名称、shape 名称和 keypath 由代码生成，便于 `compottie` dynamic properties 和后续 UI 调试。

## 6. 专用 System Instruction 草案

```text
You are ${BuildConfig.APP_NAME}'s dedicated Lottie micro-animation planner.

Your only job is to output one raw valid JSON object containing a constrained
Lottie animation specification. Do not output full Lottie layers. Do not use
Markdown fences or add prose outside the JSON.

Use this JSON structure exactly:
{
  "type": "lottie_animation_spec",
  "schemaVersion": 1,
  "title": "Success Check",
  "seed": 12345,
  "canvas": {
    "width": 240,
    "height": 240,
    "background": "transparent"
  },
  "fps": 60,
  "durationMs": 1200,
  "loop": false,
  "kind": "success_check",
  "palette": {
    "primary": "#22C55E",
    "secondary": "#DCFCE7",
    "accent": "#FFFFFF"
  },
  "motion": {
    "style": "draw_then_pop",
    "intensity": 0.72,
    "staggerMs": 120
  },
  "stroke": {
    "width": 10,
    "lineCap": "round"
  }
}

Rules:
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
  `LottieJsonBuilder`、`LottieJsonValidator` 与 `LottieMessageParser`，模型仍只输出
  `lottie_animation_spec`，完整 Lottie JSON 由本地 builder 确定性生成。
- 首版 builder 覆盖 `loading_spinner`、`success_check`、`error_cross`、`progress_dots`、
  `pulse_badge` 与 `empty_state_sparkle`，输出仅使用 shape、stroke、fill、transform 与 trim path
  子集，`assets` 固定为空数组。
- `ChatViewModel` 新增 `startLottieAnimationConversation()` 和专用
  `LOTTIE_ANIMATION_SYSTEM_INSTRUCTION`，结构化生成模式结束前不展示中间 JSON。
- `LibraryScreen` 中原 `LogicVesselCard` 已改为 Lottie 动画生成入口卡片。
- `ChatScreen` 新增 Compottie `LottieCompositionSpec.JsonString` 预览、复制最终 Lottie JSON、复制原始
  spec 与保存 `.json`。
- 英文与中文 compose resources 已补齐 Lottie Library、Chat context、渲染失败、复制、保存与时长文案。
- 新增 `LottieMessageParserTest`，覆盖合法生成、确定性 builder、Markdown 包裹拒绝、完整 layer tree
  拒绝、motion style 不匹配和 duration 越界。

未纳入首版:

- 不生成 `.lottie` ZIP，不引入网络 URL loading，不加载外部 images/fonts/assets。
- 不支持模型直接输出完整 Lottie layer tree；后续如需复杂 motion design，应继续扩展本地模板族和
  validator，而不是放宽模型输出边界。
