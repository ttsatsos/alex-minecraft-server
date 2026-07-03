# AGENTS.md — Handoff for Codex (and other agents)

Project: local Java + Bedrock cross-play Minecraft on a **Mac mini** (Apple
Silicon, macOS 15.1). Paper + Geyser + Floodgate give one shared world that both
Java and Bedrock/iOS players can join.

## Current status: WORKING

As of 2026-07-03, cross-play works end-to-end on both servers. A Java client and
a Bedrock iPhone (`1.26.20`, iOS 26.5) can both join. The long-standing "Bedrock
can ping but not join" bug is **fixed** — see "The fix" below.

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
2. **The mini needs a stable IP.** The fix hard-codes `192.168.4.59`. A DHCP
   reservation for the mini at that address is required (TODO for the owner in
   the mesh app). If the lease changes, both servers break identically until the
   `address` lines are updated.
3. Don't commit runtime state or secrets (see `.gitignore`); never commit
   `plugins/floodgate/key.pem`.

## Operating the servers

Start (foreground, interactive console):

```bash
./scripts/start-server.sh          # live server (minecraft-server, 19132/25565)
# clean-test server (bedrock-clean-test-server, 19133/25566):
cd bedrock-clean-test-server && ../runtime/jdk-21.0.10+7/Contents/Home/bin/java -Xms1G -Xmx2G -jar paper.jar --nogui
```

Run the **live server headless** (survives closing the terminal) via a **user
LaunchAgent** — no admin needed. The plist is `launchd/com.local.minecraft.paper.plist`
(`RunAtLoad` + `KeepAlive`):

```bash
mkdir -p ~/Library/LaunchAgents
cp launchd/com.local.minecraft.paper.plist ~/Library/LaunchAgents/
launchctl load ~/Library/LaunchAgents/com.local.minecraft.paper.plist
# stop it (KeepAlive means a plain `kill` just respawns it):
launchctl unload ~/Library/LaunchAgents/com.local.minecraft.paper.plist
```

Caveats: a user LaunchAgent only runs while the user is logged in; for
auto-start after reboot, enable automatic login (needs admin) and disable sleep
in Energy settings. Logs go to `minecraft-server/launchd-stdout.log`.

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
