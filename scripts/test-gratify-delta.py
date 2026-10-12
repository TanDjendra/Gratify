import gzip
import hashlib
from pathlib import Path
import struct
import tempfile
import unittest
import bsdiff4
import gratify_delta as delta


class DeltaTest(unittest.TestCase):
    def test_convert_and_verify_insert_delete_reorder_and_random_bytes(self):
        old = bytes(range(256)) * 2000
        for new in (old[:45000] + b"lyrics updated" * 100 + old[46000:], old[20000:] + old[:20000], b"new" * 30000, old):
            with tempfile.TemporaryDirectory() as root:
                source, target, patch = [Path(root) / name for name in ("source", "target", "patch")]
                source.write_bytes(old); target.write_bytes(new)
                delta.generate(source, target, patch)
                delta.verify(source, patch, len(new), hashlib.sha256(new).hexdigest())
                self.assertLess(patch.stat().st_size, len(new))

    def test_invalid_bsdiff_header_and_sizes_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            output = Path(root) / "patch"
            for raw in (b"", b"not-a-patch" * 20, b"BSDIFF40" + bytes([255]) * 24):
                with self.assertRaises(ValueError): delta.convert(raw, 100, 100, output)

    def test_corruption_truncation_and_hash_mismatch_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            source, patch = Path(root) / "source", Path(root) / "patch"
            source.write_bytes(b"abc" * 1000)
            delta.convert(bsdiff4.diff(source.read_bytes(), b"def" * 1000), 3000, 3000, patch)
            valid = patch.read_bytes()
            with self.assertRaises(ValueError): delta.verify(source, patch, 3000, "a" * 64)
            for bad in (valid[:-4], valid[:31], valid[:-8] + bytes(8)):
                patch.write_bytes(bad)
                with self.assertRaises((ValueError, EOFError, OSError)): delta.verify(source, patch, 3000, "a" * 64)

    def test_zero_progress_command_does_not_loop(self):
        # Construct standard BSDIFF40 with a valid bzip2 stream carrying a malicious zero-progress command.
        import bz2
        control = bz2.compress(bytes(24)); diff = bz2.compress(b""); extra = bz2.compress(b"")
        raw = b"BSDIFF40" + struct.pack("<qqq", len(control), len(diff), 10) + control + diff + extra
        with tempfile.TemporaryDirectory() as root:
            patch = Path(root) / "patch"
            with self.assertRaises(ValueError): delta.convert(raw, 10, 10, patch)
            self.assertFalse(patch.exists())


if __name__ == "__main__": unittest.main()
