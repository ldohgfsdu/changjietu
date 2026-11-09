---
name: verify-longshot-export
description: Check selection snapshots, empty-export prevention and export completion semantics. Use when modifying selectAll, selectedSlices, Save to Gallery, ZIP packaging, share intents or cleanup.
---

# Selected slices and export state machine

**Why:** the wrong files being shared, an empty export or a selection changing mid-write can cause lost, disordered or incomplete output.

**Source mapping (code snapshot 06347f41, 2026-11-09):**
- app/src/main/java/com/changjietu/app/feature/editor/LongshotViewModel.kt — selectedSlices, saveToGallery, packageZipToDownloads, shareIntent.
- app/src/main/java/com/changjietu/app/feature/export/OrderedSlices.kt — source-Y ascending order; external recipients can reorder.
- app/src/main/java/com/changjietu/app/feature/export/ExportRepository.kt — MediaStore, public folders, FileProvider.
- app/src/main/java/com/changjietu/app/feature/export/SliceZipper.kt — ZIP output.
- Model: formal/longshot/specs/ExportSelection.tla and ExportSelection.cfg.

**Verification:** Run `bash formal/longshot/verify.sh`. TLC v1.8.0 explores all selection subsets of three slices, two exports, success/failure and selection toggles while exporting. TypeOK, NoEmptySubmission and OnlySubmittedSlicesDelivered are checked; clearing snapshot before starting an export is a required failing mutant.

**Update procedure:** Ensure selectedSlices captures an immutable collection before asynchronous IO, check each export's no-selection boundary and ordering, then rerun TLC, mutations and the Kotlin export unit tests. If changes affect file cleanup, add a new model of file lifetime rather than assuming selected file paths remain readable. Run pstack /statechart for visual checked traces if available. Advance sync SHA only with verified Kotlin correspondence.

**Limits:** no Android runtime permission gate, MediaStore failure atomicity, FileProvider ownership or cleanup-vs-export race; no assertion about recipient ordering. The snapshot model intentionally abstracts file contents and directory eviction.
