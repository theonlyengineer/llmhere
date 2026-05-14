# Testing

TDD is the law. This document defines how we practice it, what 100% coverage
means in this codebase, and the patterns to use per layer.

## The TDD loop

For every change — feature, bug fix, refactor — the loop is:

1. **Red.** Write the smallest possible failing test that expresses the
   behavior. Run it. Confirm it fails for the reason you expect.
2. **Green.** Write the minimum production code to make it pass.
   No more.
3. **Refactor.** Clean up. Run the test again. Still green.
4. **Commit.** Test + implementation in a single atomic commit.

Two specific rules:

- **No production code without a failing test first.** Not "I'll add the
  test after." That is not TDD; that is regret.
- **Test failure messages must be diagnostic.** A failed test should tell
  you what's broken without needing to open a debugger.

## Coverage policy

The goal is **100% of code we wrote that has meaningful branches**.
"Meaningful" means the test catches a regression a human would care about.

### Excluded from coverage (by Kover/LCOV config)

These are excluded mechanically — adding them to exclusion lists requires
review:

| Category                                  | Why excluded                              |
|-------------------------------------------|-------------------------------------------|
| Generated code (Hilt, Room, kotlinx-ser)  | We didn't write it; framework is tested. |
| Compose `@Composable` Preview functions   | Visual only, not behavioral.              |
| `MainActivity.onCreate` wiring boilerplate| Tested via E2E only; line-level coverage is theater. |
| JNI native method declarations            | The C++ side is covered separately by gtest. |
| Manifest-declared receivers/services      | Lifecycle hooks; tested via integration when present. |
| `data class` `equals`/`hashCode`/`copy`   | Compiler-generated.                       |
| Sealed-class exhaustiveness branches      | Compiler-enforced; can't actually hit "else". |

Anything outside that list must be tested.

### What we report

- `./gradlew koverHtmlReport` → `build/reports/kover/html/index.html`
- `./scripts/native-coverage.sh` → `build/reports/lcov/index.html`
- `./scripts/coverage-aggregate.sh` → unified summary

### Coverage as a CI gate

The gate is on **uncovered branches**, not raw percentage. A PR that adds
100 lines and 100% covers them passes. A PR that adds 100 lines and
covers 99 of them but the one uncovered line is a real branch fails.
A 100% number with vacuous tests is still failing the spirit.

## Test pyramid

```
            ▲
            │
       E2E (instrumented, on device, real model)
            │     small number — high-confidence smoke
            │
       Integration (JVM, with real Room/DataStore)
            │     medium number — repos + use cases together
            │
       Unit (JVM, fakes/mocks)
            │     vast majority — pure logic
            ▼
```

### Unit tests

- Module: any (`domain`, `data`, `engine/api`, ViewModels)
- Runner: JUnit 5 on JVM
- Time: fast (<1s each)
- Style:
  ```kotlin
  class ChatTemplateGemmaTest {
      private val template = GemmaTemplate()

      @Test
      fun `formats single user turn with system prompt`() {
          val result = template.format(
              messages = listOf(ChatMessage(Role.User, "hi")),
              systemPrompt = "be brief",
          )
          assertEquals(
              "<start_of_turn>user\nbe brief\nhi<end_of_turn>\n" +
              "<start_of_turn>model\n",
              result,
          )
      }
  }
  ```

### Flow / coroutine tests with Turbine

```kotlin
@Test
fun `generate emits tokens then completes`() = runTest {
    val engine = FakeInferenceEngine(emits = listOf("hello", " ", "world"))
    engine.generate("prompt", GenerationConfig.default()).test {
        assertEquals("hello", awaitItem().text)
        assertEquals(" ", awaitItem().text)
        assertEquals("world", awaitItem().text)
        awaitComplete()
    }
}
```

### Integration tests (JVM, Robolectric where unavoidable)

- Module: `data` (repositories with real Room/DataStore via in-memory configs)
- Runner: JUnit 5 + Robolectric
- Time: medium (~100ms each)
- Used sparingly — only where mocking would invalidate the test.

### Instrumented tests (on device)

- Module: `app` (Compose UI Test), `engine/llamacpp` (real JNI calls)
- Runner: `connectedAndroidTest`
- Time: slow (real device round-trips)
- Categories:
  - **UI behavior** via Compose UI Test backed by fake ViewModels.
  - **JNI contract** — tiny prompts, tiniest possible model, verify
    cancellation, error paths, lifecycle.
  - **E2E smoke** (`@LargeTest`) — load a 270M-param model, generate
    10 tokens, verify output is non-empty and the engine returns to
    `Ready`. Run on every push; not on every save.

### Native tests (Google Test)

- Module: `engine/llamacpp`
- Runs as a **host binary** built by `scripts/native-tests.sh`.
- Covers the JNI wrapper logic, not llama.cpp itself.
- Run on every push.

## Test patterns per layer

### Domain (pure Kotlin)

- 100% achievable. Pure functions and data.
- Mocks: never. Use real implementations.
- Test fixtures: use kotlinx.serialization to load known-good catalog JSON
  blobs from `src/test/resources/`.

### Data (repositories)

- Use **in-memory Room** (`Room.inMemoryDatabaseBuilder`) for the real DAO behavior.
- Use **`testDataStore`** from `androidx.datastore.preferences` for DataStore.
- Mock OkHttp via a `MockWebServer` — never mock OkHttp itself.

### Engine (`InferenceEngine` implementations)

- **Contract tests** in `core/engine/api/src/testFixtures/`:
  an abstract `InferenceEngineContractTest` that exercises every requirement
  in the contract. Each concrete engine extends it and supplies a factory.
  Adding MLC later means writing one factory + zero new test logic.
- `LlamaCppEngine` additional tests:
  - Loads a tiny test model (bundled in `src/androidTest/assets/`).
  - Verifies cancellation propagates within 1 second.
  - Verifies double-`load()` fails cleanly.
  - Verifies `unload()` is idempotent.

### ViewModels

- Inject a `TestDispatcher`.
- Replace repositories and engines with fakes (hand-written, not mocked).
- Assert against `StateFlow` history with Turbine.

### Compose UI

- Use `ComposeContentTestRule`.
- Drive the screen with a fake ViewModel exposing controlled `StateFlow`.
- Assert: nodes present, content text, state-driven visual states (loading,
  error, ready). Don't snapshot-test (brittle).

### JNI / native

- Kotlin side: keep `external fun` declarations in `NativeBindings.kt` with
  no logic. Test the layer above it (`LlamaCppEngine`) using a fake
  `NativeBindings` interface in production; the real one is bound in Hilt.
- C++ side: `scripts/native-tests.sh` builds a host binary of the JNI
  wrapper (without the JNI entry points) and exercises it with gtest.
  llama.cpp itself is treated as a trusted dependency — we test our wrapper,
  not it.

## Fakes vs mocks: when to use which

- **Fake** = a hand-written stand-in with a real, deterministic implementation
  of the interface. Use for anything called more than once or with stateful
  behavior. Lives in `src/testFixtures/` next to the interface it fakes.
- **Mock (MockK)** = a generated stub with per-call expectations. Use only
  for verifying side effects (e.g., "was `repository.save()` called with X").

If a test is hard to write because mocks are stacking up, that's a smell.
Reach for a fake.

## What we don't do

- **Snapshot tests** — brittle and rarely diagnose the actual failure.
- **`@Ignore` to make CI green** — fix or delete.
- **Testing private methods** — test through the public surface.
- **`Thread.sleep` in tests** — use TestDispatcher virtual time or
  Turbine's `awaitItem(timeout)`.
- **Network calls in tests** — MockWebServer for HTTP, no exceptions.
- **Real model files in unit tests** — too slow and too large. Tiny model
  in `androidTest/assets/` for instrumented tests only.

## Practical scripts

```bash
# Fast feedback (run on save / pre-commit)
./gradlew test                          # JVM unit tests

# Pre-push
./gradlew check                         # test + lint + connectedAndroidTest
./scripts/native-tests.sh

# Coverage check
./gradlew koverHtmlReport
open build/reports/kover/html/index.html   # macOS — adapt as needed

# Run a single test class
./gradlew :core:domain:test --tests "dev.edgellm.domain.GemmaTemplateTest"
```

## Writing the very first test

When this project gets its first commit of real code, the first test
should be the one that proves `GemmaTemplate` correctly formats a single
turn. It's the smallest meaningful behavior in the system and unblocks
everything below it.
