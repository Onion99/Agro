# iOS LiteRT LM Native Bridge

Date: 2026-07-20

## Scope

This note records the iOS native bridge for `LiteRtLmJni`.

## Current Behavior

- `composeApp` no longer registers the old Kotlin/Native cinterop for `sdloader.def`.
- iOS framework builds no longer link the legacy `stable-diffusion.cpp` static libraries from Gradle.
- `composeApp/src/nativeInterop/cinterop/litertlm.def` maps the LiteRT LM C API from `cpp/lite-rt-lm/c/engine.h`.
- Kotlin/Native cinterop must include `cpp/lite-rt-lm/c` directly because `litertlm.def` declares `headers = engine.h`. Including only `cpp/lite-rt-lm` makes CI/Xcode cinterop fail with `fatal error: 'engine.h' file not found`.
- iOS framework linking expects a native library named `liblitertlm_c_api.a` or `liblitertlm_c_api.dylib` in both `cpp/libs/ios-device` and `cpp/libs/ios-simulator`.
- The active iOS target matrix is `iosArm64` and `iosSimulatorArm64`; `iosX64` is intentionally not registered.
- `iosArm64Main` and `iosSimulatorArm64Main` must explicitly depend on the shared `iosMain` source set so `src/iosMain/kotlin` actual declarations are visible to Kotlin/Native expect/actual matching.
- `validateIosLiteRtLmNativeLibs` runs on macOS before iOS link tasks and fails early if the required native library is missing. It is implemented as a custom task with path and library-name inputs so it does not capture `Project` from a `doLast` closure under Gradle configuration cache.
- `buildIosLiteRtLmNativeLibs` builds the device arm64 archive and simulator arm64 archive directly; it does not build `ios_x86_64` or merge simulator archives with `lipo`.
- iOS archive tasks must not mutate the `cpp/lite-rt-lm` submodule. They rsync the submodule into `composeApp/build/litertlm-ios-workspace`, apply the parent-repo patch `cpp/patches/lite-rt-lm-ios-native-link.patch`, and run Bazel from that temporary workspace.
- The patched iOS workspace defaults to Bazel target `//c:engine_fully_linked` and copies `bazel-bin/c/engine_fully_linked_lipo.a` as `liblitertlm_c_api.a`. The raw `//c:engine` `cc_library` archive only contains direct C API objects and leaves transitive Abseil, LiteRT, TensorFlow Lite, Rust, and parser objects unresolved for Kotlin/Native.
- The patched `//c:engine_fully_linked` target uses `apple_static_library` to register a Bazel fully-link action over `:engine`, then exposes the single-arch result as `engine_fully_linked_lipo.a`.
- iOS Bazel archive tasks resolve Bazelisk through `-Pbazelisk.path`, `BAZELISK`, `BAZELISK_PATH`, then `PATH`, then common macOS Homebrew paths (`/opt/homebrew/bin/bazelisk`, `/usr/local/bin/bazelisk`). This avoids Gradle daemon or IDE environments failing with `command 'bazelisk'` when Homebrew is not present in PATH.
- iOS Bazel archive tasks must not pass the repository-root `.bazelrc.user` explicitly. That file is reserved for host-local desktop/Android overrides and can contain Windows-only paths such as `G:/_b`; iOS uses the `cpp/lite-rt-lm` Bazel workspace rc chain instead.
- iOS Bazel archive tasks pass explicit `--macos_sdk_version` and `--ios_sdk_version` values. Defaults are resolved with Gradle `providers.exec` from `xcrun --sdk macosx|iphoneos|iphonesimulator --show-sdk-version`, and can be overridden with `apple.macosSdkVersion`, `apple.iphoneOsSdkVersion`, or `apple.iphoneSimulatorSdkVersion`.
- iOS Bazel builds enable `--incompatible_enable_apple_toolchain_resolution` so Apple split transitions preserve the configured `@build_bazel_apple_support//platforms:ios_arm64` or `ios_sim_arm64` platform instead of falling back to `ios_x86_64`.
- LiteRT LM's `rules_rust` registration maps `aarch64-apple-ios` and `aarch64-apple-ios-sim` to `@platforms//cpu:arm64` plus the matching Apple `device` or `simulator` constraint. This matches `apple_support` platforms, which use `arm64` rather than `aarch64`.
- iOS Bazel builds pass `--define=LITERT_LM_FST_CONSTRAINTS_DISABLED=1`. The Gemma model FST constraint provider is delivered as a prebuilt platform `.dylib`, which is not part of the fully linked static C API archive. Disabling this path removes the unresolved `LiteRtLmGemmaModelConstraintProvider_*` references from Kotlin/Native framework links.
- iOS audio preprocessing links `@miniaudio//:miniaudio_decoder`, a decoder-only C target with `MA_NO_DEVICE_IO`, `MA_NO_ENCODING`, and `MA_NO_GENERATION`. It must not link `@miniaudio//:miniaudio_objc`, because that target pulls Apple device frameworks and can compile against macOS `AVFoundation/CoreImage` headers while targeting iOS Simulator.
- GitHub Actions iOS builds install Bazelisk, restore the Bazel disk cache, and rewrite the repository-root `.bazelrc.user` before invoking Gradle. The iOS Bazel tasks must not inherit local developer paths such as `G:/_b` or Windows-only `BAZEL_VC` values.
- `buildReleaseIpa` packages the `.app` from `buildReleaseArchive` using a cacheable custom Gradle task. The task receives `app.name` through an `@Input Property<String>` during configuration; `@TaskAction` must not call `project.property("app.name")`, because execution-time `Project` access is incompatible with configuration cache and can fail to resolve the dynamic property.
- `LiteRtLmJni` now has an iOS `actual` implementation that calls the LiteRT LM C API for engine creation, conversation creation, synchronous message sending, streaming message sending, cancellation, and release.
- iOS model selection uses FileKit and returns the selected `.litertlm` path.
- `sendLmMessageAsync` uses a Kotlin/Native `StableRef` as callback state and disposes it on final/error stream callbacks.

## Native Contract

- The native library must export the `litert_lm_*` symbols declared by `cpp/lite-rt-lm/c/engine.h`.
- `LiteRtLmJni.ios.kt` stores native pointers as `Long` handles to keep the common API aligned with Android and Desktop.
- `LmEngine` remains responsible for deleting engine handles through `deleteLmEngine`.
- `LmConversation` remains responsible for deleting conversation handles through `deleteLmConversation`.
- `SamplerConfig` is mapped to the C API `LiteRtLmSamplerParams` using `kLiteRtLmSamplerTypeTopP`, matching the JNI implementation.

## Current Limitations

- The LiteRT LM C API does not expose the common API's `mainBackendNumThreads`, `audioBackendNumThreads`, `channelsJsonString`, `overwritePromptTemplate`, or `visualTokenBudget` inputs. The iOS bridge currently ignores those fields.
- Gemma FST constrained decoding through `LiteRtLmGemmaModelConstraintProvider` is disabled for iOS static archive builds. Calls that request that path fail with the upstream build-time disabled error rather than requiring an extra prebuilt dylib in the app bundle.
- Intel iOS Simulator is not supported by the current target matrix. Reintroducing it requires adding `iosX64`, an `ios_x86_64` Bazel build, and an explicit simulator archive merge step.
