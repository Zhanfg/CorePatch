#!/usr/bin/env python3
"""Inspect APK Signing Block IDs and APK Signature Scheme v4 .idsig blocks.

No third-party dependencies. Intended for CI validation of CorePatch test fixtures.
"""

from __future__ import annotations

import argparse
import json
import struct
from pathlib import Path

APK_SIG_MAGIC = b"APK Sig Block 42"
EOCD_MAGIC = b"PK\x05\x06"

LABELS = {
    0x7109871A: "v2",
    0xF05368C0: "v3.0",
    0x1B93AD61: "v3.1",
    0x70E1C89F: "v3.2",
}


def u32(data: bytes, off: int) -> int:
    return struct.unpack_from("<I", data, off)[0]


def u64(data: bytes, off: int) -> int:
    return struct.unpack_from("<Q", data, off)[0]


def find_eocd(data: bytes) -> int:
    start = max(0, len(data) - (0xFFFF + 22))
    pos = data.rfind(EOCD_MAGIC, start)
    if pos < 0 or pos + 22 > len(data):
        raise ValueError("ZIP EOCD not found")
    comment_len = struct.unpack_from("<H", data, pos + 20)[0]
    if pos + 22 + comment_len != len(data):
        # APKs used here are small non-ZIP64 files. Search backwards for the EOCD
        # whose comment length reaches EOF.
        cursor = len(data)
        while True:
            pos = data.rfind(EOCD_MAGIC, start, cursor)
            if pos < 0:
                raise ValueError("valid ZIP EOCD not found")
            if pos + 22 <= len(data):
                comment_len = struct.unpack_from("<H", data, pos + 20)[0]
                if pos + 22 + comment_len == len(data):
                    return pos
            cursor = pos
    return pos


def apk_signing_block_ids(path: Path) -> list[int]:
    data = path.read_bytes()
    eocd = find_eocd(data)
    cd_offset = u32(data, eocd + 16)
    if cd_offset < 24:
        raise ValueError("central directory offset is too small")

    footer = cd_offset - 24
    size_in_footer = u64(data, footer)
    magic = data[footer + 8 : footer + 24]
    if magic != APK_SIG_MAGIC:
        raise ValueError("APK Signing Block magic not found")

    block_start = cd_offset - (size_in_footer + 8)
    if block_start < 0:
        raise ValueError("invalid APK Signing Block size")
    size_in_header = u64(data, block_start)
    if size_in_header != size_in_footer:
        raise ValueError("APK Signing Block header/footer size mismatch")

    pos = block_start + 8
    end = footer
    ids: list[int] = []
    while pos < end:
        if pos + 8 > end:
            raise ValueError("truncated signing-block pair length")
        pair_len = u64(data, pos)
        pos += 8
        if pair_len < 4 or pos + pair_len > end:
            raise ValueError("invalid signing-block pair length")
        block_id = u32(data, pos)
        ids.append(block_id)
        pos += pair_len
    return ids


def read_len_prefixed(buf: memoryview, pos: int) -> tuple[memoryview, int]:
    if pos + 4 > len(buf):
        raise ValueError("truncated length-prefixed field")
    length = struct.unpack_from("<I", buf, pos)[0]
    pos += 4
    if pos + length > len(buf):
        raise ValueError("length-prefixed field exceeds buffer")
    return buf[pos : pos + length], pos + length


def idsig_extra_block_ids(path: Path) -> list[int]:
    data = memoryview(path.read_bytes())
    if len(data) < 4:
        raise ValueError("truncated idsig")
    version = struct.unpack_from("<I", data, 0)[0]
    pos = 4
    _, pos = read_len_prefixed(data, pos)  # hashingInfo
    signing_infos_raw, pos = read_len_prefixed(data, pos)
    if pos != len(data):
        raise ValueError("unexpected trailing bytes after idsig")

    buf = signing_infos_raw
    p = 0
    # V4Signature.SigningInfo:
    # apkDigest, certificate, additionalData, publicKey, signatureAlgorithmId, signature
    for _ in range(4):
        _, p = read_len_prefixed(buf, p)
    if p + 4 > len(buf):
        raise ValueError("truncated v4 signature algorithm ID")
    p += 4
    _, p = read_len_prefixed(buf, p)

    ids: list[int] = []
    while p < len(buf):
        if p + 4 > len(buf):
            raise ValueError("truncated SigningInfoBlock ID")
        block_id = struct.unpack_from("<I", buf, p)[0]
        p += 4
        _, p = read_len_prefixed(buf, p)
        ids.append(block_id)

    if version != 2:
        raise ValueError(f"unexpected v4 idsig version {version}")
    return ids


def parse_int(value: str) -> int:
    return int(value, 0)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("apk", type=Path)
    ap.add_argument("--idsig", type=Path)
    ap.add_argument("--require-apk-block", action="append", default=[], type=parse_int)
    ap.add_argument("--require-idsig-block", action="append", default=[], type=parse_int)
    args = ap.parse_args()

    apk_ids = apk_signing_block_ids(args.apk)
    idsig_ids = idsig_extra_block_ids(args.idsig) if args.idsig else []

    report = {
        "apk": str(args.apk),
        "apk_block_ids": [f"0x{x:08x}" for x in apk_ids],
        "apk_schemes": [LABELS.get(x, "other") for x in apk_ids],
        "idsig": str(args.idsig) if args.idsig else None,
        "idsig_extra_block_ids": [f"0x{x:08x}" for x in idsig_ids],
    }
    print(json.dumps(report, indent=2))

    missing_apk = [x for x in args.require_apk_block if x not in apk_ids]
    missing_idsig = [x for x in args.require_idsig_block if x not in idsig_ids]
    if missing_apk or missing_idsig:
        if missing_apk:
            print("Missing APK block(s): " + ", ".join(f"0x{x:08x}" for x in missing_apk))
        if missing_idsig:
            print("Missing idsig block(s): " + ", ".join(f"0x{x:08x}" for x in missing_idsig))
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
