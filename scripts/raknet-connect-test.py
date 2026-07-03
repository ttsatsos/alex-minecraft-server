#!/usr/bin/env python3
"""Diagnose *where* a Bedrock/RakNet connection handshake stalls.

`bedrock-ping.py` only sends the RakNet *unconnected ping* (one tiny packet).
That already works in this project, so it cannot tell us why the real join
fails. This tool walks the actual pre-login RakNet handshake stage by stage
and reports exactly which step stops responding, plus the MTU that survives
the network path:

    1. Unconnected Ping          -> Unconnected Pong          (0x01 -> 0x1c)
    2. Open Connection Request 1 -> Open Connection Reply 1   (0x05 -> 0x06)
    3. Open Connection Request 2 -> Open Connection Reply 2   (0x07 -> 0x08)

Modern Bedrock RakNet added a "security cookie" challenge: Reply 1 can carry a
4-byte cookie that the client MUST echo back in Request 2, or the server
silently drops it. This probe handles that (older probes that don't will look
like the handshake dies at Request 2 even on a healthy server).

If the client gets an Unconnected Pong (the server shows in the world list)
but the handshake dies at step 2 or 3, the problem is the RakNet transport
between this host and the server -- not the address, port, or Geyser config.

WHY RUN THIS FROM TWO PLACES:
  * On the Mac mini itself (or another wired LAN host): the handshake should
    complete. If it does, the server + Geyser are healthy over that path.
  * On a laptop/phone-hotspot joined to the SAME Wi-Fi/mesh SSID the failing
    iOS device uses: this replicates the iOS network path. If it stalls at the
    same step, the fault is the wireless path (AP/client isolation, mesh UDP
    handling, or path MTU), not Geyser.

Always target the LAN IP (192.168.4.59), never a Tailscale 100.x address --
the tunnel's 1280 MTU and relaying will skew the result.

Usage:
    python3 scripts/raknet-connect-test.py HOST PORT
    python3 scripts/raknet-connect-test.py 192.168.4.59 19133
    python3 scripts/raknet-connect-test.py 192.168.4.59 19133 --mtu 1492,1200,800,576

Exit code is 0 only if the full handshake (through Reply 2) completes.
"""

from __future__ import annotations

import argparse
import socket
import struct
import sys
import time

# RakNet "offline message data id" magic, shared by every offline packet.
MAGIC = bytes.fromhex("00ffff00fefefefefdfdfdfd12345678")

# Packet IDs.
ID_UNCONNECTED_PING = 0x01
ID_UNCONNECTED_PONG = 0x1C
ID_OPEN_CONNECTION_REQUEST_1 = 0x05
ID_OPEN_CONNECTION_REPLY_1 = 0x06
ID_OPEN_CONNECTION_REQUEST_2 = 0x07
ID_OPEN_CONNECTION_REPLY_2 = 0x08
ID_INCOMPATIBLE_PROTOCOL_VERSION = 0x19

# IP + UDP header overhead RakNet adds when turning a datagram length into an
# advertised MTU. Used only to translate a target path MTU into a payload size.
UDP_IP_OVERHEAD = 28

# Current Bedrock RakNet protocol version. If the server disagrees it replies
# with ID_INCOMPATIBLE_PROTOCOL_VERSION telling us the version it wants, and we
# retry automatically, so this default rarely matters.
DEFAULT_RAKNET_PROTOCOL = 11

# A fixed pseudo-GUID for this "client". Any stable 64-bit value works.
CLIENT_GUID = 0x1234567890ABCDEF

# Sane MTU window. Reply 1 sometimes advertises the server's internal max
# (which can be large); clamp what we echo in Request 2 to a real-world value.
MTU_MIN = 400
MTU_MAX = 1400

TIMEOUT = 2.0
RETRIES = 3


def _recv(sock: socket.socket, expect_id: int):
    """Wait for a datagram with the given leading packet id.

    Returns (tag, payload_bytes, addr) where tag is "ok", "unexpected", or
    "incompatible", or None on timeout.
    """
    try:
        data, addr = sock.recvfrom(4096)
    except socket.timeout:
        return None
    if not data:
        return None
    if data[0] == ID_INCOMPATIBLE_PROTOCOL_VERSION:
        return ("incompatible", data, addr)
    if data[0] != expect_id:
        return ("unexpected", data, addr)
    return ("ok", data, addr)


def unconnected_ping(sock: socket.socket, host: str, port: int) -> bool:
    packet = (
        bytes([ID_UNCONNECTED_PING])
        + struct.pack(">q", int(time.time() * 1000))
        + MAGIC
        + struct.pack(">q", CLIENT_GUID)
    )
    for _ in range(RETRIES):
        sock.sendto(packet, (host, port))
        result = _recv(sock, ID_UNCONNECTED_PONG)
        if result and result[0] == "ok":
            data = result[1]
            motd_len = struct.unpack(">H", data[33:35])[0]
            motd = data[35 : 35 + motd_len].decode("utf-8", "replace")
            print(f"  [1/3] Unconnected Ping  -> Pong  OK   motd: {motd}")
            return True
    print("  [1/3] Unconnected Ping  -> Pong  FAILED (no reply)")
    return False


def _parse_reply1(data: bytes):
    """Reply 1: id(1) magic(16) serverGuid(8) security(1) [cookie(4)] mtu(2).

    Returns (server_mtu, server_guid, has_security, cookie). The MTU is always
    the trailing short, which is the robust place to read it from.
    """
    server_guid = struct.unpack(">q", data[17:25])[0]
    has_security = data[25] != 0
    cookie = 0
    if has_security:
        cookie = struct.unpack(">I", data[26:30])[0]
    server_mtu = struct.unpack(">H", data[-2:])[0]
    return server_mtu, server_guid, has_security, cookie


def open_connection_request_1(
    sock: socket.socket, host: str, port: int, target_mtu: int, protocol: int
):
    """Send OCR1 padded to `target_mtu`. Returns a tagged tuple."""
    header = bytes([ID_OPEN_CONNECTION_REQUEST_1]) + MAGIC + bytes([protocol])
    pad_len = max(0, (target_mtu - UDP_IP_OVERHEAD) - len(header))
    packet = header + (b"\x00" * pad_len)

    for _ in range(RETRIES):
        sock.sendto(packet, (host, port))
        result = _recv(sock, ID_OPEN_CONNECTION_REPLY_1)
        if not result:
            continue
        kind, data = result[0], result[1]
        if kind == "incompatible":
            return ("incompatible", data[1])
        if kind == "unexpected":
            return ("unexpected", data[0])
        server_mtu, server_guid, has_security, cookie = _parse_reply1(data)
        return ("ok", server_mtu, server_guid, has_security, cookie)
    return ("timeout",)


def _encode_raknet_address(host: str, port: int) -> bytes:
    """RakNet IPv4 address: version(4) + complemented octets + port."""
    octets = socket.inet_aton(socket.gethostbyname(host))
    complemented = bytes((~b) & 0xFF for b in octets)
    return bytes([4]) + complemented + struct.pack(">H", port)


def open_connection_request_2(
    sock: socket.socket,
    host: str,
    port: int,
    mtu: int,
    has_security: bool,
    cookie: int,
) -> bool:
    """Send OCR2. If the server issued a cookie, echo it back (required)."""
    packet = bytes([ID_OPEN_CONNECTION_REQUEST_2]) + MAGIC
    if has_security:
        # Echo the server cookie, then a "client has no security challenge" bool.
        packet += struct.pack(">I", cookie) + bytes([0])
    packet += (
        _encode_raknet_address(host, port)
        + struct.pack(">H", mtu)
        + struct.pack(">q", CLIENT_GUID)
    )
    for _ in range(RETRIES):
        sock.sendto(packet, (host, port))
        result = _recv(sock, ID_OPEN_CONNECTION_REPLY_2)
        if result and result[0] == "ok":
            return True
    return False


def run(host: str, port: int, mtu_ladder: list[int]) -> int:
    print(f"RakNet handshake probe -> {host}:{port}")
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(TIMEOUT)
    try:
        if not unconnected_ping(sock, host, port):
            print(
                "\nRESULT: Server is not answering the unconnected ping from this "
                "host.\n        The port/address is unreachable over UDP from here "
                "(firewall,\n        wrong port, or AP isolation)."
            )
            return 1

        protocol = DEFAULT_RAKNET_PROTOCOL
        server_mtu = None
        has_security = False
        cookie = 0
        highest_ok = None

        def record(res, target):
            nonlocal server_mtu, has_security, cookie, highest_ok
            server_mtu = res[1]
            has_security = res[3]
            cookie = res[4]
            highest_ok = target
            sec = "cookie/security ON" if has_security else "no security"
            print(
                f"  [2/3] OCR1 @ payload~{target}B -> Reply1 OK   "
                f"server MTU={server_mtu}, {sec}"
            )

        for target in mtu_ladder:
            res = open_connection_request_1(sock, host, port, target, protocol)
            tag = res[0]
            if tag == "ok":
                record(res, target)
                break
            if tag == "incompatible":
                server_proto = res[1]
                print(
                    f"  [2/3] OCR1 -> INCOMPATIBLE PROTOCOL. Server wants RakNet "
                    f"protocol {server_proto}, we sent {protocol}. Retrying..."
                )
                protocol = server_proto
                res2 = open_connection_request_1(sock, host, port, target, protocol)
                if res2[0] == "ok":
                    record(res2, target)
                    break
                print(
                    "\nRESULT: Server reports an INCOMPATIBLE RakNet protocol "
                    "version.\n        The Bedrock client build is too new/old for "
                    "this Geyser build.\n        Update Geyser, or test with a "
                    "matching Bedrock client version."
                )
                return 1
            if tag == "unexpected":
                print(
                    f"  [2/3] OCR1 @ payload~{target}B -> unexpected packet id "
                    f"0x{res[1]:02x}"
                )
                continue
            print(f"  [2/3] OCR1 @ payload~{target}B -> Reply1 timeout (dropped)")

        if server_mtu is None:
            print(
                "\nRESULT: Handshake stalls at Open Connection Request 1 for every "
                "MTU.\n        The server SEES the ping but its Reply 1 never gets "
                "back, or the\n        server never answers OCR1. This is the "
                "classic 'pings but won't\n        join' signature: a RakNet "
                "transport fault on this network path\n        (AP/client "
                "isolation, mesh UDP handling), NOT a Geyser config bug.\n"
                "        -> Re-run this on a wired LAN host. If it succeeds there "
                "but not\n           here, the wireless path is the culprit."
            )
            return 1

        if highest_ok != mtu_ladder[0]:
            print(
                f"  NOTE: larger MTUs were dropped; only ~{highest_ok}B got "
                f"through.\n        Path MTU on this network is limited -- lower "
                f"Geyser advanced.bedrock.mtu\n        toward this value."
            )

        # Echo a sane MTU in OCR2 (Reply 1 may advertise a large internal max).
        ocr2_mtu = max(MTU_MIN, min(server_mtu, MTU_MAX))
        if open_connection_request_2(
            sock, host, port, ocr2_mtu, has_security, cookie
        ):
            print(
                f"  [3/3] OCR2 @ MTU={ocr2_mtu} -> Reply2 OK   "
                "full handshake completed"
            )
            print(
                "\nRESULT: The RakNet handshake COMPLETES from this host. If iOS "
                "still\n        fails from its own network, the fault is that "
                "device's network\n        path or an upstream Geyser<->Bedrock "
                "protocol bug at login\n        (compare Bedrock client version "
                "against Geyser's supported list)."
            )
            return 0

        print(f"  [3/3] OCR2 @ MTU={ocr2_mtu} -> Reply2 FAILED (no reply)")
        print(
            "\nRESULT: Handshake reaches Open Connection Request 2 but Reply 2 "
            "never\n        arrives. If this happens even from the Mac itself, the "
            "fault is\n        server-side (Geyser/RakNet), not the wireless path. "
            "If it only\n        happens from the wireless host, the path is the "
            "culprit."
        )
        return 1
    finally:
        sock.close()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("host")
    parser.add_argument("port", type=int)
    parser.add_argument(
        "--mtu",
        default="1492,1400,1200,1024,800,576",
        help="Comma-separated MTU ladder to probe, largest first.",
    )
    args = parser.parse_args()
    ladder = [int(x) for x in args.mtu.split(",") if x.strip()]
    return run(args.host, args.port, ladder)


if __name__ == "__main__":
    raise SystemExit(main())
