#!/usr/bin/env bash

set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
JAVA_HOME="${JAVA_HOME:-$HOME/.local/jdk-17}"
JDK_ARCHIVE="/tmp/temurin-jdk17.tar.gz"
CMDLINE_TOOLS_VERSION="11076708"
CMDLINE_TOOLS_DIR="$ANDROID_HOME/cmdline-tools/latest"
CMDLINE_TOOLS_ARCHIVE="/tmp/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"

for required_command in curl tar unzip; do
    command -v "$required_command" >/dev/null 2>&1 || {
        printf '%s gerekli. Once kurun.\n' "$required_command" >&2
        exit 1
    }
done

if [[ ! -x "$JAVA_HOME/bin/java" ]] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "17\.'; then
    JAVA_HOME="$HOME/.local/jdk-17"
    mkdir -p "$(dirname "$JAVA_HOME")"
    curl -fsSL \
        -o "$JDK_ARCHIVE" \
        "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
    rm -rf "$JAVA_HOME"
    mkdir -p "$JAVA_HOME"
    tar -xzf "$JDK_ARCHIVE" --strip-components=1 -C "$JAVA_HOME"
fi

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

mkdir -p "$ANDROID_HOME/cmdline-tools"

if [[ ! -x "$CMDLINE_TOOLS_DIR/bin/sdkmanager" ]]; then
    curl -fsSL \
        -o "$CMDLINE_TOOLS_ARCHIVE" \
        "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
    rm -rf "$CMDLINE_TOOLS_DIR"
    mkdir -p "$CMDLINE_TOOLS_DIR"
    unzip -q "$CMDLINE_TOOLS_ARCHIVE" -d "$ANDROID_HOME/cmdline-tools"
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools"/* "$CMDLINE_TOOLS_DIR/"
    rmdir "$ANDROID_HOME/cmdline-tools/cmdline-tools"
fi

export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$CMDLINE_TOOLS_DIR/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

yes | sdkmanager --licenses >/dev/null || true
sdkmanager \
    "platform-tools" \
    "platforms;android-34" \
    "platforms;android-35" \
    "build-tools;34.0.0" \
    "build-tools;35.0.0"

printf '\nAndroid SDK hazirlandi: %s\n' "$ANDROID_HOME"
printf 'Bu terminalde Gradle calistirmak icin: ./gradlew assembleDebug\n'