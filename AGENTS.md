# AGENTS.md

## Project identity

Trime (forked as **LyraIME**) — Rime IME for Android. Java/Kotlin app + C++ JNI native layer.
`applicationId = "com.carlson.lyraime"`, root project name `"lyraime"`.

## Build commands

```bash
# First time or after clean:
git submodule update --init --recursive
make debug              # assembles debug APK (auto-applies patches)
make release            # assembles release APK (auto-applies patches, needs keystore.properties)
```

`make clean` removes `build/`, `app/build/`, `app/.cxx/`, then runs `./gradlew clean`.

Java 17 is required (source/target compatibility and JVM target).

## QNN DSP — Runtime download

QNN DSP libraries are **not bundled** in the APK. They are downloaded at runtime on first QNN voice input use from GitHub Releases (`libQnnHtp` tag). The SoC model is detected via `Build.SOC_MODEL` at runtime to select the correct HTP variant. On non-Qualcomm or x86_64 devices, CPU-only inference is used with no download.

| HTP | SoC |
|-----|-----|
| V81 | SM8850 (Snapdragon 8 Gen 5) |
| V79 | SM8750 (Snapdragon 8 Gen 4) |
| V75 | SM8650 (Snapdragon 8 Gen 3) |
| V73 | SM8550 (Snapdragon 8 Gen 2) |
| V69 | SM8450 / SM8475 (Snapdragon 8 Gen 1 / 8+ Gen 1) |
| V68 | SM8350 (Snapdragon 888) |

The download is handled by `QnnDspManager` (`app/src/main/java/com/osfans/trime/link/QnnDspManager.kt`) before QNN engine initialization. The Stub+Skel pair (plus `libQnnHtp.so` and `libQnnSystem.so`) for the detected SoC are extracted to the app's internal files dir.

The DSP packages (`libQnnHtpV{n}.tar.bz2`) are hosted at `https://github.com/Carlson24/LyraIME/releases/download/libQnnHtp/`. See the `publish-qnn-htp` CI workflow for how these are built from the QNN SDK.

## Native build (C++ / JNI)

- **NDK**: 29.0.14206865 (override with `$NDK_VERSION` env or `ndkVersion` gradle property)
- **CMake**: 4.1.2 (override with `$CMAKE_VERSION` env or `cmakeVersion` gradle property)
- **ABIs**: arm64-v8a, x86_64 (split APKs, no universal; maintained in `Versions.kt`)
- **Default ABI**: `buildABI=arm64-v8a` in `gradle.properties` limits builds to arm64-v8a only. Build for emulator (x86_64) with `BUILD_ABI=x86_64 make debug` or change the property. Set to empty or comma-separated list for multi-ABI.
- Native source: `app/src/main/jni/` — submodules: librime, OpenCC, snappy, librime-lua, librime-lua-deps, librime-octagram, librime-calculator, sherpa-onnx
- Output: `librime_jni.so` + `libsherpa-onnx-jni.so` + `libonnxruntime.so` (and others)

**Prebuilt JNI caching**: if `app/prebuilt/` exists, the build reuses pre-compiled `.so` files instead of rebuilding native code. To force a full native rebuild, delete `app/prebuilt/`.

**Auto-patch**: the `native-base-convention` Gradle plugin automatically applies four patches as a dependency of `ExternalNativeBuildTask`:
- `patches/lua.patch` → `librime-lua-deps`
- `patches/sherpa-onnx-qnn.patch` → `sherpa-onnx`
- `patches/librime-custom.patch` → `librime`
- `patches/librime-lua-utf8.patch` → `librime-lua`
The Makefile `patch-apply` target is idempotent and can still be used standalone. Building via Gradle directly (e.g. from Android Studio) also works because the plugin handles it.

## sherpa-onnx (native, submodule)

Voice input depends on `sherpa-onnx` built from source as a git submodule at `app/src/main/jni/sherpa-onnx`.
The Kotlin API sources are in `app/src/main/java/com/k2fsa/sherpa/onnx/`.

**QNN (Qualcomm NPU) support** requires the QNN SDK. Can be set in two ways:
```bash
export QNN_SDK_ROOT=$HOME/.local/share/Android/qairt/2.48.40.260702   # env var
# or set in gradle.properties:
# qnnSdkRoot=/home/.../qairt/2.48.40.260702                            # gradle property
```
QNN is only compiled for `arm64-v8a`. x86_64 builds use CPU-only sherpa-onnx. If the QNN SDK is not found, arm64-v8a also falls back to CPU-only.

**onnxruntime** (static, v1.26.0) is automatically downloaded by CMake during configuration from `csukuangfj/onnxruntime-libs` GitHub releases.

## Module structure

```
:app             — Android application, JNI, Rime data schemas
:codegen         — KSP processor generating RimeKeyMapping (key code ↔ Rime key name)
build-logic/     — included build with custom Gradle convention plugins
```

Custom convention plugins (in `build-logic/convention/`):
- `native-app-convention` — NDK/CMake/ABI config, version tags for native libs
- `native-base-convention` — shared NDK/CMake/ABI config, auto-patch application
- `opencc-data` — copies OpenCC dicts from JNI submodule into assets
- `data-checksums` — generates `checksums.json` for asset integrity
- `native-cache-hash` — computes hash for CI native library caching

`:codegen` is wired via `ksp(project(":codegen"))` in `:app`. After editing `codegen/`, run `./gradlew :app:kspKotlin` to regenerate.

## Testing

```bash
./gradlew :app:test          # unit tests (JUnit5 via Kotest)
```

Tests in `app/src/test/java/`. Test fixtures in `app/src/test/assets/`.
`GeneralStyleTest` initializes Rime via JNI — needs native libraries built first.
No instrumented tests exist currently.

## Code formatting

```bash
make style-lint              # spotlessCheck (Kotlin) + clang-format-lint (C++)
make style-apply             # spotlessApply + clang-format -i (apply fixes)
```

- Kotlin: Spotless with ktlint 1.7.1, IntelliJ IDEA style (see `.editorconfig`)
- C++: clang-format with Google style (see `.clang-format`)
- Spotless excludes `app/src/main/java/com/k2fsa/sherpa/onnx/**`, `app/src/main/jni/sherpa-onnx/**`, and Go scripts — these are owned by the sherpa-onnx submodule.

Run `make style-lint` before committing.

## Commit conventions

Follow the AngularJS-inspired format: `<type>(<scope>): <summary>`
- Types: build, ci, docs, feat, fix, perf, refactor, test
- Scope is optional; `core` for cross-cutting changes
- Imperative present tense, no trailing period

PRs target `develop`, not `master`.

## Rime schemas / data

`app/data/rime/` contains submodules for Rime schema data (prelude, luna-pinyin, stroke, essay).

## Keystore for release builds

Create `keystore.properties`:
```properties
storePassword=…
keyPassword=…
keyAlias=…
storeFile=/path/to/keystore.jks
# or...
keyBase64=<base64-encoded-keystore>
```

## Key gotchas

- `librime-lua-deps` submodule tracks branch `thirdparty` (not `master`) — verify after clone
- `build-logic/` is a composite build; changing convention plugins requires stopping Gradle daemons
- OpenCC data generation runs a Python script from the JNI submodule — Python 3 required
- Symlinks are used in the native build tree; on Windows, enable Developer Mode + `git config core.symlinks true`
- `versionCode` is auto-set to current date (`YYYYMMDD`) — changes on every build
