"""Build a disposable large visual-cache corpus from an existing disposable corpus.

Usage: python scripts/build_large_corpus.py build/corpus/alpha6-fast.sqlite build/corpus/large.sqlite
The source is opened read-only; output must stay under build/corpus and must not exist.
"""
import hashlib
import sqlite3
import struct
import sys
import zlib
from pathlib import Path


def patch_coordinate(raw: bytearray, name: bytes, original: int, value: int) -> None:
    marker = b"\x03\x00\x01" + name
    at = raw.find(marker)
    if at < 0 or raw.find(marker, at + 1) >= 0 or struct.unpack_from(">i", raw, at + len(marker))[0] != original:
        raise ValueError("Unexpected NBT root coordinates")
    raw[at + len(marker):at + len(marker) + 4] = struct.pack(">i", value)


def main() -> None:
    root = (Path(__file__).resolve().parent.parent / "build" / "corpus").resolve()
    source = Path(sys.argv[1]).resolve()
    target = Path(sys.argv[2]).resolve()
    if not source.is_relative_to(root) or not target.is_relative_to(root) or source == target or target.exists():
        raise ValueError("Both files must be distinct disposable paths below build/corpus; output must be new")
    tiles = [(0, 0), (-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, -1), (-1, 1), (1, 1), (-2, 0), (2, 0), (0, 2)]
    connection = sqlite3.connect(f"file:{source}?mode=ro", uri=True)
    output = sqlite3.connect(target)
    try:
        output.execute("PRAGMA journal_mode=WAL")
        output.execute("CREATE TABLE snapshot(x INTEGER NOT NULL,z INTEGER NOT NULL,version INTEGER NOT NULL,hash BLOB NOT NULL,raw_size INTEGER NOT NULL,payload BLOB NOT NULL,PRIMARY KEY(x,z)) WITHOUT ROWID")
        count = 0
        for tile_x, tile_z in tiles:
            shift_x, shift_z = tile_x * 140, tile_z * 140
            output.execute("BEGIN")
            for x, z, version, raw_size, payload in connection.execute("SELECT x,z,version,raw_size,payload FROM snapshot"):
                if version != 1:
                    continue
                nx, nz = x + shift_x, z + shift_z
                raw = bytearray(zlib.decompress(payload))
                if len(raw) != raw_size:
                    raise ValueError("Source payload size mismatch")
                patch_coordinate(raw, b"x", x, nx)
                patch_coordinate(raw, b"z", z, nz)
                digest = hashlib.sha256(raw).digest()
                output.execute("INSERT INTO snapshot VALUES(?,?,1,?,?,?)", (nx, nz, digest, len(raw), zlib.compress(raw, 1)))
                count += 1
            output.commit()
            print(f"{count} records", flush=True)
        output.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        print(f"Finished: {count} records, {target.stat().st_size / (1 << 20):.1f} MiB", flush=True)
    finally:
        connection.close()
        output.close()


if __name__ == "__main__":
    main()
