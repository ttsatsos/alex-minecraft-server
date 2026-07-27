#!/bin/zsh
set -euo pipefail

SCRIPT_DIR="${0:A:h}"
ROOT="${SCRIPT_DIR:h}"
SERVER_DIR="${MINECRAFT_SERVER_DIR:-$ROOT/minecraft-server}"
LATEST_RUNTIME_DIR="$(find "$ROOT/runtime" -maxdepth 1 -type d -name 'jdk-*' 2>/dev/null | sort -V | tail -n 1)"
LOCAL_JAVA="${LATEST_RUNTIME_DIR:+$LATEST_RUNTIME_DIR/Contents/Home/bin/java}"
JAVA_PORT="${JAVA_PORT:-25565}"
BEDROCK_PORT="${BEDROCK_PORT:-19132}"
VOICE_PORT="${VOICE_PORT:-24454}"

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
  "$SERVER_DIR/plugins/Floodgate-Spigot.jar" \
  "$SERVER_DIR/plugins/ViaVersion.jar" \
  "$SERVER_DIR/plugins/SkinsRestorer.jar" \
  "$SERVER_DIR/plugins/SimpleVoiceChat.jar"
do
  if [ -e "$path" ]; then
    echo "[ok] $path"
  else
    echo "[missing] $path"
  fi
done

echo
echo "Expected network ports:"
echo "- Java: $JAVA_PORT/TCP"
echo "- Bedrock: $BEDROCK_PORT/UDP"
echo "- Java voice chat: $VOICE_PORT/UDP"
