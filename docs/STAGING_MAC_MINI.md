# Mac mini staging server

This staging copy is separate from Alex's live server and world. It keeps Java,
Maven, Paper, and plugin JARs inside the repository directory, so it does not
need Homebrew or system-wide installation.

## Bootstrap

From the repository root on the staging Mac mini:

```zsh
GEYSER_BIND_ADDRESS=100.79.13.24 \
VOICECHAT_BIND_ADDRESS=100.79.13.24 \
./scripts/setup-staging.sh
```

Use the mini's current Tailscale IPv4 address when testing remotely. Geyser must
bind an explicit IPv4 address on a multi-homed Mac; do not use `0.0.0.0`.

## Start and stop

```zsh
screen -S minecraft-staging
caffeinate -s ./scripts/start-server.sh
```

Detach with `Ctrl-A`, then `D`. Reattach with:

```zsh
screen -r minecraft-staging
```

At the Minecraft console, run `stop` before changing plugins or copying worlds.

## Remote test addresses

- Java Edition: `100.79.13.24:25565`
- Bedrock Edition over Tailscale: `100.79.13.24`, port `19132`
- Simple Voice Chat for Java: `100.79.13.24`, UDP port `24454`

The Tailscale app must be active on both the client and the mini.

## Java voice chat

Simple Voice Chat is installed on the Paper server. Java players who want voice
must launch Minecraft `1.21.11` with Fabric Loader and the matching Simple Voice
Chat Fabric client mod. It uses the existing Minecraft account and does not need
a separate voice service login.

The tested client combination is Fabric Loader `0.19.3` with Simple Voice Chat
`fabric-1.21.11-2.6.21`. Keep the voice mod in a dedicated game directory rather
than the launcher's shared `mods` directory so other Minecraft profiles remain
unchanged. On Tom's MacBook, this is the `StatSteal Voice Chat` launcher profile
using `~/Library/Application Support/minecraft/statsteal-voice`.

The voice server binds to the mini's Tailscale address on `24454/UDP`. Each Java
client must be connected to Tailscale so it can reach that address. Bedrock
players can continue using the server normally, but they cannot join voice chat.

## Cape compatibility test

SkinsRestorer is installed server-side for Java/Floodgate cape testing. Its
standard cape support copies a Mojang-signed player profile, which changes both
the skin and cape. From the game, temporarily apply a known profile with:

```text
/skin set Dinnerbone
```

Use `/skin clear` to restore the player's normal account skin. Confirm the test
from both a Java client and a Bedrock client before relying on cape visibility.
