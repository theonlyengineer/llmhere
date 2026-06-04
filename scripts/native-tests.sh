#!/usr/bin/env bash
# Run native (C++) Google Test suite for edgellm_jni.
# Requires NDK and CMake to be installed via sdkmanager.
# Currently a placeholder — real gtest tests require a host build
# of llama.cpp (not Android cross-compile). The native logic is
# exercised through JVM unit tests with FakeNativeBindings and
# on-device via connectedAndroidTest.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=== EdgeLLM Native Tests ==="
echo "Project: $PROJECT_DIR"

# Verify the native code compiles for arm64-v8a
echo ""
echo "--- Verifying native build (arm64-v8a) ---"
"$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" :core:engine:llamacpp:externalNativeBuildDebug

echo ""
echo "--- Native build OK ---"
echo ""
echo "Note: Full native unit tests (gtest) require a connected arm64 device."
echo "Run: ./gradlew connectedAndroidTest"
