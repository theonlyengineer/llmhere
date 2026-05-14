# Setup

First-time setup for working on edge-llm on **macOS (Apple Silicon)** with
**Android Studio** and a **physical Android device**.

## What you need

| Tool                    | Version policy                              | Why                                 |
|-------------------------|---------------------------------------------|-------------------------------------|
| macOS                   | Apple Silicon (M-series)                    | Tested host platform                |
| Android Studio          | Latest stable (Apple Silicon build)         | Primary IDE; bundles SDK, NDK, CMake|
| JDK 17 (Temurin) arm64  | 17.x — bundled with Android Studio          | AGP and Kotlin require it           |
| Android SDK             | API 35 (or latest stable) — via Android Studio | Build, adb, sign                 |
| Android NDK             | Pinned (see `gradle.properties`) — via SDK Manager | Native compilation            |
| CMake                   | 3.22.1 — via SDK Manager                    | llama.cpp build                     |
| Git                     | 2.30+ (Xcode CLT or Homebrew)               | Submodules                          |
| Python 3.10+            | Only if preparing GGUFs offline (see `docs/MODEL_PIPELINE.md`) | |
| Physical Android device | API 28+ (Android 9+), arm64-v8a, 8 GB+ RAM recommended | Real test target          |

## 1. Install Android Studio

Download the **Apple Silicon (ARM)** build from
<https://developer.android.com/studio>. The `.dmg` filename will contain
`-mac_arm.dmg`. **Do not** install the Intel build — Rosetta 2 emulation will
slow every build noticeably.

Drag to `/Applications`. Launch. Follow the setup wizard:

- Choose **Standard** installation.
- Accept the licenses.
- Let it download the default SDK + emulator (the emulator we won't use, but
  it's bundled).

Verify in **Android Studio → Settings → Languages & Frameworks → Android SDK**
that `Android SDK Location` is set (default `~/Library/Android/sdk`).

## 2. Install required SDK components

**Android Studio → Settings → Languages & Frameworks → Android SDK**

In the **SDK Platforms** tab, check:
- `Android 15 (API 35)` (or latest stable)

In the **SDK Tools** tab, check **Show Package Details** and pin:
- `Android SDK Build-Tools` → `35.0.0` (or matching)
- `NDK (Side by side)` → `27.0.12077973` (or the version pinned in
  `gradle.properties` — keep these in sync)
- `CMake` → `3.22.1`
- `Android SDK Command-line Tools (latest)`
- `Android SDK Platform-Tools`

Apply. Wait for the downloads.

## 3. Shell environment

Add to `~/.zshrc` (zsh is default on modern macOS):

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# Use the JDK that ships with Android Studio
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

Reload:
```bash
source ~/.zshrc
java -version    # expect 17.x (Temurin / JetBrains JBR)
adb --version
sdkmanager --list_installed
```

## 4. Physical device pairing

1. On your phone: **Settings → About → tap "Build number" 7 times** →
   Developer Options unlocked.
2. **Developer Options → enable "USB debugging"**.
3. Plug device into the Mac with a **data-capable USB-C cable** (cheap
   power-only cables are the #1 gotcha).
4. On the phone, approve the RSA fingerprint prompt and check "Always allow".

Verify:
```bash
adb devices
# expect: <serial>   device
```

### Wireless adb (recommended for daily work)

After pairing via USB:
```bash
adb tcpip 5555
adb shell ip addr show wlan0     # note the IPv4 address
# Now you can unplug the USB cable
adb connect <phone-ip>:5555
adb devices                       # confirm
```

Phone must stay on the same Wi-Fi. Re-run `adb connect` after phone reboots.

For Android 11+, you can also use **wireless debugging with a pairing code**
(Settings → Developer Options → Wireless debugging → Pair device with
pairing code) which doesn't require a USB cable for initial pairing.

## 5. Clone & init submodules

```bash
cd ~/code   # or wherever you keep projects
git clone <repo-url> edge-llm
cd edge-llm
git submodule update --init --recursive
# Pulls third_party/llama.cpp at the pinned commit.
```

## 6. Open in Android Studio

**File → Open** → select the `edge-llm/` directory (the root with the
top-level `build.gradle.kts`).

First sync will:
- Download the Gradle wrapper version.
- Download AGP, Compose, Hilt, OkHttp, etc.
- Run `externalNativeBuild` configuration (CMake configure for llama.cpp).

Expect 5–15 minutes the first time. Subsequent syncs are seconds.

If Android Studio prompts to "Trust" the project, do so.

## 7. First build & install

From inside Android Studio, hit the green **Run ▶ "app"** button with your
device selected in the device dropdown.

Or from the terminal (works identically):

```bash
./gradlew assembleDebug          # full debug build
./gradlew installDebug           # push APK to connected device
adb shell am start -n dev.edgellm/.MainActivity
adb logcat -s EdgeLLM:V          # tail app logs in a separate terminal
```

First build is slow (5–20 min) — NDK is compiling llama.cpp. Subsequent
builds are fast (<30s incremental).

## 8. First test run

```bash
./gradlew test                            # JVM unit tests
./gradlew connectedAndroidTest            # instrumented tests (needs device)
./scripts/native-tests.sh                 # native gtest suite
```

In Android Studio, you can also right-click a test class or method →
**Run** to run a single test with full output and one-click rerun.

## 9. Coverage report

```bash
./gradlew koverHtmlReport
open build/reports/kover/html/index.html
```

## When to use Android Studio vs the terminal

| Task                                          | Best in                          |
|-----------------------------------------------|----------------------------------|
| Editing Kotlin / Compose code                 | Android Studio                   |
| Editing C++ / CMake                           | Android Studio (CLion-class C++) |
| Iterating Compose UI                          | Android Studio (Preview + Live Edit) |
| Debugging Kotlin                              | Android Studio                   |
| Debugging C++ / JNI (LLDB across the boundary)| Android Studio                   |
| Profiling tokens/sec, memory, GPU, thermal    | Android Studio Profiler          |
| Inspecting Compose runtime tree               | Android Studio Layout Inspector  |
| APK size analysis                             | Android Studio APK Analyzer      |
| Running `./gradlew check` in CI-like terminal | Terminal (or AS terminal pane)   |
| Tailing logs                                  | Terminal (`adb logcat`)          |
| Editing markdown / docs                       | Any editor                       |

## Common problems

| Symptom                                          | Fix                                                                                          |
|--------------------------------------------------|----------------------------------------------------------------------------------------------|
| `SDK location not found`                         | `echo "sdk.dir=$ANDROID_HOME" > local.properties` (don't commit). Or set in AS settings.     |
| `NDK not configured`                             | SDK Manager → NDK (Side by side) → confirm pinned version matches `gradle.properties`.       |
| `adb devices` shows `unauthorized`               | Phone unlocked? Approve RSA prompt. If missed: Developer Options → Revoke USB authorizations, re-plug. |
| `Permission denied` on install                   | Developer Options → enable **Install via USB** (and **USB debugging (Security settings)** on some MIUI/OneUI). |
| Gradle daemon hangs                              | `./gradlew --stop` then retry.                                                               |
| Linker error `undefined symbol` for `llama_*`    | Submodule not initialized: `git submodule update --init --recursive`.                        |
| Slow builds, Mac heating up                      | Verify Android Studio is the arm64 build: `file /Applications/Android\ Studio.app/Contents/MacOS/studio` should show `arm64`. |
| Compose Preview blank                            | **Build → Make Project** first; previews need compiled symbols.                              |
| `Live Edit` not applying                         | Some changes (signature changes, new composables) require recomposition; flash a full build. |

## What's intentionally not here

- Linux or Windows host setup (we standardize on macOS Apple Silicon for this project).
- Emulator setup (physical device only).
- iOS toolchain (Android-only for now).
- Play Console / signing setup (later, when shipping).
