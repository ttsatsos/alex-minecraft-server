#!/usr/bin/env python3
"""Send a Bedrock/RakNet unconnected ping and print the advertised MOTD."""

from __future__ import annotations

import socket
import struct
import sys
import time


MAGIC = bytes.fromhex("00ffff00fefefefefdfdfdfd12345678")


def main() -> int:
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} HOST PORT", file=sys.stderr)
        return 2

    host = sys.argv[1]
    port = int(sys.argv[2])
    packet = (
        bytes([1])
        + struct.pack(">q", int(time.time() * 1000))
        + MAGIC
        + struct.pack(">q", 12345)
    )

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(2)
    try:
        sock.sendto(packet, (host, port))
        data, address = sock.recvfrom(4096)
    finally:
        sock.close()

    motd_length = struct.unpack(">H", data[33:35])[0]
    motd = data[35 : 35 + motd_length].decode("utf-8", "replace")
    print(f"{address[0]}:{address[1]} {motd}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
