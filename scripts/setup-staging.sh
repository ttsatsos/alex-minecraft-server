#!/bin/zsh
set -euo pipefail

SCRIPT_DIR="${0:A:h}"
ROOT="${SCRIPT_DIR:h}"
SERVER_DIR="${MINECRAFT_SERVER_DIR:-$ROOT/minecraft-server}"
RUNTIME_DIR="$ROOT/runtime"
TOOLS_DIR="$ROOT/tools"

PAPER_URL="https://fill-data.papermc.io/v1/objects/5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba/paper-1.21.11-132.jar"
PAPER_SHA256="5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba"
JDK_URL="https://api.adoptium.net/v3/binary/latest/21/ga/mac/aarch64/jdk/hotspot/normal/eclipse"
MAVEN_VERSION="3.9.16"
MAVEN_URL="https://downloads.apache.org/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz"
GEYSER_URL="https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot"
FLOODGATE_URL="https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot"
VIAVERSION_URL="https://ci.viaversion.com/job/ViaVersion/1430/artifact/build/libs/ViaVersion-5.11.1-SNAPSHOT.jar"
SKINSRESTORER_URL="https://cdn.modrinth.com/data/TsLS8Py5/versions/wXS6bHiC/SkinsRestorer.jar"
SKINSRESTORER_SHA512="7819f6b1e8f8ddb2e86d3d3e54352dd040f381e9a094f8a9c80c7d3273ffd7b1cef6eca7369dcee4b0f5290e7837ef51cee1baeca906b3784f30d7ba2f58b7b4"
VOICECHAT_URL="https://cdn.modrinth.com/data/9eGKb6K1/versions/62MVmInV/voicechat-bukkit-2.6.21.jar"
VOICECHAT_SHA512="12a0ff0240e12bda82c10f2277c7bf3016c2a1833f8e73f338a61601cb555a2b18832041c902c81c4f8dd781993994fbd60d0336f443d92ff887933141a6f5a7"

download() {
  local url="$1"
  local destination="$2"

  if [ -s "$destination" ]; then
    echo "[ok] $(basename "$destination") already exists"
    return
  fi

  echo "Downloading $(basename "$destination")..."
  curl --fail --location --retry 3 --output "$destination.part" "$url"
  mv "$destination.part" "$destination"
}

mkdir -p "$RUNTIME_DIR" "$TOOLS_DIR" "$SERVER_DIR/plugins"

if ! find "$RUNTIME_DIR" -maxdepth 1 -type d -name 'jdk-*' | grep -q .; then
  JDK_ARCHIVE="$RUNTIME_DIR/temurin-21.tar.gz"
  download "$JDK_URL" "$JDK_ARCHIVE"
  tar -xzf "$JDK_ARCHIVE" -C "$RUNTIME_DIR"
fi

if [ ! -x "$TOOLS_DIR/apache-maven-$MAVEN_VERSION/bin/mvn" ]; then
  MAVEN_ARCHIVE="$TOOLS_DIR/apache-maven-$MAVEN_VERSION-bin.tar.gz"
  download "$MAVEN_URL" "$MAVEN_ARCHIVE"
  tar -xzf "$MAVEN_ARCHIVE" -C "$TOOLS_DIR"
fi

download "$PAPER_URL" "$SERVER_DIR/paper.jar"
echo "$PAPER_SHA256  $SERVER_DIR/paper.jar" | shasum -a 256 -c -

download "$GEYSER_URL" "$SERVER_DIR/plugins/Geyser-Spigot.jar"
download "$FLOODGATE_URL" "$SERVER_DIR/plugins/Floodgate-Spigot.jar"
download "$VIAVERSION_URL" "$SERVER_DIR/plugins/ViaVersion.jar"
download "$SKINSRESTORER_URL" "$SERVER_DIR/plugins/SkinsRestorer.jar"
echo "$SKINSRESTORER_SHA512  $SERVER_DIR/plugins/SkinsRestorer.jar" | shasum -a 512 -c -
download "$VOICECHAT_URL" "$SERVER_DIR/plugins/SimpleVoiceChat.jar"
echo "$VOICECHAT_SHA512  $SERVER_DIR/plugins/SimpleVoiceChat.jar" | shasum -a 512 -c -

JAVA_HOME="$(find "$RUNTIME_DIR" -maxdepth 1 -type d -name 'jdk-*' | sort -V | tail -n 1)/Contents/Home"
MAVEN_BIN="$TOOLS_DIR/apache-maven-$MAVEN_VERSION/bin/mvn"

echo "Building StatStealSmp..."
JAVA_HOME="$JAVA_HOME" "$MAVEN_BIN" -q -f "$ROOT/statsteal-smp-plugin/pom.xml" clean package
PLUGIN_JAR="$(find "$ROOT/statsteal-smp-plugin/target" -maxdepth 1 -type f -name 'statsteal-smp-plugin-*.jar' | head -n 1)"
if [ -z "$PLUGIN_JAR" ]; then
  echo "StatStealSmp build completed without producing the expected JAR."
  exit 1
fi
cp "$PLUGIN_JAR" "$SERVER_DIR/plugins/StatStealSmp.jar"

echo "Building MonumentBuilder..."
JAVA_HOME="$JAVA_HOME" "$MAVEN_BIN" -q -f "$ROOT/monument-builder-plugin/pom.xml" clean package
MONUMENT_JAR="$(find "$ROOT/monument-builder-plugin/target" -maxdepth 1 -type f -name 'monument-builder-plugin-*.jar' | head -n 1)"
if [ -z "$MONUMENT_JAR" ]; then
  echo "MonumentBuilder build completed without producing the expected JAR."
  exit 1
fi
cp "$MONUMENT_JAR" "$SERVER_DIR/plugins/MonumentBuilder.jar"

if [ -n "${GEYSER_BIND_ADDRESS:-}" ]; then
  GEYSER_CONFIG="$SERVER_DIR/plugins/Geyser-Spigot/config.yml"
  python3 - "$GEYSER_CONFIG" "$GEYSER_BIND_ADDRESS" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
address = sys.argv[2]
lines = path.read_text().splitlines(keepends=True)
in_bedrock = False
updated = False
for index, line in enumerate(lines):
    if line == "bedrock:\n":
        in_bedrock = True
        continue
    if in_bedrock and line and not line[0].isspace() and not line.startswith("#"):
        break
    if in_bedrock and re.match(r"^\s+address:", line):
        indent = line[: len(line) - len(line.lstrip())]
        lines[index] = f"{indent}address: {address}\n"
        updated = True
        break
if not updated:
    raise SystemExit(f"Could not update bedrock.address in {path}")
text = "".join(lines)
text = re.sub(r"(?m)^(\s+mtu:)\s*\d+\s*$", r"\1 800", text, count=1)
path.write_text(text)
PY
fi

if [ -n "${VOICECHAT_BIND_ADDRESS:-}" ]; then
  VOICECHAT_CONFIG="$SERVER_DIR/plugins/voicechat/voicechat-server.properties"
  if [ -f "$VOICECHAT_CONFIG" ]; then
    python3 - "$VOICECHAT_CONFIG" "$VOICECHAT_BIND_ADDRESS" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
address = sys.argv[2]
text = path.read_text()
text = re.sub(r"(?m)^bind_address=.*$", f"bind_address={address}", text, count=1)
text = re.sub(r"(?m)^voice_host=.*$", f"voice_host={address}:24454", text, count=1)
path.write_text(text)
PY
  else
    echo "[note] Start the server once, then rerun setup to configure voice chat binding."
  fi
fi

echo
echo "Staging dependencies are ready."
echo "Geyser bind: ${GEYSER_BIND_ADDRESS:-unchanged}"
echo "Voice chat bind: ${VOICECHAT_BIND_ADDRESS:-unchanged}"
echo "Start with: caffeinate -s $ROOT/scripts/start-server.sh"
