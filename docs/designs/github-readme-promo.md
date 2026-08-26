# GitHub README 宣传图设计说明

## 目标

为 Agro 仓库提供一张适合 GitHub README 首屏展示的横版项目宣传图，突出本地智能、隐私感与桌面/移动端的一致体验。

## 视觉方案

- 延续 Ethereal Minimalism 设计系统：暖羊皮纸底色、鼠尾草绿与灰蓝水彩晕染、低透明度柔和阴影。
- 使用桌面聊天页作为主焦点，桌面首页与资料库页作为后层，移动端首页和聊天页作为前景，形成跨平台产品叙事。
- 保留大面积留白，并仅使用 `Agro`、`LOCAL INTELLIGENCE`、`Private. Local. Yours.` 三组品牌文案。
- 不添加设备外壳、浏览器边框、徽章、霓虹或重阴影，避免干扰原始界面设计。

## 素材与产物

- 参考素材：`docs/screenshot/desktop_home.webp`、`desktop_chat.webp`、`desktop_library.webp`、`mobile_home.webp`、`mobile_chat.webp`。
- 最终采用：`docs/screenshot/github_readme_hero_v7_matrix.webp`。
- 输出尺寸：1718 × 915 px，约 1.88:1，适合 README 横幅展示。

## 生成提示摘要

使用 `ads-marketing` 场景生成横版 GitHub README hero；将提供的界面作为高保真截图插图，采用暖羊皮纸、水彩鼠尾草绿与灰蓝背景；桌面聊天界面为主视觉，桌面首页、资料库与两张移动端界面分层组合；保持品牌文案准确、画面克制、无额外标志和水印。

## 候选版本

- `github_readme_hero.webp`：原始层叠主视觉，桌面聊天界面为核心，移动端界面集中在右侧前景。
- `github_readme_hero_v3_panorama.webp`：沉浸式全景构图，以聊天界面覆盖中心，水彩流线连接桌面端与移动端。
- `github_readme_hero_v4_gallery.webp`：极简画廊式构图，三张桌面界面与两张移动界面采用正视、规整的展台排列。
- `github_readme_hero_v6_typographic.webp`：巨幅排版海报构图，以超大半透明 `Agro` 字样和水平界面序列建立强烈品牌识别。
- `github_readme_hero_v7_matrix.webp`：模块化产品矩阵构图，以工程网格和四个功能区域集中呈现跨端能力；当前 README 使用此版本。
- `github_readme_hero_v8_watercolor_journey.webp`：水彩旅程构图，以 Gris 风格的鼠尾草绿至灰蓝河流与轨道线连接不同设备界面。

所有候选版本沿用相同品牌文本与界面保真约束，通过编辑感、沉浸感、秩序感、暮色氛围、巨幅排版、模块矩阵和水彩叙事，为 README 首屏提供不同的信息密度和视觉语气。

## README 内容结构

- 使用中文说明项目定位、核心能力、平台支持与联网边界。
- 通过桌面端和移动端截图展示聊天页与资源库的响应式布局。
- 提供 JDK 21、Bazelisk、Git LFS、Android NDK 与各桌面平台工具链要求。
- 提供桌面端、Android、iOS 的运行命令，以及测试、打包和首次原生构建提示。
- 记录主要模块、技术栈、贡献约束与 GPL-3.0 许可证入口。
