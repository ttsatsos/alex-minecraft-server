# Bedrock / Geyser Troubleshooting Handoff

This project hosts a local Paper Minecraft server on a Mac mini with Geyser + Floodgate for Bedrock/iOS cross-play.

## Current Servers

Live StatSteal SMP:

- Directory: `minecraft-server`
- Java: `192.168.4.59:25565`
- Bedrock: `192.168.4.59:19132`
- Geyser config: `minecraft-server/plugins/Geyser-Spigot/config.yml`
- Current Geyser MTU: `1000`

Clean Bedrock test server:

- Directory: `bedrock-clean-test-server`
- Java: `192.168.4.59:25566`
- Bedrock: `192.168.4.59:19133`
- Geyser config: `bedrock-clean-test-server/plugins/Geyser-Spigot/config.yml`
- Current Geyser bind address: `0.0.0.0`
- Current Geyser MTU: `800`
- Current Bedrock compression level: `-1`

## What Works

- Java clients can connect to the server.
- Bedrock ping works locally from the Mac using a synthetic RakNet unconnected ping.
- Both Geyser listeners advertise successfully:
  - `19132`: `MCPE;StatSteal SMP;1001;26.32;...`
  - `19133`: `MCPE;Clean Bedrock Test;1001;26.32;...`
- Geyser logs show the iOS device reaches the server and starts connecting.

## Current Failure

The iOS Bedrock client fails during early connection with:

- Codeword: `NetherNet`
- Error detail: `InitialConnection-13`
- Client examples observed:
  - `Version: 1.26.20-iPhone14,4`
  - `Transport: RakNet:975`
  - `NetworkType: 2`
  - `Connected: Wifi`

Clean test server logs show repeated early RakNet attempts, for example:

```text
/192.168.4.69:63821 has pinged you!
/192.168.4.69:49852 tried to connect!
/192.168.4.69:49852 tried to connect!
```

There is no Bedrock username, Floodgate login, or successful join after those lines, which suggests the connection dies before the normal Bedrock login/session phase.

## Important Things Already Checked

- This is not simply the wrong address or port. The server receives traffic from the iOS device.
- This is not a basic LAN reachability issue. A clean server hosted on the same Mac has been reachable before, and Geyser receives pings/connect attempts.
- This is not obviously an unsupported Bedrock version problem. The installed Geyser jar includes support for multiple 26.x versions, including `26.20`, `26.30`, `26.31`, and `26.32`.
- Geyser was updated to `2.10.1-b1175 (git-master-ed861ee)`.
- Floodgate is installed.
- ViaVersion is installed on the test servers.

## Diagnosis (2026-07-03)

The failure is in the **RakNet/UDP transport phase**, before Geyser ever hands
the player to Bedrock login or Floodgate. The evidence lines up cleanly:

- The unconnected ping (`bedrock-ping.py`) is a **single tiny UDP round-trip**
  and it works — that only proves the port is open and Geyser is advertising.
- The real join needs the multi-step RakNet handshake
  (`OpenConnectionRequest1 -> Reply1 -> Request2 -> Reply2` -> session). The
  logs show it looping on the early "tried to connect!" step and never reaching
  a Bedrock username / Floodgate login. **A handshake that keeps restarting is
  the "pings but won't join" signature.**
- Java (TCP) works because TCP retransmits and does path-MTU discovery; the
  Bedrock path (UDP) has neither and is far more sensitive to a lossy or
  size-limited network segment.
- `NetherNet / InitialConnection-13` is the client-side label for "the initial
  connection never established." It is generic — it does **not** by itself mean
  the client chose the NetherNet (WebRTC) transport; the client's own
  `Transport: RakNet:975` line shows it is on RakNet.

### Two leading hypotheses, ranked

**H1 — Wi-Fi/mesh network path drops the RakNet handshake (most likely).**
Ping (one small packet) survives, but the burst of handshake datagrams does
not. Common causes on a mesh/Wi-Fi LAN: AP/client isolation, a mesh node
mishandling UDP between wireless clients, or a path-MTU limit. The server sees
the client's request but its reply (or the client's follow-up) is lost, so the
client restarts from step 1 — exactly the observed loop.

**H2 — Upstream Geyser × newest-Bedrock (26.x) handshake bug.** The iOS client
here is `1.26.20`; the same `InitialConnection-*` symptom with the newest 26.x
Bedrock builds and no server-side login logs is reported upstream (e.g.
GeyserMC/Geyser issues [#6479](https://github.com/GeyserMC/Geyser/issues/6479)
and [#6457](https://github.com/GeyserMC/Geyser/issues/6457)). If H1 is ruled
out, this is the fallback: a client/Geyser protocol mismatch, not a local
config error.

### Decisive tests (do these first — they split H1 from H2)

1. **Run the new staged handshake probe from the Mac itself:**

   ```bash
   python3 scripts/raknet-connect-test.py 192.168.4.59 19133
   ```

   It walks Ping -> OCR1/Reply1 -> OCR2/Reply2 and prints exactly which step
   stalls and the negotiated MTU. From the Mac (loopback/wired), this should
   complete. If it completes here but iOS fails, the server + Geyser are healthy
   and the fault is downstream of Geyser (network path or H2).

2. **Run the same probe from a second device joined to the exact Wi-Fi/mesh
   SSID the iPhone uses** (a laptop on that SSID). If it stalls at the same step
   the iPhone does, **H1 is confirmed — it's the wireless path, not Geyser.**

3. **Join from a *wired* Bedrock client (a Windows PC on Ethernet)** on the same
   LAN. If wired works and wireless doesn't, that isolates the wireless segment.

### If H1 is confirmed (wireless path)

- Turn off **AP/client isolation** (a.k.a. "guest mode" / "client isolation")
  on the router/mesh for the SSID the phone uses. This is the single most common
  cause of "device on the same LAN can ping but can't hold a UDP session."
- Put the phone and Mac on the **same mesh node / band** to avoid inter-node
  UDP relay quirks; try 5 GHz vs 2.4 GHz.
- Only *then* revisit MTU (see note below).

### If H1 is ruled out (probe completes from the phone's network)

- Suspect **H2**. Confirm the installed Geyser build lists protocol support for
  the client's exact version (`1.26.20`), not just neighboring 26.x builds.
- Test with a different Bedrock client version / platform (Windows) to see if
  the failure is specific to the `1.26.20` iOS build.
- Watch the upstream issues above for a fix; update Geyser when one lands.

## MTU note — lowering may be the wrong lever here

Geyser's common-issues guidance says to lower `advanced.bedrock.mtu` **in steps
of ~100** for poor-network cases. We already tried:

- Live server: `1200` -> `1000`
- Clean test: `1200` -> `1000` -> `800`

...with no change. Every RakNet packet *after* the padded `OpenConnectionRequest1`
is small, and the server already receives that request ("tried to connect!"), so
a too-large **handshake** packet is unlikely to be the blocker. Lowering MTU
below the client's negotiated value can even *add* failures. Recommendation:
let the `raknet-connect-test.py` probe report the real path MTU first, then set
`mtu` to that value — don't keep guessing lower. If the probe shows a high MTU
survives, reset `mtu` back toward the default `1400`.

The clean-test server also has these diagnostic changes in place (harmless to
keep, but not the fix): `bedrock.address: 0.0.0.0`,
`advanced.bedrock.compression-level: -1`.

## Latent issues to fix *after* the handshake works

These do **not** cause the current RakNet stall, but they will block Bedrock
players at the *next* phase once the handshake succeeds — fix them so you're not
chasing a second bug:

- **`enforce-secure-profile=true`** in both `minecraft-server/server.properties`
  and `bedrock-clean-test-server/server.properties`. Bedrock/Floodgate players
  have no Mojang-signed chat profile key; the recommended setting for a
  Geyser+Floodgate server is `enforce-secure-profile=false`. Left as-is, a
  Bedrock player that finally gets through RakNet can be kicked at Java login.
- **`scripts/start-clean-server.sh` targets `minecraft-server-clean/`**, which is
  gitignored and is **not** the `bedrock-clean-test-server/` directory whose
  Geyser config we've been editing. Start the clean test server with the command
  under "Useful Local Commands" below (which runs inside
  `bedrock-clean-test-server`), or fix the script's `SERVER_DIR`. Otherwise
  config edits won't be reflected in the running server.
- `online-mode=true` with Floodgate installed **as a plugin on the same server**
  is correct — leave it. Floodgate authenticates Bedrock players itself; this
  only needs to change for proxy/standalone setups.

## Useful Local Commands

Check listeners:

```bash
lsof -nP -iUDP:19132 -iUDP:19133 -iTCP:25565 -iTCP:25566
```

Start live server:

```bash
./scripts/start-server.sh
```

Start clean test server:

```bash
cd bedrock-clean-test-server
../runtime/jdk-21.0.10+7/Contents/Home/bin/java -Xms1G -Xmx2G -jar paper.jar --nogui
```

Synthetic Bedrock ping (proves the port is open + Geyser advertises):

```bash
python3 scripts/bedrock-ping.py 192.168.4.59 19132
python3 scripts/bedrock-ping.py 192.168.4.59 19133
```

Staged RakNet handshake probe (proves *where* a real join stalls — run from the
Mac AND from a device on the phone's Wi-Fi to split H1 vs H2):

```bash
python3 scripts/raknet-connect-test.py 192.168.4.59 19132
python3 scripts/raknet-connect-test.py 192.168.4.59 19133
```

## Files Worth Reviewing

- `minecraft-server/plugins/Geyser-Spigot/config.yml`
- `bedrock-clean-test-server/plugins/Geyser-Spigot/config.yml`
- `minecraft-server/server.properties` / `bedrock-clean-test-server/server.properties` (see `enforce-secure-profile`)
- `scripts/raknet-connect-test.py` (staged handshake diagnostic)
- `scripts/start-server.sh`
- `scripts/start-clean-server.sh` (note: targets `minecraft-server-clean/`, not `bedrock-clean-test-server/`)
- `README.md`
