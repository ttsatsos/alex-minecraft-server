#!/bin/zsh
set -euo pipefail

ROOT="/Users/alextsatsos1/Documents/Codex/2026-04-26/help-us-set-up-a-minecraft"
OLLAMA_BIN="$ROOT/.local-tools/Ollama.app/Contents/Resources/ollama"
OLLAMA_DATA="$ROOT/ollama-data"

if [ ! -x "$OLLAMA_BIN" ]; then
  echo "Missing Ollama binary at $OLLAMA_BIN"
  exit 1
fi

mkdir -p "$OLLAMA_DATA"

export OLLAMA_MODELS="$OLLAMA_DATA/models"
export OLLAMA_HOST="127.0.0.1:11434"

exec "$OLLAMA_BIN" serve
