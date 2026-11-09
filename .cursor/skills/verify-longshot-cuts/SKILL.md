---
name: verify-longshot-cuts
description: Check that edited split boundaries remain exclusive with no gaps or duplicate rows, and that an unchanged cut does not re-encode. Use for SliceGeometry, cut editor and manual reprocessing changes.
---

# Cut geometry and edit/commit state machine

**Why:** a one-pixel gap, duplicate source row or hidden padding can corrupt text when slices are assembled.

**Source mapping (code snapshot 06347f41, 2026-11-09):**
- app/src/main/java/com/changjietu/app/image/processing/SliceGeometry.kt — regionFor, requireNoOverlapCoverage, matchesRenderedRanges.
- app/src/main/java/com/changjietu/app/image/processing/ImageSplitter.kt — splitToFiles and contiguous ranges.
- app/src/main/java/com/changjietu/app/feature/editor/LongshotViewModel.kt — previewCut, commitCuts, buildRangesFromEditor.
- app/src/test/java/com/changjietu/app/image/processing/SliceGeometryTest.kt.
- Model: formal/longshot/specs/SliceCuts.tla and SliceCuts.cfg.

**Verification:** Run `bash formal/longshot/verify.sh` from root. TLC v1.8.0 models 6 abstract pixel rows and up to two re-encodes. Invariants TypeOK, NoGapNoOverlap and ExactTotalHeight check the shared exclusive cut boundary. The broken mutant moves the second output start by +1 and TLC must reject it.

**Update procedure:** Check that Kotlin range.last + 1 maps to Android BitmapRegionDecoder's exclusive lower edge, and that source margins are applied once. Revise spec when overlap/crop behavior changes; cover those dimensions separately because this bounded model only covers two contiguous slices at overlap=0. Rerun both original and intentionally broken models, real Kotlin slice tests and visually compare actual exported JPEG seams before changing the sync SHA. Use pstack /statechart for the model graph where installed.

**Limits:** two slices, one adjustable seam, zero overlap, no deleted slice, no dynamic margins, no actual JPEG content or OpenCV text-safety guarantee. In particular, no formal statement is made about auto seam selection splitting through a glyph.
