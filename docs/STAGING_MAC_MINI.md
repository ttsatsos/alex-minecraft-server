# Mac mini staging server

This staging copy is separate from Alex's live server and world. It keeps Java,
Maven, Paper, and plugin JARs inside the repository directory, so it does not
need Homebrew or system-wide installation.

## Bootstrap

From the repository root on the staging Mac mini:

```zsh
GEYSER_BIND_ADDRESS=100.79.13.24 ./scripts/setup-staging.sh
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

The Tailscale app must be active on both the client and the mini.

## Cape compatibility test

SkinsRestorer is installed server-side for Java/Floodgate cape testing. Its
standard cape support copies a Mojang-signed player profile, which changes both
the skin and cape. From the game, temporarily apply a known profile with:

```text
/skin set Dinnerbone
```

Use `/skin clear` to restore the player's normal account skin. Confirm the test
from both a Java client and a Bedrock client before relying on cape visibility.
