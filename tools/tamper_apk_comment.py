#!/usr/bin/env python3
"""Change only an APK's ZIP EOCD comment.

This preserves the APK Signing Block and central-directory offset while changing bytes covered by
APK signature content digests. It is useful for creating structurally intact tamper probes.
"""

from __future__ import annotations

import argparse
import struct
from pathlib import Path


def tamper(src: Path, dst: Path, marker: bytes) -> None:
    data = bytearray(src.read_bytes())
    magic = b"PK\x05\x06"
    start = max(0, len(data) - (0xFFFF + 22))
    pos = data.rfind(magic, start)
    if pos < 0 or pos + 22 > len(data):
        raise ValueError("ZIP EOCD not found")

    old_len = struct.unpack_from("<H", data, pos + 20)[0]
    if pos + 22 + old_len != len(data):
        raise ValueError("ZIP EOCD comment does not reach EOF")
    if len(marker) > 0xFFFF:
        raise ValueError("marker exceeds ZIP comment limit")

    struct.pack_into("<H", data, pos + 20, len(marker))
    dst.write_bytes(data[: pos + 22] + marker)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("src", type=Path)
    parser.add_argument("dst", type=Path)
    parser.add_argument("marker")
    args = parser.parse_args()

    tamper(args.src, args.dst, args.marker.encode("utf-8"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
