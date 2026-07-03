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
- Current Geyser MTU: `800`

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

## Current Hypothesis

This looks like a RakNet/UDP handshake transport problem between iOS Bedrock and Geyser on the local Wi-Fi/mesh network, not a Paper gameplay/plugin problem.

The official Geyser common-issues guidance recommends lowering:

```yaml
advanced:
  bedrock:
    mtu: ...
```

for poor-network or connection-stall behavior. We lowered:

- Live server: `1200` to `1000`
- Clean test: `1200` to `1000`, then `800`

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

Synthetic Bedrock ping:

```bash
python3 scripts/bedrock-ping.py 192.168.4.59 19132
python3 scripts/bedrock-ping.py 192.168.4.59 19133
```

## Files Worth Reviewing

- `minecraft-server/plugins/Geyser-Spigot/config.yml`
- `bedrock-clean-test-server/plugins/Geyser-Spigot/config.yml`
- `scripts/start-server.sh`
- `scripts/start-clean-server.sh`
- `README.md`

