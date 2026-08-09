# Bazel Windows/Android RC 配置边界

## 背景

项目通过 `composeApp:buildAndroidNativeLib` 调用 `cpp/lite-rt-lm` 工作区内的 Bazel 目标：

```text
//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni
```

Gradle 任务会额外传入仓库根目录的 `.bazelrc.user`，用于设置本机 Bazel 输出目录、Visual Studio 工具链路径，以及 Windows host 编译参数。

`.bazelrc.user` 中的启动参数必须按 Bazel 主机平台隔离。Windows 使用短输出根
`G:/_b`；Gradle 在 macOS/Linux 上显式覆盖为 `/tmp/agro-bazel`，CI 则通过
`BAZEL_OUTPUT_ROOT` 传入 runner 的绝对输出根。否则 `G:/_b` 会在 Unix 工作区中
被解析为相对路径，导致 Bazel 从类似 `cpp/lite-rt-lm/G:/_b/...` 的目录加载内嵌 JDK。

## 约束

`/utf-8` 是 MSVC 风格编译参数，不能通过全局 `build --copt` 或 `build --cxxopt` 注入。Android 交叉编译使用 NDK `clang.exe`，该驱动不会把 `/utf-8` 当作编码参数处理，而是按输入文件解析，导致如下错误：

```text
clang: error: no such file or directory: '/utf-8'
```

因此 `.bazelrc.user` 必须遵循以下边界：

- Android target 编译不能收到 `/utf-8`。
- Windows host 工具编译可以通过 `--config=win_host` 收到 `/utf-8`。
- Windows desktop target 编译必须显式启用 `--config=msvc_target_utf8`，不能依赖全局 target copts。

## 当前配置

`.bazelrc.user` 中保留 Windows Visual Studio 路径，并将 UTF-8 参数拆到显式配置：

```text
startup --output_user_root=G:/_b
build:windows --action_env=BAZEL_VC="C:/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC"
build:windows --repo_env=BAZEL_VC="C:/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC"
build:win_host --action_env=BAZEL_VC="C:/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC"
build:win_host --repo_env=BAZEL_VC="C:/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC"
build:msvc_target_utf8 --copt=/utf-8
build:msvc_target_utf8 --cxxopt=/utf-8
build:msvc_target_utf8 --host_copt=/utf-8
build:msvc_target_utf8 --host_cxxopt=/utf-8
build:win_host --host_copt=/utf-8
build:win_host --host_cxxopt=/utf-8
```

Gradle 的 Bazel 任务通过 `-Pbazel.outputUserRoot=...` 或
`BAZEL_OUTPUT_ROOT` 接收覆盖值；未设置时，macOS/Linux 使用 `/tmp/agro-bazel`。
CI 会在构建前重写 `.bazelrc.user` 并导出 `BAZEL_OUTPUT_ROOT`，仍使用 runner
上的绝对输出根。

`composeApp/build.gradle.kts` 的 Windows desktop 原生库任务显式传入 `--config=msvc_target_utf8`。Android 原生库任务在 Windows 主机上只传入 `--config=win_host`，从而只影响 host 工具链，不污染 Android NDK target 编译。

## GitHub Actions 策略

仓库根目录的 `.bazelrc.user` 是本机开发配置，包含本机 Bazel 输出目录和 Visual Studio 路径，不能直接作为 GitHub Actions 的运行时配置使用。`.github/workflows/build.yml` 在 CI 中会重写工作区内的 `.bazelrc.user`：

- Desktop 三平台和 Android release 构建都会显式安装 `bazelisk`，避免 Gradle 原生库任务启动 `bazelisk` 时找不到命令。
- Desktop CI 当前只发布 `macos-aarch64`、`linux-x86_64`、`windows-x86_64` 三个平台产物；`macos-x86_64` 不在构建矩阵和打包聚合链路中。
- Windows CI 下载 `bazelisk-windows-amd64.exe` 并加入 `PATH`。不要只依赖 `npm install -g @bazel/bazelisk`，因为 npm 在 Windows 上暴露的是 `.cmd` shim，Gradle `ExecOperations`/Java `ProcessBuilder` 直接启动 `bazelisk` 时可能无法解析该 shim。
- Windows desktop CI 必须在执行 Gradle build 步骤时清空 `ANDROID_NDK_HOME` 与 `ANDROID_NDK_ROOT`。`cpp/lite-rt-lm` 的 `android_ndk_env.bzl` 只要检测到 `ANDROID_NDK_HOME` 非空，就会注册 `@androidndk` toolchain；`windows-latest` 预置 NDK 位于 runner SDK 目录，`rules_android_ndk` 在该路径创建 symlink 会触发 `Cannot write outside of the repository directory`。
- CI 生成独立的 Bazel 输出路径，不复用本机 `G:/_b` 等绝对路径。
- Windows CI 必须使用 `startup --output_base=C:/b`。`rules_rust` 构建 proc-macro 时会把很长的 `bazel-out/.../external/crate_index__.../*.rcgu.o` 路径交给 MSVC `link.exe`；如果输出根位于 `D:/a/_temp/bazel-output`，完整路径会超过传统 `MAX_PATH` 并触发 `LNK1181: cannot open input file`。
- CI 生成 `build --disk_cache=~/.cache/bazel-disk`，并通过 `actions/cache` 按 `runner.os` 与构建矩阵隔离缓存。
- Windows CI 通过 `vswhere.exe` 动态发现 Visual Studio C++ toolchain，并写入 `BAZEL_VC`，不依赖本机固定的 BuildTools 路径。
- Linux、macOS 和 Android-on-macOS 不写入 `BAZEL_VC`，只保留 Bazel 输出目录和磁盘缓存配置。
- Linux CI 通过 `.bazelrc.user` 写入 `build --define=xnn_enable_avxvnniint8=false`，并安装、显式绑定 LLVM/Clang 18。Ubuntu 22.04 默认 clang 14 不支持 XNNPACK 的 `-mavxvnniint8` 参数，也不能与 LiteRT-LM 使用的 Abseil `std::source_location` 配置组合；CI 必须通过绝对路径设置 Linux 的 `CC`、`CXX` action/repository 环境变量，不能只依赖 `clang` 的 PATH 解析。
- Windows desktop 仍保留 `msvc_target_utf8` 与 `win_host` 两组 UTF-8 配置，确保 target 与 host 参数边界不回退。
- 所有会执行 `./gradlew` 的 Unix runner 在 wrapper 校验前都会执行 `chmod +x ./gradlew`；同时仓库中的 `gradlew` 必须保持 Git 可执行位，避免 Linux/macOS checkout 后出现 `Permission denied`。
- Desktop 和 Android/iOS 构建 job 必须拉取 Git LFS 资源，并对子模块执行 `git lfs pull`。`cpp/lite-rt-lm/prebuilt/*` 下的 `.so`、`.dylib`、`.dll`、`.lib` 均由子模块 Git LFS 管理；如果 CI 只拿到 LFS pointer，macOS 链接会出现 `ld: unknown file type`。
- macOS 本地构建前可只恢复当前平台的 LFS 对象，并确认文件是 Mach-O 而不是 pointer 文本：

  ```bash
  git -C cpp/lite-rt-lm lfs pull origin HEAD --include='prebuilt/macos_arm64/*'
  file cpp/lite-rt-lm/prebuilt/macos_arm64/*.dylib
  ```

  如果 `file` 输出 `ASCII text`，或文件内容以 `version https://git-lfs.github.com/spec/v1` 开头，必须先完成 LFS 拉取；否则 Bazel 会在最终 dylib 链接阶段报 `ld: unknown file type`。
- Android release 构建必须在 Gradle 执行前安装 NDK，并写入 `local.properties` 的 `sdk.dir` 与 `ndk.dir`。`composeApp:buildAndroidNativeLib` 在任务创建阶段读取 AGP 的 `ndkDirectory`，未安装 NDK 会直接导致 `NDK is not installed`。
- CI 安装的 Android NDK 版本必须与 `gradle/libs.versions.toml` 中的 `android-ndk` 完全一致，并通过 Android convention 插件写入 `android.ndkVersion`。如果 `local.properties` 的 `ndk.dir` 指向另一个版本，AGP 会在配置阶段报 `CXX1104` 版本不一致错误。

## 验证

Windows 上执行 Android 原生库构建时需要 Java 21+：

```powershell
$env:JAVA_HOME='D:\MyApp\Code\Android\android-studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :composeApp:buildAndroidNativeLib --no-configuration-cache
```

期望结果：

- Bazel `android_arm64` target 编译不再出现 `clang: error: no such file or directory: '/utf-8'`。
- 构建产物输出到 `cpp/libs/liblitertlm_jni.so`。
- Android 运行时产物同步到 `composeApp/src/androidMain/jniLibs/arm64-v8a/liblitertlm_jni.so`。
