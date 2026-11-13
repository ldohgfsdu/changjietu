#!/usr/bin/env python3
"""Real emulator smoke: synthetic image -> SEND -> cut -> save -> JPEG readback.

Uses an app-private fixture in a DEBUG build so no external share app, photo
picker, uploaded user image or broad storage permission is required. Verifies
actual MediaStore output dimensions and full source-height coverage, not pixel
losslessness (the production export uses JPEG). Never implies physical-device
acceptance.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import struct
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
import zlib

from emulator_home_smoke import PACKAGE, ACTIVITY, BOUNDS, adb

WIDTH, HEIGHT = 720, 4600
FIXTURE_NAME = "qa-e2e-source.png"
SAVE_DIR = "/sdcard/Pictures/changjietu"


def make_png(width: int = WIDTH, height: int = HEIGHT) -> bytes:
    """Deterministic compact RGB PNG with line and table-like synthetic blocks."""
    if width <= 0 or height <= 0:
        raise ValueError("invalid image size")
    compressor = zlib.compressobj(6)
    body = bytearray()
    for y in range(height):
        row = bytearray(width * 3)
        for x in range(width):
            ink = y % 92 in range(22, 31) and 60 <= x < 590
            table = 1550 <= y < 2750 and x in (50, 51, 170, 171, 290, 291, 410, 411, 530, 531)
            value = 50 if ink or table else 248
            k = x * 3
            row[k:k + 3] = bytes((value, value, value))
        body.extend(compressor.compress(bytes([0]) + row))
    body.extend(compressor.flush())

    def chunk(name: bytes, payload: bytes) -> bytes:
        return (
            struct.pack(">I", len(payload)) + name + payload +
            struct.pack(">I", zlib.crc32(name + payload) & 0xFFFFFFFF)
        )

    header = b"\x89PNG\r\n\x1a\n"
    return (
        header + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)) +
        chunk(b"IDAT", bytes(body)) + chunk(b"IEND", b"")
    )


def jpeg_dimensions(data: bytes) -> tuple[int, int]:
    if not data.startswith(b"\xff\xd8"):
        raise AssertionError("exported image is not a JPEG")
    pos = 2
    frames = {0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB}
    while pos < len(data):
        if data[pos] != 0xFF:
            pos += 1
            continue
        while pos < len(data) and data[pos] == 0xFF:
            pos += 1
        if pos >= len(data):
            break
        marker = data[pos]
        pos += 1
        if marker in (0x01, 0xD8, 0xD9) or 0xD0 <= marker <= 0xD7:
            continue
        if pos + 2 > len(data):
            break
        size = struct.unpack(">H", data[pos:pos + 2])[0]
        if size < 2 or pos + size > len(data):
            break
        if marker in frames:
            if size < 7:
                raise AssertionError("truncated JPEG frame header")
            height, width = struct.unpack(">HH", data[pos + 3:pos + 7])
            return width, height
        pos += size
    raise AssertionError("JPEG has no valid frame header")


def hierarchy(evidence: Path, stage: str) -> str:
    dump = adb("shell", "uiautomator", "dump", "/sdcard/qa-flow.xml", timeout=60, check=False)
    if dump.returncode:
        raise RuntimeError("uiautomator dump failed: " + dump.stderr.decode("utf-8", "replace")[-350:])
    result = adb("exec-out", "cat", "/sdcard/qa-flow.xml")
    xml = result.stdout.decode("utf-8", "replace")
    ET.fromstring(xml)
    (evidence / f"{stage}.xml").write_text(xml, encoding="utf-8")
    return xml


def screenshot(evidence: Path, stage: str):
    result = adb("exec-out", "screencap", "-p", check=False)
    if result.returncode == 0 and result.stdout.startswith(b"\x89PNG"):
        (evidence / f"{stage}.png").write_bytes(result.stdout)


def node_texts(xml: str) -> list[str]:
    root = ET.fromstring(xml)
    return [v for n in root.iter("node") for v in (n.get("text"), n.get("content-desc")) if v]


def wait_for_text(evidence: Path, stage: str, pattern: str, seconds: int = 90) -> tuple[str, str]:
    regex = re.compile(pattern)
    end = time.monotonic() + seconds
    last = []
    while time.monotonic() < end:
        try:
            xml = hierarchy(evidence, stage)
            last = node_texts(xml)
            match = next((t for t in last if regex.search(t)), None)
            if match:
                screenshot(evidence, stage)
                return xml, match
        except (RuntimeError, ET.ParseError, subprocess.TimeoutExpired):
            pass
        time.sleep(2)
    raise AssertionError(f"Cannot find UI text {pattern!r}; last visible texts: {last[-15:]!r}")


def tap_text(xml: str, pattern: str):
    regex = re.compile(pattern)
    root = ET.fromstring(xml)
    for n in root.iter("node"):
        if not any(regex.search(v) for v in (n.get("text", ""), n.get("content-desc", ""))):
            continue
        b = BOUNDS.fullmatch(n.get("bounds", ""))
        if not b:
            continue
        x1, y1, x2, y2 = map(int, b.groups())
        if x2 <= x1 or y2 <= y1:
            continue
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        return
    raise AssertionError(f"No tappable visible node matching {pattern!r}")


def app_fixture(evidence: Path):
    fixture = evidence / FIXTURE_NAME
    fixture.write_bytes(make_png())
    adb("shell", "pm", "clear", PACKAGE)
    adb("shell", "run-as", PACKAGE, "mkdir", "-p", "files")
    # adb exec-in sometimes reports success while the destination is absent.
    # Stage with adb push, then copy as the debuggable app UID (run-as).
    # The byte-length check prevents a false-green from a partial transfer.
    staged = f"/data/local/tmp/{PACKAGE}-{FIXTURE_NAME}"
    destination = f"files/{FIXTURE_NAME}"
    try:
        adb("push", str(fixture), staged, timeout=60)
        adb("shell", "run-as", PACKAGE, "cp", staged, destination, timeout=60)
        result = adb("shell", "run-as", PACKAGE, "wc", "-c", destination)
        try:
            copied_bytes = int(result.stdout.decode("utf-8", "replace").split()[0])
        except (ValueError, IndexError) as exc:
            raise RuntimeError("Cannot determine app-private fixture length") from exc
        expected_bytes = fixture.stat().st_size
        if copied_bytes != expected_bytes:
            raise RuntimeError(
                f"App-private fixture incomplete: expected {expected_bytes} bytes, got {copied_bytes}"
            )
    finally:
        adb("shell", "rm", "-f", staged, check=False)


def export_files() -> list[str]:
    result = adb("shell", "ls", "-1", SAVE_DIR, check=False)
    if result.returncode:
        return []
    return sorted(
        f.strip() for f in result.stdout.decode("utf-8", "replace").splitlines()
        if re.fullmatch(r"longshot_\d{2}_of_\d{2}\.jpg", f.strip())
    )


def wait_export_files(min_count: int, seconds: int = 60) -> list[str]:
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        found = export_files()
        if len(found) >= min_count:
            return found
        time.sleep(2)
    raise AssertionError(f"Only {len(export_files())} exported files after {seconds}s")


def run_flow(evidence: Path):
    app_fixture(evidence)
    uri = f"file:///data/user/0/{PACKAGE}/files/{FIXTURE_NAME}"
    adb("shell", "am", "start", "-W", "-n", ACTIVITY,
        "-a", "android.intent.action.SEND", "-t", "image/png",
        "--eu", "android.intent.extra.STREAM", uri, timeout=60)
    process, _ = wait_for_text(evidence, "process", r"^原图 720 × 4600$", 90)
    # The title alone would not prove that decoding and analysis completed.
    if not any(re.fullmatch(r"生成 \d+ 张切片", t) for t in node_texts(process)):
        process, _ = wait_for_text(evidence, "process", r"^生成 \d+ 张切片$", 60)
    tap_text(process, r"^生成 \d+ 张切片$")
    result, count_text = wait_for_text(evidence, "preview", r"^\d+ 张切片$", 120)
    count = int(count_text.split()[0])
    if count < 2:
        raise AssertionError(f"Expected multiple slices for {HEIGHT}px source, got {count}")
    if "保存相册" not in node_texts(result):
        result, _ = wait_for_text(evidence, "preview", r"保存相册", 30)
    tap_text(result, r"^保存相册$")
    names = wait_export_files(count)
    if len(names) != count:
        raise AssertionError(f"Saved {len(names)} JPEGs but result page says {count}")
    dims = []
    for name in names:
        blob = adb("exec-out", "cat", f"{SAVE_DIR}/{name}", timeout=60).stdout
        dims.append(jpeg_dimensions(blob))
    if any(w != WIDTH for w, h in dims):
        raise AssertionError(f"Export altered source width {WIDTH}: {dims}")
    if sum(h for w, h in dims) != HEIGHT:
        raise AssertionError(f"No-overlap export did not cover original {HEIGHT} rows: {dims}")
    (evidence / "export-evidence.txt").write_text(
        f"Original: {WIDTH}x{HEIGHT} PNG\nSaved: {count} JPEG slices\n" +
        "\n".join(f"{name}: {w}x{h}" for name, (w, h) in zip(names, dims)) +
        f"\nSum heights: {sum(h for w, h in dims)}\n", encoding="utf-8"
    )
    screenshot(evidence, "export")
    print(f"PASS: synthetic SEND -> analyze -> cut -> save -> JPEG readback, {count} slices, {dims}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", type=Path, default=Path("output/emulator-flow-smoke"))
    args = parser.parse_args()
    args.evidence.mkdir(parents=True, exist_ok=True)
    try:
        adb("wait-for-device", timeout=120)
        run_flow(args.evidence)
        return 0
    except (AssertionError, RuntimeError, subprocess.TimeoutExpired, ET.ParseError) as exc:
        print(f"FAIL: emulator import/cut/export flow: {exc}", file=sys.stderr)
        return 1
    finally:
        screenshot(args.evidence, "final")
        log = adb("logcat", "-d", "-t", "1000", check=False)
        (args.evidence / "logcat.txt").write_bytes(log.stdout + b"\n" + log.stderr)


if __name__ == "__main__":
    raise SystemExit(main())
