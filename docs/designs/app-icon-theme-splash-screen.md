# AppIcon 主题启动页设计

## 品牌叙事

启动页将 AppIcon 解释为“玻璃容器中的本地智能种子”。画面从低对比度的失色世界开始，水彩潮汐唤醒环境，玻璃容器中的种子、披风叶片与星芒依次生长，最终形成完整品牌标志。

## 主题落地

- 页面底层使用 `watercolorGradient()`，以 Primary 鼠尾草绿与 Secondary 雾蓝形成低透明度水彩过渡。
- AppIcon 外壳使用 `glassSurface()`，继承 `AppTheme.shape.xxl`、玻璃透明度、柔边框与环境阴影。
- 羊皮纸 Surface 作为留白基底，Slate Gray 仅用于低对比度地平线与拱门结构。
- 标题使用 `AppTheme.typography.headlineSmall/headlineLarge`，全部间距、尺寸与线宽来自语义 token。

## 动画阶段

1. 拱门线稿、地平线和环境空间显现。
2. 鼠尾草绿与雾蓝水彩潮汐从两侧扩散。
3. 玻璃容器出现，种子精灵、双色披风叶片、嫩芽与四芒星逐层生长。
4. 应用名和由中心向两侧流淌的双色光脉出现。
5. 完整场景停留后统一淡出，淡出完成才执行导航。

## 响应式与平台边界

- `ContentType.Single` 使用纵向构图；`ContentType.Dual` 使用横向构图并左移背景焦点。
- 图标尺寸受窗口宽高与 `cardMedium/cardLarge` token 共同限制。
- 图标内部使用 commonMain Compose Canvas 按 `ic_launcher.xml` 比例绘制，Android、Desktop 与 iOS 共用。
- 除 `BuildConfig.APP_NAME` 外不增加用户文案；装饰图形从语义树移除。
