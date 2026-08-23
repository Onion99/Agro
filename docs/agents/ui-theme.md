## 主题设计规范 — Ethereal Minimalism

> 灵感源自游戏 *Gris* 的艺术方向。视觉语言融合 **极简主义** 与 **毛玻璃效果（Glassmorphism）**，以水彩有机纹理柔化界面，营造宁静、成长与沉思之美。

### 设计系统入口

- **主题对象**：统一通过 `AppTheme.*` 访问所有设计 token。
- **Material3 桥接**：`AppTheme` 内部自动桥接 `MaterialTheme`，所有 M3 组件自动继承 Ethereal Minimalism 色板。
- **默认主题**：亮色模式（`isDark = false`），暖白羊皮纸底色。

```kotlin
// ✅ 正确 — 使用 AppTheme
AppTheme.colors.primary
AppTheme.typography.headlineLarge
AppTheme.shape.xxl
AppTheme.spacing.sectionGap
AppTheme.elevation.glassSurfaceAlpha

// ❌ 禁止 — 硬编码颜色/尺寸
Color(0xFF4A654F)  // 应使用 AppTheme.colors.primary
RoundedCornerShape(32.dp)  // 应使用 AppTheme.shape.xxl
```

### 调色板

| 角色 | 用途 | 亮色值 |
|------|------|--------|
| **Primary (Sage Green)** | 成长性操作、正向状态 | `#4A654F` |
| **Secondary (Dusty Blue)** | 次要交互、信息元素 | `#466275` |
| **Tertiary (Slate Gray)** | 高对比文字、结构边框 | `#50606F` |
| **Surface (Parchment)** | UI 基底，温暖有机 | `#FCF9F2` |
| **Error** | 错误状态 | `#BA1A1A` |

**颜色使用规则**：
- **渐变**：仅使用水彩混合风格——Primary 与 Secondary 间的低透明度过渡。使用 `Modifier.watercolorGradient()`。
- **禁止**硬编码 `Color()` 值，**始终**引用 `AppTheme.colors.*`。
- 暗色主题使用温暖暗底（twilight parchment `#141311`），**非纯黑**。

### 排版

- **字体族**：Metropolis（Light / Regular / Medium / SemiBold / Bold）。
- **标题**（headline）：使用 **Light (300)** 字重，营造"空灵"质感。
- **标签**（label）：使用 **SemiBold (600)** + 加宽字间距 `0.05em`，保证功能可读性。
- **正文**（body）：行高 **≥ 1.5x** 字号，维持"空灵留白"叙事。
- **禁止**在 UI 中使用 `TextStyle()` 自定义样式，**始终**使用 `AppTheme.typography.*`。

| Token | 字号 | 字重 | 行高 | 场景 |
|-------|------|------|------|------|
| `headlineLarge` | 40sp | Light | 52sp | 桌面端页面标题 |
| `headlineSmall` | 30sp | Light | 38sp | 移动端页面标题 |
| `headlineMedium` | 28sp | Normal | 36sp | 节标题 |
| `bodyLarge` | 18sp | Normal | 30sp | 正文大段 |
| `bodyMedium` | 16sp | Normal | 26sp | 正文默认 |
| `labelMedium` | 14sp | SemiBold | 20sp | 按钮/标签 |
| `bodySmall` | 12sp | Normal | 16sp | 辅助说明/caption |

### 形状

使用语义化命名，**禁止**使用旧的 `r100~r500` 命名：

| Token | 圆角 | 用途 |
|-------|------|------|
| `sm` | 4dp | 小元素（输入框内角） |
| `regular` | 8dp | 默认 |
| `md` | 12dp | 中等容器 |
| `lg` | 16dp | 大卡片 |
| `xl` | 24dp | 特大容器 |
| `xxl` | 32dp | 毛玻璃卡片、突出容器 |
| `full` | 9999dp | 胶囊按钮、标签、头像 |

### 间距

基于 **8px 韵律尺度**，通过大间距创造"呼吸空间"：

| Token | 值 | 用途 |
|-------|-----|------|
| `xs` | 4dp | 紧凑间隙 |
| `sm` | 8dp | 小间距 |
| `md` | 16dp | 中间距 |
| `lg` | 24dp | 大间距（= gutter） |
| `xl` | 32dp | 特大间距 |
| `xxl` | 48dp | 组内分隔 |
| `containerPaddingMobile` | 24dp | 移动端容器内边距 |
| `containerPaddingDesktop` | 64dp | 桌面端容器内边距 |
| `sectionGap` | 80dp | 主要内容区块间距 |

兼容旧字段 `s100~s500` 仍可使用。

### 尺寸（Size）

语义化尺寸 token，**禁止**使用旧的 `s24~s512` 命名：

| 分组 | Token | 默认值 | 说明 |
|------|-------|--------|------|
| 图标 | `iconSmall / icon / iconLarge` | 12 / 18 / 24dp | 图标尺寸 |
| 图标按钮 | `iconButton / iconButtonSmall` | 40 / 32dp | 可点击图标区域 |
| 组件 | `buttonHeight / chipHeight` | 48 / 32dp | 按钮/标签高度 |
| 头像 | `avatarSmall / Medium / Large` | 32 / 48 / 72dp | 头像尺寸 |
| 布局 | `borderWidth / borderWidthThin` | 1 / 0.5dp | 边框粗细 |
| 布局 | `maxContentWidth` | 1200dp | 最大内容宽度 |
| 卡片 | `cardSmall / Medium / Large` | 160 / 240 / 320dp | 卡片尺寸 |

### 深度与毛玻璃效果（Elevation）

**避免重阴影**，通过毛玻璃与色调层级传达深度：

| 层级 | 描述 | 实现方式 |
|------|------|----------|
| **基底层** | 羊皮纸底色 + 水彩纹理 | `AppTheme.colors.surface` |
| **表面层** | 半透明白色（60-80%）+ 背景模糊 | `Modifier.glassSurface()` |
| **阴影层** | 环境光阴影，大模糊半径，极低透明度 | 自动包含在 `glassSurface()` 中 |

```kotlin
// ✅ 毛玻璃卡片 — 一行搞定
Box(modifier = Modifier.glassSurface()) { content() }

// ✅ 自定义毛玻璃参数
Box(modifier = Modifier.glassSurface(
    shape = AppTheme.shape.lg,
    alpha = 0.6f,
    borderAlpha = 0.1f,
)) { content() }

// ✅ 水彩渐变背景
Box(modifier = Modifier.watercolorGradient()) { content() }
```

**阴影规则**：
- 模糊半径 ≥ 20dp，透明度 5-8%
- 阴影颜色使用 Slate Gray 着色（`#50606F`），**禁止**纯黑阴影
- **禁止**使用 `elevation` 硬编码数值，使用 `AppTheme.elevation.*`

### 组件设计指南

| 组件 | 设计规范 |
|------|----------|
| **按钮** | Primary 使用 Sage Green 柔和渐变 + `full` 圆角；次要使用 Ghost（仅描边） |
| **卡片** | 毛玻璃风格：`Modifier.glassSurface()` + `xxl` 圆角 + 1px 柔边框 |
| **输入框** | 低调设计：底部边框或极浅半透明填充；聚焦时边框渐变为 Sage Green 发光 |
| **标签/Chip** | 胶囊形状（`full`）+ 50% 透明度背景 + `bodySmall` 字号 |
| **模态/遮罩** | **必须**对底层内容施加背景模糊（`backdrop-filter`） |
| **进度指示器** | 柔和"流淌"色条，**禁止**硬边状态指示器 |

### LLM 运行态水彩指示器与微晶胶囊

- 统一使用 `GrisWatercolorStatusIndicator`；卡片及信息栏使用微晶流光胶囊版本 `GrisWatercolorStatusChip`。禁止在页面中重新实现硬编码圆点、阴影或 `System Ready` 文案。
- **Gris 艺术与神圣几何融合**：指示器由多层低透明径向水彩墨晕（Lissajous 调和漂移）、0.75dp 超细神圣几何星轨微环、四象限微星芒锚点、轨道巡游星尘微粒与纯澈发光星核组成。
- **乔布斯极简主义与极致工艺**：`GrisWatercolorStatusChip` 采用流光微晶毛玻璃材质（微悬浮、背光水彩溢染、迎光面渐变高光倒角微边框），配合平滑字间距与状态淡入淡出动效。
- **语义状态动效规范**：
  - `UNINITIALIZED`：低透明静泊星环与沉睡星核。
  - `INITIALIZING`：星尘巡游粒子沿星轨加速，伴随幽蓝水彩微晕展开。
  - `APPLYING_CONTEXT`：双环共振与柔和能量涟漪向外扩散。
  - `READY`：静谧恒稳的心跳慢呼吸，纯澈星核散发温润微光。
  - `GENERATING`：水彩能量涟漪连续流淌，流光编织回应。
  - `ERROR`：沉静的暮色微烬与低频阻尼脉动，克制而富有叙事张力，严禁生硬闪烁。
- 状态颜色与语义映射由组件内部维护；其他页面如需给图标着色，调用 `resolveGrisStatusAccent()`，避免重复 `when` 或仅依赖 `context.isApplied` 二态判断。

---
> [!NOTE]
> 本文档应随着引入新模式而更新。
