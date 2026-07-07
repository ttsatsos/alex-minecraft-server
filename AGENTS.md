# AGENTS.md — Handoff for Codex (and other agents)

Project: local Java + Bedrock cross-play Minecraft on a **Mac mini** (Apple
Silicon, macOS 15.1). Paper + Geyser + Floodgate give one shared world that both
Java and Bedrock/iOS players can join.

## Current status: WORKING (local)

As of 2026-07-04, cross-play works end-to-end on the LAN. A Java client and a
Bedrock iPhone (`1.26.20`, iOS 26.5) can both join. The "Bedrock can ping but not
join" bug is **fixed** (see "The fix"), and the mini's IP is now **pinned to
`192.168.4.59` via an eero DHCP reservation** so it won't drift.

Open items:
- **Remote access for friends outside the LAN** is planned via **playit.gg**
  (free UDP tunnel, no port-forwarding, nothing for friends to install, home
  network stays sealed). Not set up yet. Port-forwarding was rejected (exposes the
  network); Tailscale was rejected (friends would have to install it).
- Run the server with `caffeinate -s` (+ `screen`) so it survives sleep and a
  closed terminal — see "Operating the servers". launchd is a trap (INVARIANT 3).

## The two servers

| Server | Directory | Java (TCP) | Bedrock/Geyser (UDP) |
|---|---|---|---|
| Live "StatSteal SMP" | `minecraft-server/` | `192.168.4.59:25565` | `192.168.4.59:19132` |
| Clean test (debug only) | `bedrock-clean-test-server/` | `192.168.4.59:25566` | `192.168.4.59:19133` |

- Mac mini LAN IP: **`192.168.4.59`** (gateway `192.168.4.1`).
- The mini is **multi-homed**: Wi-Fi `192.168.4.59` **and** a **Tailscale**
  `utun` interface (`100.x`). This matters — see root cause.
- Java runs from a **bundled JDK** at `runtime/jdk-21.0.10+7` (no system Java).
- Plugins on both: `Geyser-Spigot`, `floodgate`, `ViaVersion`. Paper 1.21.11.

## Environment constraints (important)

- **No admin / no `sudo`** on this Mac mini (it belongs to the owner's son).
  So: no `tcpdump`, no `pfctl`, no system LaunchDaemons, no Homebrew installs.
  Everything must run as the normal user with the bundled JDK.
- macOS Application Firewall is **disabled** on the mini (checked).
- Runtime state (`logs/`, `world/`, `*.jar`, `runtime/`, Floodgate `key.pem`,
  etc.) is **git-ignored** — see `.gitignore`. Only source/config is tracked.

## The bug and the fix (what this handoff is really about)

**Symptom.** Bedrock/iOS clients could *ping* the server (it appeared in the
list with correct MOTD) but joining failed at the RakNet transport phase with
`NetherNet / InitialConnection-13`. Geyser logged repeated "tried to connect!"
and never reached Bedrock login/Floodgate. Java clients were unaffected (TCP).

**Root cause.** Geyser's `bedrock.address` was `0.0.0.0`. On this **multi-homed**
mini, that made Geyser/Netty open a **dual-stack IPv6 wildcard socket**
(`lsof` showed `IPv6 UDP *:19132`). It *received* off-box RakNet requests fine
but sent replies with the wrong source address / out the wrong interface (Wi-Fi
vs Tailscale `utun`), so LAN Bedrock clients never saw the reply and the
handshake looped. The mini talking to *itself* always worked because that's
loopback — which is why every local test passed while every off-box client
failed.

**The fix.** In each server's `plugins/Geyser-Spigot/config.yml`, set the
Bedrock bind to the explicit LAN IPv4 (do **not** use `0.0.0.0` on this host):

```yaml
bedrock:
  address: 192.168.4.59   # was 0.0.0.0 — pin to the LAN IPv4, not the wildcard
  port: 19132             # 19133 for the clean-test server
```

Then restart the server. Confirm the boot log prints
`Started Geyser on 192.168.4.59:19132` (not `0.0.0.0:...`) and that
`lsof -nP -iUDP:19132` shows **`IPv4 192.168.4.59:19132`** (not `IPv6 *:...`).
This is committed for both servers on branch
`claude/minecraft-bedrock-geyser-connection-hvj1tj`.

**How it was proven.** A same-subnet laptop could ICMP-ping the mini and
complete a full RakNet handshake to a *public* Bedrock server, but got zero
replies from the mini's Bedrock port — until the bind was pinned to IPv4, after
which the same laptop (and the iPhone) connected. The only variable was the
`address` line.

**Alternative fix** (if you ever need `0.0.0.0` back): force an IPv4 stack with
the JVM flag `-Djava.net.preferIPv4Stack=true` in the start scripts. Pinning the
IP is simpler and is what was verified.

## INVARIANTS — do not regress these

1. **Never set Geyser `bedrock.address` back to `0.0.0.0` on this host.** Keep it
   pinned to `192.168.4.59`. That is the fix.
2. **The mini needs a stable IP — pinned via an eero DHCP reservation to
   `192.168.4.59`.** If the lease ever drifts (it did once, jumping to `.93`),
   Geyser can't bind Bedrock to `.59` and every client entry breaks. To force the
   mini back onto the reserved IP WITHOUT admin: **toggle Wi-Fi off/on from the
   menu bar** (`ipconfig set en1 DHCP` needs admin, which we don't have). Verify
   with `ipconfig getifaddr en1` — **Wi-Fi is `en1` on this machine, not en0**.
3. **NEVER run the servers via launchd on this box.** Two independent reasons:
   (a) macOS TCC blocks launchd jobs from reading `~/Documents`, so the LaunchAgent
   crash-loops with exit 127; (b) far worse — the LaunchAgent has `KeepAlive`, so it
   **respawns the server every time it's killed**, which caused a multi-hour loop of
   "Address already in use" / `session.lock` errors that looked like the server just
   wouldn't start. If starts keep failing that way, check
   `launchctl list | grep -i minecraft`; if `com.local.minecraft.paper` shows up,
   it's the culprit — remove it:
   `launchctl bootout gui/$(id -u)/com.local.minecraft.paper && rm ~/Library/LaunchAgents/com.local.minecraft.paper.plist`.
   For headless, use `caffeinate -s` (+ `screen`), NOT launchd.
4. Don't commit runtime state or secrets (see `.gitignore`); never commit
   `plugins/floodgate/key.pem`.

## Operational gotchas (hard-won — read before touching the server)

These caused most of the multi-hour debugging spiral. Internalize them.

- **Exactly ONE thing may start the server.** Two starters (two agents at once, or
  a KeepAlive launchd agent) → port + world-lock collisions that look like the
  server is broken. Before ANY start: `pgrep -fl paper.jar` shows no server (the
  long `net.minecraft.client` game line is fine), and
  `lsof -nP -iTCP:25565 -iUDP:19132` is empty.
- **Always launch with `caffeinate -s` to prevent sleep.** If the Mac sleeps, the
  server is *suspended*: it keeps holding the ports + world lock but stops serving
  — a "zombie" that both blocks new starts AND rejects clients (looks like
  `InitialConnection-13`). No admin needed. To keep an already-running server awake
  without restarting it: `caffeinate -dimsu -w <server-pid>` in a spare terminal.
- **`session.lock: already locked` / "Address already in use"** = an instance is
  already running, or a respawner is restarting it. Do NOT just `rm session.lock`.
  Find and kill the *source* first: `lsof -nP -iTCP:25565` (PID on the Java port),
  `pgrep -fl paper.jar`, and `launchctl list | grep -i minecraft` (the launchd
  respawner). Kill/bootout the source, confirm the port stays empty on a re-check,
  THEN `rm minecraft-server/world/session.lock` and start once.
- **Clean restart recipe:** stop all sources → `pgrep -fl paper.jar` empty →
  `lsof -nP -iTCP:25565 -iUDP:19132` empty → `rm -f minecraft-server/world/session.lock`
  → `caffeinate -s ./scripts/start-server.sh` → wait for `Setting MTU to 800`,
  `Started Geyser on 192.168.4.59:19132`, and `Done (…)!` **before** testing a
  client. A mid-boot or zombie server gives `InitialConnection-13` even though the
  port looks bound.

## Operating the servers

Start (foreground, interactive console):

```bash
./scripts/start-server.sh          # live server (minecraft-server, 19132/25565)
# clean-test server (bedrock-clean-test-server, 19133/25566):
cd bedrock-clean-test-server && ../runtime/jdk-21.0.10+7/Contents/Home/bin/java -Xms1G -Xmx2G -jar paper.jar --nogui
```

Run the **live server headless** (survives closing the terminal, stays awake).
The mini sleeping is the #1 cause of "it stopped," so always wrap with
`caffeinate -s`. Best combo is **`screen` + `caffeinate`** (foreground-like
behavior, detachable, survives closing the window):

```bash
screen -S mc
# inside the screen session:
cd /Users/alextsatsos1/Documents/Codex/2026-04-26/help-us-set-up-a-minecraft
caffeinate -s ./scripts/start-server.sh
# wait for "Started Geyser on 192.168.4.59:19132", then press Ctrl-A then D to detach
```

Reattach with `screen -r mc`; stop by reattaching and typing `stop`. If `screen`
isn't installed, `caffeinate -s ./scripts/start-server.sh` in a Terminal window
you leave open also works (survives nothing if the window closes, but stays awake).
Both survive closing the window / sleep but **not** a reboot/logout.

**DO NOT use the launchd LaunchAgent here — it fails AND respawns (see INVARIANT
3).** The plist `launchd/com.local.minecraft.paper.plist` (a) can't read the start
script because the project is under `~/Documents/` (macOS TCC → exit 127), and (b)
with `KeepAlive` it restarts the server every time it's killed, producing an
endless "Address already in use" / `session.lock` loop. Recover with:
`launchctl bootout gui/$(id -u)/com.local.minecraft.paper && rm ~/Library/LaunchAgents/com.local.minecraft.paper.plist`.
launchd only becomes viable if the project is moved out of `~/Documents`
(e.g. `/Users/Shared/minecraft`) or admin is obtained.

## Diagnostic tools

- `scripts/bedrock-ping.py HOST PORT` — RakNet unconnected ping (proves the port
  is open + Geyser advertises). Passing this proves little on its own.
- `scripts/raknet-connect-test.py HOST PORT` — **staged** RakNet handshake probe
  (Ping -> OCR1/Reply1 -> OCR2/Reply2), handles the modern security cookie, and
  reports exactly which stage stalls + the negotiated MTU. Run it from the mini
  AND from another device on the same Wi-Fi to split "server/software" from
  "network path". This is what localized the dual-stack bug.
- `lsof -nP -iUDP:19132 -iUDP:19133 -iTCP:25565 -iTCP:25566` — who's listening,
  and (critically) `IPv4` vs `IPv6` and the bound address.

## Known non-issues / gotchas seen during debugging

- **`U-000` on the Bedrock client** = a stale/duplicate entry in the phone's
  Servers list (client-side), not a server problem. Delete the old entry,
  restart the app, re-add a single clean entry.
- **A device stuck on "Locating server…" forever** = iOS **Local Network**
  permission is off for Minecraft on *that device*, or it's on a different/guest
  Wi-Fi. Settings → Minecraft → Local Network → on (toggle off/on to reset);
  confirm the device is on the same SSID and has a `192.168.4.x` IP. It's
  per-device, so one phone can fail while others work.
- **MTU matters for flaky-Wi-Fi Bedrock clients.** The iOS `1.26.20` phone only
  completes the handshake at `advanced.bedrock.mtu: 800` (+ `compression-level:
  -1`); MTU 1000 fails for it. Keep the live server at MTU 800.
- **Newest Bedrock version can be incompatible.** If only the device on the very
  newest Bedrock (e.g. `1.26.32`, `RakNet:1001`) fails while older ones work, it
  may be a Geyser-vs-newest-Bedrock bug — update `Geyser-Spigot.jar` to the latest
  build from download.geysermc.org. (Verify it's actually the version and not a
  per-device network/permission issue first.)
- **"NetherNet" codeword** on the error screen is generic in 26.x Bedrock; the
  real transport is RakNet (`Transport: RakNet:975`). Don't chase NetherNet.
- **Tailscale is fine to keep running** — it's not blocking; it only mattered
  because it made the host multi-homed, which the IPv4 bind resolves.
- `enforce-secure-profile=true` is set in `server.properties`. Bedrock players
  currently join fine, but if a future change breaks Bedrock at the *Java login*
  phase (after the handshake succeeds), setting it to `false` is the known fix.
- `scripts/start-clean-server.sh` targets `minecraft-server-clean/` (git-ignored),
  NOT `bedrock-clean-test-server/`. Start the clean-test server with the direct
  `java` command above, not that script.

## Full narrative

`docs/BEDROCK_TROUBLESHOOTING.md` has the complete diagnostic journey, the
findings log, and the RESOLVED writeup with all the evidence.
