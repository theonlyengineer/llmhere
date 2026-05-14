# Tech Stack

Every dependency and the reason it was chosen over alternatives.
Version-pinning policy at the bottom.

## Language & UI

### Kotlin (JVM target 17)
- **Why:** Official Android language. First-class Compose support.
  Coroutines + Flow are how we model async/streaming.
- **Considered:** Java (too verbose), Flutter/Dart (extra layer between us
  and JNI), React Native (we'd own a JS bridge on top of an NDK bridge).

### Jetpack Compose (BOM, Material 3)
- **Why:** Modern declarative UI. Recomposition handles streaming token
  updates naturally — exactly what an LLM chat UI needs.
- **Considered:** XML/View — slower iteration, doesn't fit streaming UX well.
- **Tooling note:** Compose Preview and Live Edit (Android Studio) speed
  up UI iteration considerably; use them. Behavioral correctness is still
  verified by Compose UI Tests on the real device — never by previews.

## Inference

### llama.cpp (vendored as git submodule)
- **Why:** Widest model coverage of any on-device runtime. GGUF format is
  the de-facto package format for small LLMs on HuggingFace. Active
  development, frequent perf improvements, quantization options from Q2
  through Q8 plus K-quants and IQ-quants.
- **Considered:**
  - **MediaPipe / AICore** — whitelist-based, controlled by Google. Doesn't
    meet the "many model families" goal.
  - **MLC LLM** — strong contender, faster GPU. May be added as a second
    engine later. Higher per-model toolchain cost (TVM compilation).
  - **ExecuTorch** — narrower model coverage, Llama-centric.
  - **ONNX Runtime Mobile** — less LLM-specific optimization.
- **Integration:** `third_party/llama.cpp` git submodule. CMake target built
  by Gradle `externalNativeBuild`. JNI bindings in `core/engine/llamacpp/src/main/cpp`.

### Backends
- **Vulkan** (primary GPU path) — modern, broadly supported on Android.
- **CPU** (fallback with NEON + ARMv8.2 dotprod + i8mm when available).
- **Considered:** OpenCL (older, less consistent on modern Android).
  Qualcomm QNN (Snapdragon-only — vendor lock-in we're avoiding for now).

### KV cache quantization
- Q8_0 and Q4_0 KV cache supported via llama.cpp flags. Off by default;
  enabled via `GenerationConfig.kvCacheType`.

## Build system

### Gradle (Kotlin DSL) + Android Gradle Plugin
- **Why:** Standard for Android. Kotlin DSL gives us type-safety and
  the same language as the app.
- **Wrapper checked in.** `./gradlew` is the only entry point.

### NDK + CMake
- **Why:** llama.cpp uses CMake; AGP integrates via `externalNativeBuild`.
- **NDK version:** pinned in `app/build.gradle.kts` and `gradle.properties`
  so all contributors and CI use the same compiler.

### ABI strategy
- **arm64-v8a only.**
- **Why:**
  - Android 8+ requires 64-bit native libs for new apps anyway.
  - 32-bit (armeabi-v7a) caps usable address space at ~3 GB — fatal for
    even small LLMs once weights + KV cache are factored in.
  - x86_64 only useful for emulator, which we don't use.
- **Result:** smaller APKs, simpler build matrix, smaller test surface.

## Async, state, DI

### kotlinx.coroutines + Flow
- **Why:** Standard for Kotlin async. Flow is the right abstraction for
  streaming tokens. Cancellation propagation is what we need to stop
  generation mid-stream.

### Hilt (DI)
- **Why:** Official Android DI. Compile-time safety. Good Compose & ViewModel
  integration. The standard the next developer will expect.
- **Considered:** Koin (simpler runtime DI, but loses compile-time checks).
  Manual DI (works for small projects but breaks down as engines/repos grow).

### AndroidX ViewModel + SavedStateHandle
- **Why:** Survives configuration change. SavedStateHandle survives process
  death for conversation state.

## Persistence

### DataStore (Proto)
- **Why:** Modern replacement for SharedPreferences. Async. Typed.
- **What goes here:** user settings, selected model id, generation defaults,
  remote-catalog URL, telemetry opt-in.

### Room
- **Why:** SQLite ORM with compile-time SQL verification. Coroutine + Flow APIs.
- **What goes here:** conversations, messages, per-conversation system prompts.

### Filesystem
- **What goes here:** model files (`.gguf`) in app's internal `files/models/`.
  SHA256 verified at materialization time.

## Networking

### OkHttp
- **Why:** Standard Android HTTP client. Streaming downloads, resume support
  via Range headers, connection pooling.
- **Used for:** model downloads, remote catalog fetch.

### WorkManager
- **Why:** Background work that survives app death and reboots.
- **Used for:** large model downloads. Constraints: unmetered network +
  charging by default; user can override per download.

## Testing

### JUnit 5 (JVM unit tests)
- **Why:** Modern test framework. Parameterized tests, lifecycle hooks,
  nested classes. AGP supports it on JVM modules.

### MockK
- **Why:** Idiomatic Kotlin mocking. Handles coroutines, suspending functions,
  and `object` mocking — things Mockito struggles with.

### Turbine
- **Why:** Ergonomic Flow testing. Awaits emissions, verifies completion,
  fails on unconsumed emissions.

### kotlinx-coroutines-test
- **Why:** TestDispatcher and runTest for controlling virtual time in tests.

### Compose UI Test
- **Why:** Official Compose testing. Runs as instrumented test on device.
- **Used for:** UI tests verifying state-to-screen behavior. Backed by fakes.

### AndroidX Test (Robolectric for the few Android-class tests we run on JVM)
- **Used for:** repository and DataStore tests that touch the Android Context
  but don't need a real device.

### Google Test (native)
- **Why:** Standard C++ testing. Runs via a dedicated `scripts/native-tests.sh`
  that builds a host binary using the same wrapper code as the Android lib.

## Coverage & lint

### Kover (JetBrains)
- **Why:** Modern Kotlin-aware coverage. Plays well with Compose and
  multi-module Gradle. Handles inline functions correctly (JaCoCo doesn't).
- **HTML report:** `build/reports/kover/html`.

### LCOV / gcov (native)
- **Why:** Standard C++ coverage tooling. Output combined with Kover into
  a single aggregate report in CI.

### ktlint
- **Why:** Standard Kotlin style. Fast. Zero-config.

### detekt
- **Why:** Catches more than style — complexity, code smells, anti-patterns.
  Custom rule set in `config/detekt/detekt.yml`.

## Other notable choices

- **Logging:** Timber wrapping Android Log. Production builds strip verbose
  logs via R8.
- **Crash reporting:** none in v1 (privacy-first; this is an on-device app).
  May add an opt-in local-only crash log later.
- **Serialization:** kotlinx.serialization for the catalog JSON and any
  future network APIs. Compile-time, reflection-free, fast.
- **Date/time:** kotlinx-datetime.

## Version policy

- **Gradle wrapper pinned.** Bump intentionally.
- **AGP, Kotlin, Compose BOM, NDK** pinned in `gradle/libs.versions.toml`
  (version catalog). Bump intentionally with a "regenerate report and verify"
  pass.
- **llama.cpp submodule** pinned to a specific commit. Bumping requires
  re-running the JNI contract tests and the model regression suite.
- **No `+` or `latest` versions, ever.**

## SDK targets

- **`minSdk = 28`** (Android 9, August 2018).
  Rationale: ~95% of currently active Android devices. Devices below this
  threshold can't run small LLMs usefully anyway (typically <4 GB RAM,
  older SoC without ARMv8.2 dotprod).
- **`targetSdk` = latest stable** at time of release. Bump deliberately
  per Play Store annual requirements.
- **`compileSdk` = `targetSdk`.**

## Things we are deliberately NOT using

- **XML layouts** — Compose only.
- **RxJava** — Coroutines + Flow.
- **Dagger 2 (raw)** — Hilt wraps it.
- **Retrofit** — overkill for our download-shaped HTTP needs.
- **Glide / Coil for images** — no image loading in v1.
- **Firebase** — privacy-first, on-device app.
- **Google Play Services** — not required; the app must work without it.
- **Anti-piracy / licensing checks** — open use.
