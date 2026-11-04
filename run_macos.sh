#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 1. Auto-detect JDK 17+ on macOS if JAVA_HOME is not already configured
if [[ -z "${JAVA_HOME:-}" ]] || [[ ! -x "${JAVA_HOME}/bin/java" ]]; then
    CANDIDATES=(
        "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
        "/Applications/Android Studio Preview.app/Contents/jbr/Contents/Home"
        "/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"
        "/Applications/IntelliJ IDEA CE.app/Contents/jbr/Contents/Home"
        "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
        "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
        "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
    )
    for c in "${CANDIDATES[@]}"; do
        if [[ -x "$c/bin/java" ]]; then
            export JAVA_HOME="$c"
            break
        fi
    done

    if [[ -z "${JAVA_HOME:-}" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
         DETECTED_JDK="$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)"
         if [[ -n "$DETECTED_JDK" ]] && [[ -x "$DETECTED_JDK/bin/java" ]]; then
             export JAVA_HOME="$DETECTED_JDK"
         fi
    fi
fi

# 2. Ensure the native macOS CoreAudio helper binary is compiled and up to date
HELPER_SRC="composeApp/src/desktopMain/resources/macos/GlassBeatAudioHelper.swift"
HELPER_BIN="composeApp/src/desktopMain/resources/macos/glassbeat-audio-helper"
if command -v swiftc >/dev/null 2>&1 && [[ -f "$HELPER_SRC" ]]; then
    if [[ ! -x "$HELPER_BIN" ]] || [[ "$HELPER_SRC" -nt "$HELPER_BIN" ]]; then
        echo "Compiling macOS CoreAudio helper..."
        swiftc -O "$HELPER_SRC" -o "$HELPER_BIN"
        chmod +x "$HELPER_BIN"
    fi
fi

# 3. Run or package the Compose for Desktop application
TASK="${1:-:composeApp:run}"
exec ./gradlew "$TASK"
