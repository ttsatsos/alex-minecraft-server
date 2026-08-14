#!/bin/zsh
# LaunchAgent entrypoint. Use server-start.sh instead of running this directly.
set -euo pipefail

ROOT="/Users/alextsatsos1/minecraft"
SERVER_DIR="$ROOT/minecraft-server"
JAVA_BIN="$ROOT/runtime/jdk-21.0.10+7/Contents/Home/bin/java"
FIFO="$SERVER_DIR/console.fifo"

if lsof -nP -iTCP:25565 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "[launchd-server] TCP 25565 already in use; refusing to double-start." >&2
  exit 0
fi

rm -f "$FIFO"
/usr/bin/mkfifo "$FIFO"
chmod 600 "$FIFO"

# Keep one writer open so Java never sees EOF between console commands.
/usr/bin/tail -f /dev/null > "$FIFO" &
KEEPER_PID=$!
JAVA_PID=""

cleanup() {
  kill "$KEEPER_PID" 2>/dev/null || true
  wait "$KEEPER_PID" 2>/dev/null || true
  rm -f "$FIFO"
}

forward_stop() {
  if [ -n "$JAVA_PID" ]; then
    kill -TERM "$JAVA_PID" 2>/dev/null || true
  fi
}

trap cleanup EXIT
trap forward_stop INT TERM

cd "$SERVER_DIR"
"$JAVA_BIN" -Xms4G -Xmx8G -jar "$SERVER_DIR/paper.jar" --nogui < "$FIFO" &
JAVA_PID=$!

set +e
wait "$JAVA_PID"
STATUS=$?
if kill -0 "$JAVA_PID" 2>/dev/null; then
  wait "$JAVA_PID"
  STATUS=$?
fi
set -e

exit "$STATUS"
