"""Device-independent negative controls for actual JPEG export and UI detection."""
import struct
import sys
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from emulator_flow_smoke import make_png, jpeg_dimensions, node_texts, tap_text


class FlowDetectorTests(unittest.TestCase):
    def test_generated_fixture_is_png_with_expected_dimensions(self):
        import zlib
        blob = make_png(24, 50)
        self.assertEqual(b"\x89PNG\r\n\x1a\n", blob[:8])
        self.assertEqual((24, 50), struct.unpack(">II", blob[16:24]))
        self.assertGreater(len(zlib.decompress(blob[41:-16])), 24 * 50 * 3)

    def test_fake_or_truncated_jpeg_is_rejected(self):
        for blob in (b"", b"not a jpeg", b"\xff\xd8\xff\xd9"):
            with self.assertRaises(AssertionError):
                jpeg_dimensions(blob)

    def test_valid_sof_header_returns_real_dimensions(self):
        # Small synthetic JPEG header for metadata parsing, not a decoded image.
        data = b"\xff\xd8\xff\xe0\x00\x04xx\xff\xc0\x00\x11\x08\x00\x64\x02\xd0" + b"x" * 10
        self.assertEqual((720, 100), jpeg_dimensions(data))

    def test_ui_text_only_finds_present_nodes(self):
        self.assertEqual(["切片结果", "保存相册"], node_texts(
            '<hierarchy><node text="切片结果"/><node content-desc="保存相册"/></hierarchy>'
        ))
        self.assertEqual([], node_texts('<hierarchy/>'))

    def test_invalid_xml_does_not_silently_pass(self):
        with self.assertRaises(ET.ParseError):
            node_texts("<hierarchy><node>")


if __name__ == "__main__":
    unittest.main()
