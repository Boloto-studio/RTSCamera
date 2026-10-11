#!/bin/sh
# Builds the mod jar into build/libs/. Uses the portable JDK 17 in ~/.local/jdk17 (no system Java installed).
set -e
cd "$(dirname "$0")"
JAVA_HOME="${JAVA_HOME:-$HOME/.local/jdk17}" ./gradlew --no-daemon build "$@"
ls -1 build/libs/*.jar
