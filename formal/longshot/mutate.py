#!/usr/bin/env python3
"""Create isolated one-line TLA+ mutants. These are not Kotlin mutation tests."""
from pathlib import Path
import sys

src, out = map(Path, sys.argv[1:3])
changes = {
    "ShareIngress": ("displayed' = IF g = generation THEN g ELSE displayed", "displayed' = g"),
    "ProcessResult": (
        "UNCHANGED <<sequence, pending, delivered, phase>>",
        "/\\ delivered' = delivered + 1\n    /\\ UNCHANGED <<sequence, pending, phase>>",
    ),
    "SliceCuts": ("resultStart' = desiredCut", "resultStart' = desiredCut + 1"),
    "ExportSelection": ("snapshot' = selected", "snapshot' = {}"),
}
for name, (before, after) in changes.items():
    text = (src / f"{name}.tla").read_text()
    assert text.count(before) == 1, f"expected one mutation target for {name}"
    target = out / name
    target.mkdir(parents=True)
    (target / f"{name}.tla").write_text(text.replace(before, after, 1))
    (target / f"{name}.cfg").write_text((src / f"{name}.cfg").read_text())
print("Generated four intentionally broken TLA+ models.")
