# CLAUDE.md — edge-llm

Android app that runs LLMs entirely on-device via the GGUF / llama.cpp ecosystem.
Supports multiple model families: Gemma, Qwen, Falcon, Llama, Phi, Mistral, SmolLM,
and any future GGUF-compatible model.

This file is the operational briefing. Read it every session. Deeper docs live in `docs/`.

---

## Non-negotiable conventions

1. **TDD — always.** Write the failing test first; then the minimum implementation to pass.
   No code without a test. No "I'll add the test after."
2. **100% coverage of code we wrote.** See `docs/TESTING.md` for the exclusion list
   (generated code, JNI stubs, Compose framework artifacts). Don't game the number
   by adding trivial tests; the bar is meaningful coverage.
3. **Android Studio on macOS (Apple Silicon) is the primary dev environment.**
   Every command documented in `docs/SETUP.md` is the Gradle wrapper or `adb`,
   so they work identically from any terminal — Android Studio just layers
   Compose Preview, native debugger, profiler, and Layout Inspector on top.
   Suggest IDE-specific steps only when they actually save time (debugging
   across the JNI boundary, profiling).
4. **Physical device only.** No emulator. The test/run loop assumes a real Android
   device connected via `adb`. If `adb devices` shows nothing, stop and ask.
5. **No model files in git.** Models are downloaded at runtime. `.gguf` files
   are in `.gitignore`. Never commit one.
6. **No bypassing tests.** If a test fails, fix the root cause. Don't disable,
   skip, weaken assertions, or `@Ignore` to make CI green.
7. **No `armeabi-v7a` or `x86_64` ABI.** arm64-v8a only — see `docs/TECH_STACK.md`.

## Tech stack at a glance

- **Language:** Kotlin (latest stable)
- **UI:** Jetpack Compose (Material 3)
- **Inference:** llama.cpp (git submodule), JNI bridge, Vulkan + CPU backends
- **Build:** Gradle Kotlin DSL, AGP, NDK + CMake
- **Async:** Coroutines + Flow
- **DI:** Hilt
- **Persistence:** DataStore (settings), Room (chat history)
- **HTTP:** OkHttp (model downloads via WorkManager)
- **Tests (JVM):** JUnit 5, MockK, Turbine, kotlinx-coroutines-test
- **Tests (Android):** Compose UI Test, AndroidX Test
- **Tests (native):** Google Test
- **Coverage:** Kover (Kotlin) + LCOV/gcov (native)
- **Lint:** ktlint + detekt
- **Min SDK:** 28 (Android 9), **Target SDK:** latest stable, **ABI:** arm64-v8a

Full rationale: `docs/TECH_STACK.md`.

## Architecture in one sentence

A Compose UI talks to ViewModels which call a pure-Kotlin `InferenceEngine`
interface; the only implementation today is `LlamaCppEngine`, which bridges
via JNI to a vendored llama.cpp built for arm64-v8a with Vulkan acceleration.

Full layered design: `docs/ARCHITECTURE.md`.

## Module layout

```
edge-llm/
├── app/                         # Android app: UI, navigation, DI wiring
├── core/
│   ├── domain/                  # Pure Kotlin — no Android imports
│   ├── data/                    # Repositories, DataStore, Room
│   └── engine/
│       ├── api/                 # InferenceEngine interface (pure Kotlin)
│       └── llamacpp/            # JNI + native code (CMake builds llama.cpp)
├── third_party/
│   └── llama.cpp/               # git submodule
├── docs/
└── scripts/
```

The dependency rule: `app → core/data → core/domain ← core/engine`.
`domain` never depends on Android. Violations break the build.

## Commands cheat sheet

```bash
# Setup (once)
git submodule update --init --recursive

# Build & install on connected device
./gradlew assembleDebug
./gradlew installDebug
adb shell am start -n dev.edgellm/.MainActivity

# Tests
./gradlew test                              # JVM unit tests
./gradlew connectedAndroidTest              # instrumented tests (device)
./scripts/native-tests.sh                   # C++ gtest suite
./gradlew check                             # everything

# Coverage
./gradlew koverHtmlReport                   # Kotlin coverage → build/reports/kover/
./scripts/native-coverage.sh                # native coverage → build/reports/lcov/

# Lint
./gradlew ktlintCheck detekt

# Logs
adb logcat -s EdgeLLM:V                     # tail app logs

# Native rebuild only
./gradlew externalNativeBuildDebug
```

## Things NOT to do

- Don't suggest running on an emulator.
- Don't write production code before the test exists.
- Don't add `armeabi-v7a` or `x86_64` ABIs.
- Don't bundle `.gguf` files inside the APK or commit them to git.
- Don't use XML layouts — Compose only.
- Don't use `runBlocking` outside test code.
- Don't bypass Hilt with hand-rolled singletons.
- Don't introduce a second LLM runtime (MLC, AICore) until `InferenceEngine`
  has full test coverage and at least one shipped feature uses it.
- Don't add a new model family without adding its `ChatTemplate` and template tests.
- Don't catch and swallow exceptions in native bridge code — surface them as
  `Result.failure` or sealed error types.

## Where to read more

- [`docs/SETUP.md`](docs/SETUP.md) — first-time machine setup, VS Code config, device pairing
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — module layout, layers, data flow, contracts
- [`docs/TECH_STACK.md`](docs/TECH_STACK.md) — every dependency and the reason it was chosen
- [`docs/TESTING.md`](docs/TESTING.md) — TDD workflow, coverage rules, patterns per layer
- [`docs/MODEL_PIPELINE.md`](docs/MODEL_PIPELINE.md) — preparing GGUFs, manifest format, download flow
