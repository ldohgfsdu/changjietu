---
name: verify-longshot-navigation
description: Formally verify successful processing produces one-shot result navigation and manual edits do not create phantom navigation. Use when editing ProcessTransition, startProcess, result acknowledgement or preview navigation.
---

# Processing completion and navigation state machine

**Why:** returning from the result screen must not immediately navigate back due to stale output; editing a slice is not a new processing completion event.

**Source mapping (code snapshot 06347f41, 2026-11-09):**
- app/src/main/java/com/changjietu/app/core/ProcessTransition.kt — markCompleted, pending, ack.
- app/src/main/java/com/changjietu/app/feature/editor/LongshotViewModel.kt — startProcess, ackProcessResult, commitCuts and reprocessFromRanges.
- app/src/test/java/com/changjietu/app/core/ProcessTransitionTest.kt — existing Kotlin behavior tests.
- Model: formal/longshot/specs/ProcessResult.tla and ProcessResult.cfg.
- app/src/main/java/com/changjietu/app/core/NavigationRecovery.kt and MainActivity.kt — process-death / stale back-stack recovery (not covered by the ProcessResult TLC model).
- app/src/test/java/com/changjietu/app/core/NavigationRecoveryTest.kt — pure JVM route recovery cases, including loading and empty preview.

**Verification:** Run `bash formal/longshot/verify.sh` at the root. TLC v1.8.0 explores three successes, failures, acknowledgements and up to three manual edits. It checks TypeOK, NoPhantomNavigation and PendingOnlyAfterSuccess. A mutant that makes ManualEdit deliver an extra navigation must violate NoPhantomNavigation.

**Update procedure:** Compare actual order of ViewModel state updates, markCompleted and UI ack. Revise model after any caller changes; rerun model, mutation check, and native tests. Only advance this skill's sync commit after rechecking correspondence. Use pstack /statechart for TLC-annotated state graphs when available.

**Restore-specific gate:** Also run `NavigationRecoveryTest` and `python3 tools/verify_state_machines.py` after any change to restored-route decisions or `openUri`. The script ensures loading is claimed synchronously. Real device test: move app to background on preview, kill its background process without force-stop, then restore from Recents; it must return to a usable home rather than showing two empty destinations. See `docs/state-machines/NAVIGATION_RECOVERY.md`.

**Limits:** TLC models the pure one-shot completion contract, **not** NavigationRecovery's process-death decisions, Activity/Compose delivery scheduling, concurrent double-taps, rotation timing or the Android screen stack. A model pass never establishes those runtime behaviors.
