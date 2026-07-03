#!/bin/zsh
set -euo pipefail

ROOT="/Users/alextsatsos1/Documents/Codex/2026-04-26/help-us-set-up-a-minecraft"
BOT_DIR="$ROOT/ai-bot"
NODE_BIN="/Applications/Codex.app/Contents/Resources/node"
ENTRYPOINT="$BOT_DIR/src/index.js"

if [ ! -x "$NODE_BIN" ]; then
  echo "Missing Node runtime at $NODE_BIN"
  exit 1
fi

if [ ! -f "$BOT_DIR/.env" ]; then
  echo "Missing $BOT_DIR/.env"
  echo "Copy .env.example to .env and fill in the bot account settings first."
  exit 1
fi

if [ ! -f "$ENTRYPOINT" ]; then
  echo "Missing bot entrypoint at $ENTRYPOINT"
  exit 1
fi

cd "$BOT_DIR"
exec "$NODE_BIN" "$ENTRYPOINT"
