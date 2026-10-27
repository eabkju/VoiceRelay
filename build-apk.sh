#!/bin/bash
set -e

echo "=== Building VoiceRelay Android Application ==="

# Check ANDROID_HOME
if [ -z "$ANDROID_HOME" ]; then
    echo "Warning: ANDROID_HOME is not set."
    echo "Please set ANDROID_HOME to your Android SDK location before running."
    echo "Example: export ANDROID_HOME=/path/to/android/sdk"
fi

echo "[1/4] Running Unit Tests..."
./gradlew test --stacktrace

echo "[2/4] Assembling Debug APK..."
./gradlew assembleDebug --stacktrace

echo "[3/4] Assembling Signed Release APK..."
./gradlew assembleRelease --stacktrace

echo "[4/4] Copying final signed release APK..."
mkdir -p outputs
mkdir -p /outputs 2>/dev/null || true

RELEASE_APK="app/build/outputs/apk/release/app-release.apk"
if [ -f "$RELEASE_APK" ]; then
    cp "$RELEASE_APK" outputs/VoiceRelay.apk
    cp "$RELEASE_APK" /outputs/VoiceRelay.apk 2>/dev/null || true
    echo "✓ SUCCESS: Signed release APK generated at:"
    echo "  - outputs/VoiceRelay.apk"
    echo "  - $RELEASE_APK"
else
    echo "Build finished. Check app/build/outputs/apk/ for outputs."
fi
