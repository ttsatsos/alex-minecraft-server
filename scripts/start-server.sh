#!/bin/zsh
set -euo pipefail

ROOT="/Users/alextsatsos1/Documents/Codex/2026-04-26/help-us-set-up-a-minecraft"
SERVER_DIR="$ROOT/minecraft-server"
JAR_PATH="$SERVER_DIR/paper.jar"
LOCAL_JAVA="$ROOT/runtime/jdk-21.0.10+7/Contents/Home/bin/java"

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

if [ ! -d "$SERVER_DIR" ]; then
  echo "Creating server directory at $SERVER_DIR"
  mkdir -p "$SERVER_DIR/plugins"
fi

if [ ! -f "$JAR_PATH" ]; then
  echo "Missing $JAR_PATH"
  echo "Download Paper and save it as paper.jar in $SERVER_DIR"
  exit 1
fi

cd "$SERVER_DIR"

exec "$JAVA_BIN" -Xms4G -Xmx8G -jar "$JAR_PATH" --nogui
