#!/usr/bin/env python3
"""Android emulator home-screen patrol.

Runs against an installed *debug test build*, never reports physical-device
acceptance. Keeps a screenshot, UI hierarchy and bounded logcat evidence.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.changjietu.app"
ACTIVITY = f"{PACKAGE}/.MainActivity"
LABEL = "选择图片"
BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def ui_label_center(xml: str, width: int) -> float:
    """Return x center or raise AssertionError with an actionable cause."""
    if width <= 0:
        raise AssertionError("screen width must be positive")
    root = ET.fromstring(xml)
    nodes = [
        n for n in root.iter("node")
        if n.get("text") == LABEL or n.get("content-desc") == LABEL
    ]
    if not nodes:
        raise AssertionError(f"Visible UI node '{LABEL}' not found in accessibility hierarchy")
    for node in nodes:
        match = BOUNDS.fullmatch(node.get("bounds", ""))
        if match:
            left, top, right, bottom = map(int, match.groups())
            if 0 <= left < right <= width and 0 <= top < bottom:
                return (left + right) / 2
    raise AssertionError(f"UI node '{LABEL}' has no valid on-screen bounds")


def require_centered(xml: str, width: int, tolerance_fraction: float = 0.04):
    x = ui_label_center(xml, width)
    expected = width / 2
    delta = abs(x - expected)
    if delta > width * tolerance_fraction:
        raise AssertionError(
            f"'{LABEL}' horizontal center x={x:.1f}, screen center={expected:.1f}; "
            f"delta={delta:.1f}px exceeds {tolerance_fraction * 100:.0f}% threshold"
        )
    return x


def adb(*arguments: str, timeout: int = 45, check: bool = True) -> subprocess.CompletedProcess:
    proc = subprocess.run(
        ["adb", *arguments], capture_output=True, timeout=timeout, check=False
    )
    if check and proc.returncode:
        raise RuntimeError(
            f"adb {' '.join(arguments)} failed ({proc.returncode}): "
            + proc.stderr.decode("utf-8", "replace")[-1000:]
        )
    return proc


def save_evidence(directory: Path):
    directory.mkdir(parents=True, exist_ok=True)
    shot = adb("exec-out", "screencap", "-p", check=False)
    if shot.returncode == 0 and shot.stdout.startswith(b"\x89PNG"):
        (directory / "home.png").write_bytes(shot.stdout)
    log = adb("logcat", "-d", "-t", "800", check=False)
    (directory / "logcat.txt").write_bytes(log.stdout + b"\n" + log.stderr)


def capture_hierarchy(directory: Path) -> str:
    last_error = "unknown"
    for _ in range(6):
        dump = adb("shell", "uiautomator", "dump", "/sdcard/window.xml", timeout=60, check=False)
        if dump.returncode == 0:
            data = adb("exec-out", "cat", "/sdcard/window.xml", check=False)
            if data.returncode == 0:
                xml = data.stdout.decode("utf-8", "replace")
                try:
                    ET.fromstring(xml)
                    (directory / "home.xml").write_text(xml, encoding="utf-8")
                    return xml
                except ET.ParseError as exc:
                    last_error = str(exc)
            else:
                last_error = data.stderr.decode("utf-8", "replace")[-500:]
        else:
            last_error = dump.stderr.decode("utf-8", "replace")[-500:]
        time.sleep(2)
    raise RuntimeError(f"Unable to obtain emulator UI hierarchy: {last_error}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--evidence", type=Path, default=Path("output/emulator-home-smoke"))
    args = parser.parse_args()
    if not args.apk.is_file():
        parser.error(f"APK does not exist: {args.apk}")
    args.evidence.mkdir(parents=True, exist_ok=True)

    try:
        adb("wait-for-device", timeout=120)
        adb("install", "-r", str(args.apk), timeout=120)
        adb("shell", "pm", "clear", PACKAGE)
        adb("shell", "am", "start", "-W", "-n", ACTIVITY, timeout=60)
        time.sleep(3)
        xml = capture_hierarchy(args.evidence)
        wm = adb("shell", "wm", "size").stdout.decode("utf-8", "replace")
        sizes = re.findall(r"(\d+)x(\d+)", wm)
        if not sizes:
            raise RuntimeError(f"Cannot determine emulator width from wm size: {wm!r}")
        width = int(sizes[-1][0])
        center = require_centered(xml, width)
        print(f"PASS: launcher displays '{LABEL}' centered at x={center:.1f} on {width}px screen")
        return 0
    except (RuntimeError, AssertionError, ET.ParseError, subprocess.TimeoutExpired) as exc:
        print(f"FAIL: emulator home UI patrol: {exc}", file=sys.stderr)
        return 1
    finally:
        save_evidence(args.evidence)


if __name__ == "__main__":
    raise SystemExit(main())
