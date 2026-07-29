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

## Monument test site

MonumentBuilder generates large structures in small batches to keep the server
responsive. The first test structure is a roughly full-scale Statue of Liberty
on an eleven-point island base in the overworld at `544, 63, 928`.

Operators can use:

```text
/monument visit
/monument status
/monument undo
```

The undo command is available only during the same server session as the build.
A world backup must be made before each permanent monument build.

## Fantasy Magic Castle district

The overworld includes Silveran666's Fantasy Magic Castle map as a physical
chunk transplant. Players can visit it with:

```text
/castle
```

The imported map's original spawn is at `16000, 93, 16000`. Its generated
footprint spans approximately `15648..16703` on X and `15648..16559` on Z. It
was relocated by `1000` chunks on both axes with MCA Selector, including
terrain, entities, and points of interest.

Source: CurseForge project `1548133`, file `8115520`, Minecraft `1.21.4`.
The source map is marked All Rights Reserved. Keep the downloaded and merged
world data out of Git, and do not redistribute the merged world without the
creator's permission.

Cold backup immediately before the import:
`backups/pre-fantasy-castle-20260728-173710.tar.gz`.

## Cape compatibility test

SkinsRestorer is installed server-side for Java/Floodgate cape testing. Its
standard cape support copies a Mojang-signed player profile, which changes both
the skin and cape. From the game, temporarily apply a known profile with:

```text
/skin set Dinnerbone
```

Use `/skin clear` to restore the player's normal account skin. Confirm the test
from both a Java client and a Bedrock client before relying on cape visibility.
