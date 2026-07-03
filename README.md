# Minecraft Server Setup for This Mac mini

This Mac is an Apple silicon machine (`arm64`) running macOS 15.1.

The cleanest setup for Java + Bedrock cross-play on one machine is:

- `Paper` for the Java server
- `Geyser-Spigot` for Bedrock-to-Java translation
- `Floodgate-Spigot` so Bedrock players can join without owning Java

That combination keeps one shared world and one shared plugin-based server.

## Recommended version target

Because you are migrating an existing server, the safest path is:

1. Match the Minecraft version already used on the current host.
2. Match the plugin list from the current host.
3. Only upgrade versions after the migrated server is stable.

If your current server is on `1.20.x` through `1.21.11`, use `Java 21`.

If you intentionally want to move to the newer `26.1+` Minecraft/Paper line, use `Java 25` instead.

## Folder layout

Use this structure inside this project folder:

```text
help-us-set-up-a-minecraft/
├── README.md
├── launchd/
│   └── com.local.minecraft.paper.plist
├── minecraft-server/
│   ├── paper.jar
│   ├── eula.txt
│   ├── server.properties
│   ├── world/
│   └── plugins/
├── scripts/
│   ├── start-server.sh
│   └── check-server.sh
└── transfer/
    └── put-old-server-files-here.txt
```

## Step 1: Install Java

PaperMC's current macOS guidance recommends using Homebrew on macOS:

- Java install docs: <https://docs.papermc.io/misc/java-install/>

This Mac does not currently have Java or Homebrew installed.
The usual Homebrew path needs admin rights on this Mac, so this project now also includes a bundled local Java 21 runtime in:

```text
/Users/alextsatsos1/Documents/Codex/2026-04-26/help-us-set-up-a-minecraft/runtime/jdk-21.0.10+7
```

That means you can run the server from this project folder without installing system-wide Java.

Install Homebrew:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

Then install Java:

For a server staying on `1.20` through `1.21.11`:

```bash
brew install openjdk@21
```

For a server moving to `26.1+`:

```bash
brew install openjdk@25
```

After install, verify:

```bash
java -version
```

If you use the bundled runtime instead, no extra Java install step is required for `1.21.11`.

## Step 2: Create the server folder

```bash
mkdir -p "/Users/alextsatsos1/Documents/Codex/2026-04-26/help-us-set-up-a-minecraft/minecraft-server/plugins"
```

## Step 3: Download Paper

Official downloads:

- Paper downloads: <https://papermc.io/downloads/paper/>
- Paper docs: <https://docs.papermc.io/paper/admin/getting-started/>

Recommended for migration safety:

- Download the Paper build for the same Minecraft version your current host is using.
- Save it as:

```text
/Users/alextsatsos1/Documents/Codex/2026-04-26/help-us-set-up-a-minecraft/minecraft-server/paper.jar
```

## Step 4: First start

Run:

```bash
"/Users/alextsatsos1/Documents/Codex/2026-04-26/help-us-set-up-a-minecraft/scripts/start-server.sh"
```

The first launch will generate the base files and stop because of the EULA.

Edit:

```text
/Users/alextsatsos1/Documents/Codex/2026-04-26/help-us-set-up-a-minecraft/minecraft-server/eula.txt
```

Set:

```text
eula=true
```

## Step 5: Migrate the old server

Before copying anything, make a full backup of the old host.

Move or copy the old server contents into:

```text
/Users/alextsatsos1/Documents/Codex/2026-04-26/help-us-set-up-a-minecraft/minecraft-server/
```

Keep these local items in place:

- `paper.jar`
- `plugins/` folder
- the scripts in `/scripts`

Migration checklist:

1. Copy the main world folders:
   `world`, `world_nether`, `world_the_end` if they exist.
2. Copy `server.properties`, `ops.json`, `whitelist.json`, `banned-players.json`, `banned-ips.json`, `permissions.yml`, and any plugin config folders.
3. Copy existing plugins only after confirming they support the same Minecraft version you are running on Paper here.
4. If the old host used Spigot/Paper already, the move is usually straightforward.
5. If the old host used a different server type, stop and verify compatibility before copying plugin jars.

## Step 6: Add cross-play plugins

Official setup docs:

- Geyser Paper/Spigot setup: <https://geysermc.org/wiki/geyser/setup/self/paper-spigot/>
- Floodgate Paper/Spigot setup: <https://geysermc.org/wiki/floodgate/setup/paper-spigot/>
- Geyser config reference: <https://geysermc.org/wiki/geyser/understanding-the-config/>

Download and place these in:

```text
/Users/alextsatsos1/Documents/Codex/2026-04-26/help-us-set-up-a-minecraft/minecraft-server/plugins/
```

- `Geyser-Spigot.jar`
- `Floodgate-Spigot.jar`

Then start the server once so the plugin config files are generated.

## Step 7: Configure Geyser for Bedrock

Open:

```text
/Users/alextsatsos1/Documents/Codex/2026-04-26/help-us-set-up-a-minecraft/minecraft-server/plugins/Geyser-Spigot/config.yml
```

Make sure these values are set:

```yaml
bedrock:
  address: 0.0.0.0
  port: 19132
  clone-remote-port: false

remote:
  auth-type: floodgate
```

Notes:

- `19132/UDP` is the normal Bedrock port.
- `clone-remote-port` should stay `false` on a self-hosted Mac unless you intentionally need Bedrock to reuse the Java port.
- `auth-type: floodgate` is what allows Bedrock players to join without Java accounts.

## Step 8: Verify `server.properties`

Open:

```text
/Users/alextsatsos1/Documents/Codex/2026-04-26/help-us-set-up-a-minecraft/minecraft-server/server.properties
```

Recommended basics:

```text
server-port=25565
motd=Family Minecraft Server
enable-query=false
white-list=true
```

Keep `online-mode=true` unless you have a very specific reason not to. With Paper + Geyser + Floodgate on the same server, Java players should still authenticate normally.

## Step 9: Network setup

For players outside your house, forward both ports in your router:

- `25565/TCP` for Java
- `19132/UDP` for Bedrock

If macOS Firewall is enabled, allow incoming connections for Java when macOS prompts you.

Players connect like this:

- Java players: `your-public-ip-or-domain:25565`
- Bedrock players: `your-public-ip-or-domain`, port `19132`

On the same local network:

- Java: use the Mac's local LAN IP, port `25565`
- Bedrock: use the Mac's local LAN IP, port `19132`

## Step 10: Start automatically on reboot

There is a launchd template in:

```text
/Users/alextsatsos1/Documents/Codex/2026-04-26/help-us-set-up-a-minecraft/launchd/com.local.minecraft.paper.plist
```

To install it later:

```bash
mkdir -p ~/Library/LaunchAgents
cp "/Users/alextsatsos1/Documents/Codex/2026-04-26/help-us-set-up-a-minecraft/launchd/com.local.minecraft.paper.plist" ~/Library/LaunchAgents/
launchctl load -w ~/Library/LaunchAgents/com.local.minecraft.paper.plist
```

## Step 11: Sanity checks after migration

Run:

```bash
"/Users/alextsatsos1/Documents/Codex/2026-04-26/help-us-set-up-a-minecraft/scripts/check-server.sh"
```

Then confirm:

1. Java clients can join.
2. Bedrock clients can join.
3. Existing world loads correctly.
4. OPs, whitelist, and permissions carried over.
5. Plugins do not show compatibility errors.
6. Console does not show repeated chunk or entity upgrade failures.

## Notes for your exact situation

- Because you already have a server elsewhere, version matching matters more than "latest."
- On April 26, 2026, current Paper guidance says `1.20` through `1.21.11` should use `Java 21`, while `26.1+` should use `Java 25`.
- Geyser's current self-host docs note that self-hosted Bedrock access normally uses `19132/UDP`.
- If your existing server is older than `1.21.11`, current Geyser docs say you may also need `ViaVersion`.

If you want, the next step can be either:

1. I install Java and create the live server folder here on this Mac.
2. I help you pull the files off the existing host and migrate them into this layout.
