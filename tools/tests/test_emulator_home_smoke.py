"""Negative controls for the emulator UI hierarchy assertion."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from emulator_home_smoke import require_centered  # noqa: E402


def hierarchy(bounds: str, label: str = "选择图片") -> str:
    return '<hierarchy><node text="' + label + '" bounds="' + bounds + '"/></hierarchy>'


class HomeUiPatrolTest(unittest.TestCase):
    def test_centered_label_passes(self):
        self.assertEqual(540, require_centered(hierarchy("[480,450][600,530]"), 1080))

    def test_offset_label_is_detected(self):
        with self.assertRaisesRegex(AssertionError, "delta"):
            require_centered(hierarchy("[700,450][820,530]"), 1080)

    def test_missing_primary_action_is_detected(self):
        with self.assertRaisesRegex(AssertionError, "not found"):
            require_centered(hierarchy("[480,450][600,530]", "无关控件"), 1080)

    def test_invalid_bounds_are_detected(self):
        with self.assertRaisesRegex(AssertionError, "valid on-screen bounds"):
            require_centered(hierarchy("[900,450][1100,530]"), 1080)


if __name__ == "__main__":
    unittest.main()
