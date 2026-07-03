#!/bin/zsh
set -euo pipefail

ROOT="/Users/alextsatsos1/Documents/Codex/2026-04-26/help-us-set-up-a-minecraft"
SERVER_DIR="$ROOT/minecraft-server"
LATEST_RUNTIME_DIR="$(printf '%s\n' "$ROOT"/runtime/jdk-* 2>/dev/null | sort -V | tail -n 1)"
LOCAL_JAVA="${LATEST_RUNTIME_DIR}/Contents/Home/bin/java"

echo "Checking expected server files..."

for path in \
  "$SERVER_DIR" \
  "$SERVER_DIR/plugins" \
  "$SERVER_DIR/paper.jar"
do
  if [ -e "$path" ]; then
    echo "[ok] $path"
  else
    echo "[missing] $path"
  fi
done

if [ -x "$LOCAL_JAVA" ]; then
  echo "[ok] Bundled Java found:"
  "$LOCAL_JAVA" -version
elif command -v java >/dev/null 2>&1; then
  echo "[ok] System Java found:"
  java -version
else
  echo "[missing] Java is not installed and bundled Java was not found"
fi

echo
echo "Cross-play plugin checks:"

for path in \
  "$SERVER_DIR/plugins/Geyser-Spigot.jar" \
  "$SERVER_DIR/plugins/Floodgate-Spigot.jar"
do
  if [ -e "$path" ]; then
    echo "[ok] $path"
  else
    echo "[missing] $path"
  fi
done

echo
echo "Expected network ports:"
echo "- Java: 25565/TCP"
echo "- Bedrock: 19132/UDP"
