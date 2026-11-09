#!/usr/bin/env python3
"""Weak correspondence gate: assert selected anchors in the real Kotlin source.

These checks are not proofs of Kotlin semantics. Removal mutants demonstrate
that the anchors are sensitive to simple code drift, nothing more.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1])
checks = {
    "share pending acknowledgement": (
        "app/src/main/java/com/changjietu/app/core/ShareDelivery.kt",
        "if (pendingId == id) pendingId = null",
    ),
    "new source invalidates old analyses": (
        "app/src/main/java/com/changjietu/app/feature/editor/LongshotViewModel.kt",
        "if (generation != sourceGeneration) return@launch",
    ),
    "only successful processing publishes result navigation": (
        "app/src/main/java/com/changjietu/app/feature/editor/LongshotViewModel.kt",
        "processTransition.markCompleted()",
    ),
    "one-shot result acknowledgement": (
        "app/src/main/java/com/changjietu/app/core/ProcessTransition.kt",
        "if (_pending.value == id)",
    ),
    "exclusive source-pixel output boundary": (
        "app/src/main/java/com/changjietu/app/image/processing/SliceGeometry.kt",
        "bottom = margins.top + range.last + 1",
    ),
    "zero-overlap coverage enforced at initial processing": (
        "app/src/main/java/com/changjietu/app/feature/editor/LongshotViewModel.kt",
        "SliceGeometry.requireNoOverlapCoverage(plan.ranges, contentH)",
    ),
    "submission cannot have no selected slices": (
        "app/src/main/java/com/changjietu/app/feature/editor/LongshotViewModel.kt",
        "if (slices.isEmpty())",
    ),
    "submission order defined by source Y and index": (
        "app/src/main/java/com/changjietu/app/feature/export/OrderedSlices.kt",
        "slices.sortedWith(compareBy<SliceOutput> { it.startY }.thenBy { it.index })",
    ),
}
for name, (path, fragment) in checks.items():
    text = (root / path).read_text()
    if fragment not in text:
        raise AssertionError(f"Missing Kotlin anchor: {name} at {path}")
    mutant = text.replace(fragment, "/* removed safety step */", 1)
    if fragment in mutant:
        raise AssertionError(f"Anchor mutation not detected: {name}")
    print(f"ANCHOR PASS: {name}; removal mutant rejected")
print(f"PASS: {len(checks)} Kotlin source anchors (not semantic Kotlin proofs).")
