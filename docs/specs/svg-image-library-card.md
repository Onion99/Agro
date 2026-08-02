# SVG 图像生成资源库卡片

> 日期: 2026-07-23（2026-07-25 更新）
> 范围: `composeApp` Library 入口、SVG 专用会话、响应解析与消息预览

## 1. 目标

将 Library 页原先的 Data Crystal 通用分析卡片替换为专门面向 SVG 图像生成的入口。用户点击该卡片后直接进入 Chat，并为当前 LLM 会话创建注入专用 `systemInstruction`，让模型稳定输出可解析的 SVG 图像 JSON。

## 2. 入口行为

- `LibraryScreen` 中使用 `SvgImageCard` 展示 SVG 图像生成入口。
- 点击卡片会调用 `ChatViewModel.startSvgImageConversation()`，随后通过 `onOpenChat()` 导航到 Chat 页。
- 该方法不会覆盖用户在设置页维护的全局 `systemPrompt`，而是选择
  `ChatSessionMode.SVG_IMAGE` 并为当前会话保存独立 system instruction 快照。
- 普通新建会话仍走 `ChatViewModel.startNewConversation()`，并恢复使用全局 `systemPrompt`。
- Chat 页顶部的上下文提示条会显示当前对象为 SVG 图像生成器、模型应用状态，并允许展开、
  选择和复制完整 system instruction。

## 3. 会话创建约束

SVG 图像模式通过 `LmEngine.createConversation()` 创建会话时传入:

- `systemInstruction = SVG_IMAGE_SYSTEM_INSTRUCTION`
- `toolsDescriptionJsonString = agentTools.getToolsDescriptionJson()`
- `enableConversationConstrainedDecoding = true`
- 当前 UI 中配置的 `temperature`、`topP`、`topK`

如果用户先点击 SVG 卡片但 LLM 引擎尚未初始化，`ChatViewModel` 会在
`ConversationContextState` 中保留模式与目标 instruction，并把 `isApplied` 标为
`false`。后续 `initLLM()` 初始化引擎时使用该上下文创建专用会话，成功后写回 Room
会话快照。

## 4. 输出 JSON 结构

专用 `systemInstruction` 要求模型只输出一个 JSON object，不允许 Markdown 包裹或额外说明。结构如下:

```json
{
  "type": "svg_image",
  "svg": "<svg xmlns='http://www.w3.org/2000/svg' width='1024' height='1024' viewBox='0 0 1024 1024'>...</svg>"
}
```

`svg` 字段内部必须使用单引号属性，避免 JSON 字符串中出现 `\"`。这样用户从 JSON 中复制 `svg` 字段值时，得到的就是可直接保存为 `.svg` 或交给 SVG 渲染器的 markup。

## 5. 安全边界

SVG 输出必须自包含，禁止:

- `<script>` 与事件处理器。
- `DOCTYPE` 与 `ENTITY` 声明。
- `foreignObject`。
- 外部链接、远程图片、`@import` 和非 fragment `url(...)`。

模型应优先使用 SVG 原生矢量元素、`path`、渐变、mask 和必要的文本元素。
生成完成后 `SvgMessageParser` 会再次执行这些安全检查；不安全、超出 1 MiB、XML
不完整或画布无效的响应转换为 `Unsupported` 内容，不交给 SVG 解码器。

## 6. SVG 可复制与可渲染约束

为避免导出的 JSON 中 `svg` 字段复制后无法渲染，专用 `systemInstruction` 约束如下:

- `svg` 字段必须是单行 SVG markup，不插入 JSON newline escape。
- SVG/XML 属性必须使用单引号，例如 `width='1024'`，避免在字段值中出现 `\"`。
- 根节点必须是一个完整 `<svg ...>...</svg>`，包含 `xmlns`、`width`、`height`、`viewBox`。
- 所有 `<g>`、`<defs>`、`<filter>`、`<mask>`、`<clipPath>` 等标签必须严格配对，禁止多余 `</g>` 或孤立闭合标签。
- 优先使用十六进制颜色与 `opacity` / `fill-opacity` / `stroke-opacity`，避免 `rgba(...)` 在部分 SVG 渲染器中的兼容问题。
- 必须遵循 SVG 的 painter's order：可见元素按照文档顺序从底到顶绘制。`<defs>` 后的第一个可见元素应为全画布背景（如有），所有主体、装饰、阴影与文字必须位于背景之后。
- 禁止在主体之后追加或重复不透明的全画布背景 `<rect>`、`<path>` 或 `<g>`；响应前必须检查背景不会覆盖请求的图案。

## 7. 消息预览与持久化

- 完成响应被解析为 `ChatMessageContent.SvgImage`，并写入 Room v2
  `chat_message_contents`。
- Coil ImageLoader 注册 `SvgDecoder.Factory()`；Chat 气泡直接渲染 SVG UTF-8
  字节，不经过位图化。
- SVG 消息提供复制源码与跨端 `.svg` 文件保存操作；渲染失败时仍保留这两种退路。
- 历史会话保存 `mode = svg_image` 和实际应用的 system instruction，重新打开后仍按
  SVG 协议创建 constrained-decoding conversation。
- v1 数据库迁移会识别旧助手消息中的 SVG JSON 并转换为类型化 SVG 内容。

## 8. 当前限制

- 应用恢复历史消息后会重建正确模式的原生 LLM conversation，但仍不会把历史轮次重新
  播放进模型上下文。
- SVG 校验是面向本地预览的保守白/黑名单边界；将文件交给其他执行环境时仍应由目标环境
  执行自己的内容安全策略。
