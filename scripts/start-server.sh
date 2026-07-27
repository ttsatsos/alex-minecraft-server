#!/bin/zsh
set -euo pipefail

SCRIPT_DIR="${0:A:h}"
ROOT="${SCRIPT_DIR:h}"
SERVER_DIR="${MINECRAFT_SERVER_DIR:-$ROOT/minecraft-server}"
JAR_PATH="$SERVER_DIR/paper.jar"
LATEST_RUNTIME_DIR="$(find "$ROOT/runtime" -maxdepth 1 -type d -name 'jdk-*' 2>/dev/null | sort -V | tail -n 1)"
LOCAL_JAVA="${LATEST_RUNTIME_DIR:+$LATEST_RUNTIME_DIR/Contents/Home/bin/java}"
MC_XMS="${MC_XMS:-2G}"
MC_XMX="${MC_XMX:-6G}"

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
  echo "Expected a bundled Java runtime under $ROOT/runtime/jdk-*"
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

exec "$JAVA_BIN" -Xms"$MC_XMS" -Xmx"$MC_XMX" -jar "$JAR_PATH" --nogui
