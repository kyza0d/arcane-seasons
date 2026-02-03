#!/bin/bash

set -e

JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}
export JAVA_HOME

DEFAULT_HYTALE_DIR="/home/kyza/.var/app/com.hypixel.HytaleLauncher/data/Hytale"
HYTALE_DIR="${HYTALE_DIR:-$DEFAULT_HYTALE_DIR}"
MODS_DIR="${MODS_DIR:-$HYTALE_DIR/UserData/Saves/Modding/mods}"

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="$PROJECT_ROOT/build/libs"

if [ ! -d "$BUILD_DIR" ]; then
    echo "Error: Build directory not found: $BUILD_DIR"
    exit 1
fi

JAR_FILE=$(find "$BUILD_DIR" -name "*.jar" -type f 2>/dev/null | head -1)
if [ -z "$JAR_FILE" ]; then
    echo "Error: No JAR file found in: $BUILD_DIR"
    exit 1
fi

if [ ! -d "$MODS_DIR" ]; then
    echo "Error: Mods directory not found: $MODS_DIR"
    exit 1
fi

cp "$JAR_FILE" "$MODS_DIR/" || exit 1

echo "✓ Plugin deployed to: $MODS_DIR/$(basename "$JAR_FILE")"
