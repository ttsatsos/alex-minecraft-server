# CLAUDE.md — Session handoff (Minecraft cross-play server on Alex's Mac mini)

**You are now running as Claude Code directly ON the Mac mini**, so unlike the
prior (cloud/web) session you can execute commands here directly. That prior
session could not reach this machine, which is why everything was done by
copy-paste — that limitation is gone now.

## Read these first
- **`AGENTS.md`** — full project context, the root-cause of the main bug, the
  INVARIANTS, and the hard-won operational gotchas. **Read it before touching the
  server.**
- **`docs/BEDROCK_TROUBLESHOOTING.md`** — the complete diagnostic narrative and
  evidence, including the RESOLVED writeup.

## What this is
Local Java + Bedrock cross-play Minecraft on this Mac mini (Apple Silicon, macOS
15.1). Paper + Geyser + Floodgate = one shared world for Java and Bedrock/iOS
players. **No admin / no sudo** on this machine; Java is the bundled JDK at
`runtime/jdk-21.0.10+7`.

Live server: `minecraft-server/` — Java `192.168.4.59:25565`, Bedrock
`192.168.4.59:19132`. (A `bedrock-clean-test-server/` on `25566`/`19133` exists
for debugging; not needed normally.)

## Current state (2026-07-04): WORKING locally
- Bedrock cross-play works on the LAN; Java and Bedrock/iOS both join.
- **Main bug (Bedrock pings but can't join / `InitialConnection-13`) is fixed:**
  it was a dual-stack IPv6 bind on this multi-homed (Wi-Fi + Tailscale) mini.
  Fixed by pinning Geyser `bedrock.address` to `192.168.4.59` (never `0.0.0.0`).
- **The mini's IP is pinned to `192.168.4.59` via an eero DHCP reservation** (it
  drifted to `.93` mid-session and broke everything; that's now prevented).
- The server should be running via `caffeinate -s ./scripts/start-server.sh`.

## The rules that cost hours of debugging (full detail in AGENTS.md)
1. **Exactly ONE thing starts the server, ever.** Two starters (two agents, or a
   KeepAlive launchd agent) → port/world-lock collisions that look like the server
   is broken. Always check `pgrep -fl paper.jar` and
   `lsof -nP -iTCP:25565 -iUDP:19132` before starting.
2. **NEVER use launchd on this box.** The `com.local.minecraft.paper` LaunchAgent
   both fails (macOS TCC can't read scripts under `~/Documents` → exit 127) AND its
   `KeepAlive` **respawns the server on every kill**, causing endless "Address
   already in use" / `session.lock` loops. If it reappears:
   `launchctl list | grep -i minecraft`, then
   `launchctl bootout gui/$(id -u)/com.local.minecraft.paper && rm ~/Library/LaunchAgents/com.local.minecraft.paper.plist`.
3. **Always launch with `caffeinate -s`** — otherwise the Mac sleeps and the
   server becomes a zombie that holds the ports but rejects clients.
4. **Keep `bedrock.address: 192.168.4.59` and `advanced.bedrock.mtu: 800`** in
   `minecraft-server/plugins/Geyser-Spigot/config.yml`.

## Verify health quickly
```bash
ipconfig getifaddr en1              # expect 192.168.4.59 (Wi-Fi is en1, not en0)
pgrep -fl paper.jar                 # expect ONE server (the game client line is separate)
lsof -nP -iTCP:25565 -iUDP:19132    # expect TCP *:25565 and UDP 192.168.4.59:19132
```
Clean-restart recipe is in AGENTS.md ("Operational gotchas").

## Remaining work — the owner's two priorities, in order

### Priority 1: Run the server WITHOUT a terminal window (headless / persistent)
The owner does not want to launch it from Terminal each time. Options, simplest
first — pick with the owner:
- **Quick win — `screen` + `caffeinate`:** run inside `screen` (window can close)
  wrapped in `caffeinate -s` (sleep won't kill it). Survives closing the window and
  sleep, but NOT a reboot, and won't auto-restart on crash.
- **Proper — move the project out of `~/Documents`, then a user LaunchAgent:**
  launchd is blocked ONLY because the project lives under `~/Documents` (macOS TCC).
  Move it to e.g. `~/minecraft` or `/Users/Shared/minecraft` (TCC does not protect
  those), update the hardcoded paths in `scripts/*.sh` and the plist, then install a
  **user** LaunchAgent in `~/Library/LaunchAgents/` (no admin) with `RunAtLoad` +
  `KeepAlive` — that's true no-terminal auto-start + auto-restart. For reboot
  survival also enable auto-login and disable sleep. Keep `caffeinate -s` in the
  launch. **Verify it actually serves** (the old agent silently failed — confirm
  `lsof` shows `192.168.4.59:19132`). Before installing: kill any running server
  and remove the old broken `com.local.minecraft.paper` agent (INVARIANT 3), and
  keep it to ONE instance.

### Priority 2: Remote access for friends outside the LAN — set up playit.gg
Chosen for the owner's constraints: free, **nothing for friends to install**, opens
**no ports** on the router (home network stays sealed), works behind CGNAT.
Steps: free playit.gg account → install the playit **agent program** (NOT the
plugin — the plugin has no UDP) on the mini → create a **Java TCP** tunnel to
`192.168.4.59:25565` and a **Minecraft Bedrock (UDP)** tunnel to
`192.168.4.59:19132` → give friends the tunnel addresses (no install their side).
Official guide: https://geysermc.org/wiki/geyser/playit-gg/
Then **enable the whitelist before sharing** (`whitelist on`, add friends' Java +
Bedrock usernames) so only approved players can join.

## Owner's constraints / preferences
- No admin on this mini; free solutions only; friends must not install anything;
  home network must stay secure; low maintenance.
- The mini must stay awake (`caffeinate`) and powered on to serve.
- All fixes + docs are on branch
  `claude/minecraft-bedrock-geyser-connection-hvj1tj`.
