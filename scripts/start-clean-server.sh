#!/bin/zsh
set -euo pipefail

ROOT="/Users/alextsatsos1/Documents/Codex/2026-04-26/help-us-set-up-a-minecraft"
SERVER_DIR="$ROOT/minecraft-server-clean"
JAR_PATH="$SERVER_DIR/paper.jar"
LATEST_RUNTIME_DIR="$(printf '%s\n' "$ROOT"/runtime/jdk-* 2>/dev/null | sort -V | tail -n 1)"
LOCAL_JAVA="${LATEST_RUNTIME_DIR}/Contents/Home/bin/java"

JAVA_BIN="${JAVA_HOME:-}"
if [ -n "$JAVA_BIN" ] && [ -x "$JAVA_BIN/bin/java" ]; then
  JAVA_BIN="$JAVA_BIN/bin/java"
elif [ -x "$LOCAL_JAVA" ]; then
  JAVA_BIN="$LOCAL_JAVA"
else
  JAVA_BIN="$(command -v java || true)"
fi

if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
  echo "Java is not installed or not in PATH."
  echo "Expected bundled Java at $LOCAL_JAVA"
  exit 1
fi

cd "$SERVER_DIR"
exec "$JAVA_BIN" -Xms2G -Xmx4G -jar "$JAR_PATH" --nogui
