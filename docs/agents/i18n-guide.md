## 国际化 (i18n) 机械约束
**原则**：所有面向用户的界面文本必须支持多语言。

- **已支持语言目录 (`composeApp/src/commonMain/composeResources/`)**：
  - `values/` : English (默认语言)
  - `values-zh/` : 简体中文
  - `values-zh-rTW/` : 繁體中文
  - `values-ja/` : 日本語
  - `values-ko/` : 한국어
  - `values-de/` : Deutsch
  - `values-es/` : Español
  - `values-ru/` : Русский
  - `values-fr/` : Français

- **约束规范**：
  - **禁止项**：禁止在 Composable 函数和 ViewModel 中硬编码任何中/英文或其他语言字符串常量。
  - **键对齐要求**：所有新增或删除的字符串键，必须在默认 `values/strings.xml` 及其他所有 8 个语言目录中同步增删，保持 100% 键对齐。
  - **UI 引用**：必须使用 `compose.components.resources`，统一调用 `stringResource(Res.string.key_name)`。