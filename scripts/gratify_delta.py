"""Publisher-only BSDiff -> bounded streaming gzip delta format (no code execution on client)."""
import bz2
import gzip
import hashlib
import io
from pathlib import Path
import struct

FORMAT = "gratify-delta-v1"
MAX_SIZE = 300 * 1024 * 1024
MAX_COMMANDS = 1_000_000
BLOCK = 65536


def digest(path):
    result = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(BLOCK), b""):
            result.update(block)
    return result.hexdigest()


def off_t(data):
    if len(data) != 8:
        raise ValueError("Truncated BSDiff control")
    value = int.from_bytes(data, "little")
    return -(value & ((1 << 63) - 1)) if value >> 63 else value


def read_exact(stream, size):
    data = stream.read(size)
    if len(data) != size:
        raise ValueError("Truncated BSDiff stream")
    return data


def convert(bsdiff, source_size, expected_size, output):
    """Absolute offsets + diff/extra per command, gzip compressed; fixed 32-byte header."""
    if not 0 < source_size <= MAX_SIZE or not 0 < expected_size <= MAX_SIZE:
        raise ValueError("APK exceeds delta size limit")
    if bsdiff[:8] != b"BSDIFF40" or len(bsdiff) < 32:
        raise ValueError("Unsupported BSDiff format")
    control_size, diff_size, new_size = [off_t(bsdiff[i:i + 8]) for i in (8, 16, 24)]
    if control_size < 0 or diff_size < 0 or 32 + control_size + diff_size > len(bsdiff) or new_size != expected_size:
        raise ValueError("Invalid BSDiff header")
    control = bz2.BZ2File(io.BytesIO(bsdiff[32:32 + control_size]))
    diff = bz2.BZ2File(io.BytesIO(bsdiff[32 + control_size:32 + control_size + diff_size]))
    extra = bz2.BZ2File(io.BytesIO(bsdiff[32 + control_size + diff_size:]))
    commands = controls = 0
    try:
        with Path(output).open("wb") as raw:
            raw.write(b"GRTFDLT1" + struct.pack(">qqq", source_size, expected_size, 0))
            with gzip.GzipFile(fileobj=raw, mode="wb", compresslevel=9, mtime=0, filename="") as target:
                old_position = produced = 0
                while produced < expected_size:
                    x, y, z = [off_t(read_exact(control, 8)) for _ in range(3)]
                    controls += 1
                    if controls > MAX_COMMANDS or x < 0 or y < 0 or x + y > expected_size - produced:
                        raise ValueError("Invalid BSDiff command")
                    if x + y == 0:
                        if z == 0:
                            raise ValueError("BSDiff command makes no progress")
                        old_position += z
                        continue
                    commands += 1
                    if not -MAX_SIZE <= old_position <= MAX_SIZE:
                        raise ValueError("BSDiff offset exceeds client limit")
                    target.write(struct.pack(">qqq", old_position, x, y))
                    for stream, size in ((diff, x), (extra, y)):
                        while size:
                            count = min(BLOCK, size)
                            target.write(read_exact(stream, count))
                            size -= count
                    old_position += x + z
                    produced += x + y
                if control.read(1) or diff.read(1) or extra.read(1):
                    raise ValueError("Trailing BSDiff data")
            raw.seek(24)
            raw.write(struct.pack(">q", commands))
    except Exception:
        Path(output).unlink(missing_ok=True)
        raise
    finally:
        control.close(); diff.close(); extra.close()


def generate(source, target, output):
    # This dependency is only used on the release preparer's computer. It is never in the APK.
    import bsdiff4
    old, new = Path(source).read_bytes(), Path(target).read_bytes()
    convert(bsdiff4.diff(old, new), len(old), len(new), output)
    verify(source, output, len(new), hashlib.sha256(new).hexdigest())


def verify(source, patch, expected_size, expected_hash):
    result = hashlib.sha256()
    with Path(source).open("rb") as old, Path(patch).open("rb") as raw:
        if read_exact(raw, 8) != b"GRTFDLT1":
            raise ValueError("Unsupported delta")
        old_size, new_size, commands = struct.unpack(">qqq", read_exact(raw, 24))
        if old_size != Path(source).stat().st_size or new_size != expected_size or not 1 <= commands <= min(MAX_COMMANDS, new_size):
            raise ValueError("Invalid delta header")
        produced = 0
        with gzip.GzipFile(fileobj=raw, mode="rb") as stream:
            for _ in range(commands):
                offset, x, y = struct.unpack(">qqq", read_exact(stream, 24))
                if not -MAX_SIZE <= offset <= MAX_SIZE or min(x, y) < 0 or x + y == 0 or x + y > new_size - produced:
                    raise ValueError("Invalid delta command")
                remaining = x
                while remaining:
                    count = min(BLOCK, remaining)
                    diff = bytearray(read_exact(stream, count))
                    first, end = max(0, offset), min(old_size, offset + count)
                    if end > first:
                        old.seek(first)
                        for index, byte in enumerate(read_exact(old, end - first), first - offset):
                            diff[index] = (diff[index] + byte) & 255
                    result.update(diff); offset += count; remaining -= count
                remaining = y
                while remaining:
                    count = min(BLOCK, remaining)
                    result.update(read_exact(stream, count)); remaining -= count
                produced += x + y
            if produced != new_size or stream.read(1):
                raise ValueError("Delta output is incomplete or has extra data")
    if result.hexdigest() != expected_hash:
        raise ValueError("Delta reconstruction differs from signed target APK")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path); parser.add_argument("target", type=Path); parser.add_argument("output", type=Path)
    args = parser.parse_args()
    if args.output.exists() or args.output.resolve() in (args.source.resolve(), args.target.resolve()):
        parser.error("Choose a new output file")
    if not all(0 < path.stat().st_size <= MAX_SIZE for path in (args.source, args.target)):
        parser.error("APK exceeds delta size limit")
    generate(args.source, args.target, args.output)
