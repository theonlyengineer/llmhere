# Architecture

## Goals (priority-ordered)

1. **Run multiple model families** — any GGUF-compatible model (Gemma, Qwen, Falcon,
   Llama, Phi, Mistral, SmolLM, …) through a single inference path.
2. **Stay swappable** — adding a second runtime (MLC, AICore) later must not require
   changes in UI or ViewModel layers.
3. **Operate within phone constraints** — limited RAM, thermal throttling, battery,
   storage. Memory budgets are first-class concerns, not afterthoughts.
4. **Be fully testable** — every layer below the UI is unit-testable on the JVM
   with zero Android dependencies. UI is tested via Compose UI Test against fakes.

## Layered design

```
┌────────────────────────────────────────────────────────┐
│  ui/                       (Compose)                   │
│  Composables, Navigation, Theme, Previews              │
│  ↓ depends on                                          │
├────────────────────────────────────────────────────────┤
│  viewmodel/                (AndroidX ViewModel)        │
│  ChatViewModel, ModelCatalogViewModel, …               │
│  Exposes immutable StateFlow<UiState>                  │
│  ↓                                                     │
├────────────────────────────────────────────────────────┤
│  data/                     (Android-dependent repos)   │
│  ModelRepository (catalog + downloads)                 │
│  ConversationRepository (Room)                         │
│  SettingsRepository (DataStore)                        │
│  ↓                                                     │
├────────────────────────────────────────────────────────┤
│  domain/                   (Pure Kotlin, NO Android)   │
│  - InferenceEngine interface                           │
│  - ChatTemplate interface                              │
│  - ModelDescriptor, GenerationConfig, Token            │
│  - Use cases: GenerateReplyUseCase, etc.               │
│  ↑ implemented by                                      │
├────────────────────────────────────────────────────────┤
│  engine/llamacpp/                                      │
│  LlamaCppEngine : InferenceEngine                      │
│  JNI bindings → native llama.cpp                       │
├────────────────────────────────────────────────────────┤
│  native/                   (C++)                       │
│  Thin JNI wrapper around llama.cpp API                 │
│  Vendored llama.cpp via git submodule                  │
│  Tested with Google Test                               │
└────────────────────────────────────────────────────────┘
```

**Dependency rule:** arrows only point downward. `domain` knows nothing about
`data`, `data` knows nothing about `viewmodel`, `viewmodel` knows nothing about
`ui`. Engine implementations sit beside `data`, both depending on `domain`.

## Gradle module structure

```
edge-llm/
├── app/                            # com.android.application
│   └── src/main/kotlin/dev/edgellm/
│       ├── MainActivity.kt
│       ├── EdgeLLMApplication.kt   # @HiltAndroidApp
│       ├── ui/
│       └── di/                     # Hilt modules wire everything
│
├── core/
│   ├── domain/                     # java-library + kotlin
│   │   └── (no android dep — verified by build script)
│   │
│   ├── data/                       # com.android.library
│   │   └── repositories, Room, DataStore, OkHttp clients
│   │
│   └── engine/
│       ├── api/                    # java-library + kotlin
│       │   └── InferenceEngine, LoadConfig, GenerationConfig
│       │
│       └── llamacpp/               # com.android.library + externalNativeBuild
│           ├── src/main/kotlin/    # LlamaCppEngine, NativeBindings
│           └── src/main/cpp/       # JNI shims + CMakeLists.txt
│
├── third_party/
│   └── llama.cpp/                  # git submodule, built by CMake
│
├── scripts/                        # native-tests.sh, native-coverage.sh, etc.
├── docs/
└── gradle/, build.gradle.kts, settings.gradle.kts
```

## Why split modules

- **Enforces the dependency rule via the build system.** Compile fails if `domain`
  imports anything Android-shaped.
- **Faster tests.** `domain` and `engine/api` run on the JVM. No Robolectric or
  emulator needed for the vast majority of business logic.
- **Engine swap is local.** A future `engine/mlc/` or `engine/aicore/` module
  follows the same shape and slots in by Hilt binding.
- **Smaller native rebuild surface.** Only `engine/llamacpp` triggers NDK builds.

## The InferenceEngine contract

```kotlin
// core/engine/api
package dev.edgellm.engine

interface InferenceEngine {
    val capabilities: EngineCapabilities
    val state: StateFlow<EngineState>

    suspend fun load(model: ModelHandle, config: LoadConfig): Result<Unit>
    suspend fun unload()
    fun generate(prompt: String, config: GenerationConfig): Flow<Token>
    fun cancel()
}

data class Token(
    val text: String,
    val logprob: Float?,
    val isFinal: Boolean,
)

sealed class EngineState {
    data object Idle : EngineState()
    data object Loading : EngineState()
    data object Ready : EngineState()
    data object Generating : EngineState()
    data class Error(val cause: Throwable) : EngineState()
}

data class EngineCapabilities(
    val supportsGpu: Boolean,
    val supportsKvCacheQuantization: Boolean,
    val supportedQuantizations: Set<String>,
    val supportedFamilies: Set<String>,
)
```

### Contract requirements

Every implementation must:

1. **Be stateless across loads.** A second `load()` may follow a previous
   `unload()` without residue. Tested.
2. **Surface progress via `state`.** No raw callbacks. Tested.
3. **Honor coroutine cancellation in `generate()`.** Cancelling the collector
   propagates a stop signal to native and the flow completes promptly. Tested.
4. **Never block the calling thread.** All work happens on `Dispatchers.Default`
   or a dedicated dispatcher injected via the constructor.
5. **Surface native errors as `Result.failure` or `EngineState.Error`** — never
   crash the process.

### LlamaCppEngine specifics

- Owns one native `llama_context*` at a time.
- Native handle stored as an opaque `Long` on the Kotlin side; never dereferenced
  except by passing back through JNI.
- Generation loop runs in a dedicated single-thread dispatcher to keep `llama.cpp`'s
  context single-threaded.
- Token decoding happens native-side; Kotlin receives UTF-8 strings via JNI.
- Stop tokens checked native-side before yielding to JNI to avoid round-trip cost.

## Model catalog

A baseline JSON manifest ships in the APK at `app/src/main/assets/catalog.json`.
At runtime it may be overlaid by a remote catalog fetched from a configurable URL
(stored in DataStore; default empty). One entry per quantization variant.

```json
{
  "id": "gemma-4-e2b-q4_k_m",
  "family": "gemma-4",
  "display_name": "Gemma 4 E2B (Q4_K_M)",
  "url": "https://huggingface.co/.../gemma-4-e2b-q4_k_m.gguf",
  "sha256": "abc123...",
  "size_bytes": 1400000000,
  "quantization": "Q4_K_M",
  "context_length": 128000,
  "min_ram_mb": 3000,
  "recommended_ram_mb": 6000,
  "chat_template": "gemma-4",
  "stop_tokens": ["<end_of_turn>"],
  "modalities": ["text"],
  "engine": "llamacpp"
}
```

`ModelRepository.materialize(descriptor)`:
1. Returns existing local path if file present and sha256 matches.
2. Otherwise enqueues a `DownloadWorker` via WorkManager.
3. Verifies sha256 on completion.
4. Refuses to return if free RAM at moment of call is below `min_ram_mb`.
5. Returns `ModelHandle(file, descriptor)` to pass to `InferenceEngine.load()`.

Catalog format details: `docs/MODEL_PIPELINE.md`.

## Memory and lifecycle

- **One model in RAM at a time.** Switching = `unload()` then `load()`.
- **`onTrimMemory(TRIM_RUNNING_CRITICAL)`** → engine `unload()`. Conversation
  state is preserved; user reload triggers reinitialization.
- **`onLowMemory()`** → same as above plus user-visible notification.
- **Active generation lives in a foreground service** so it survives the user
  navigating away (with a notification, cancellable).
- **Process death** during generation: state in `SavedStateHandle` + Room means
  the conversation is intact; the in-flight response is lost (acceptable).

## Chat templating

Per-family. llama.cpp's GGUF often embeds a Jinja chat template, but coverage
and correctness are inconsistent. We apply our own template in Kotlin before
handing the prompt to native.

```kotlin
// core/domain
interface ChatTemplate {
    fun format(messages: List<ChatMessage>, systemPrompt: String?): String
    val stopTokens: List<String>
}

data class ChatMessage(val role: Role, val content: String)
enum class Role { System, User, Assistant }
```

Implementations live in `core/domain/templates/`:

| Family    | Class            | Turn markers                              |
|-----------|------------------|-------------------------------------------|
| gemma-3   | `GemmaTemplate`  | `<start_of_turn>user … <start_of_turn>model …`  |
| gemma-4   | `Gemma4Template` | same family, may differ in detail         |
| qwen-2.5  | `QwenTemplate`   | `<\|im_start\|>user …<\|im_end\|>`        |
| llama-3   | `Llama3Template` | `<\|begin_of_text\|><\|start_header_id\|>…` |
| phi-3     | `Phi3Template`   | `<\|user\|>…<\|end\|>`                    |
| falcon-3  | `FalconTemplate` | family-specific                           |

Selection: `ModelDescriptor.chat_template` → `ChatTemplateRegistry.get(name)`.
Each template has unit tests covering: system prompt present/absent, multi-turn,
trailing assistant turn for resumption, empty content, special characters that
collide with template tokens.

## Data flow: one chat turn

```
User types & sends
  │
  ▼
ChatViewModel.send(text)
  │  appends UserMessage to conversation StateFlow
  │  appends empty AssistantMessage placeholder
  ▼
GenerateReplyUseCase.invoke(messages, modelHandle, config)
  │  template.format(messages, systemPrompt) → String prompt
  │  engine.generate(prompt, config) → Flow<Token>
  ▼
Collect Flow<Token>:
  │  for each token: update assistant message content via StateFlow
  │  Compose recomposes only the affected bubble
  ▼
Token.isFinal == true OR cancellation
  │  Flow completes, ViewModel marks turn complete
  │  ConversationRepository persists final turn to Room
```

Cancellation: tapping "stop" → `viewModelScope` job cancellation →
coroutine cancellation propagates → `LlamaCppEngine.cancel()` sets a native
atomic flag → native loop exits at next token boundary → Flow completes.

## Adding a new engine (future)

Concrete steps to add MLC LLM, AICore, or any other runtime:

1. Create `core/engine/<name>/` module.
2. Implement `InferenceEngine`.
3. Add a Hilt `@Provides` keyed by an engine identifier.
4. Add an `engine` field in `ModelDescriptor` JSON and an `EngineSelector`
   that maps `descriptor.engine` → implementation.
5. Write contract tests using the shared `InferenceEngineContractTest` base
   class (lives in `core/engine/api/src/testFixtures/`).

UI, ViewModels, and templates are unaffected.

## What this architecture is NOT optimized for

Deliberate non-goals, so we don't drift:

- **Multiple models loaded simultaneously.** Phone RAM doesn't support it.
- **Server-grade throughput.** Single-user, single-stream.
- **Cloud fallback.** This is an on-device app. No cloud path.
- **iOS.** Android-only for now; if we add iOS later, `domain` and `engine/api`
  are already portable Kotlin.
- **Training or fine-tuning on-device.** Inference only.
