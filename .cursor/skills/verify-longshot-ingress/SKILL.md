---
name: verify-longshot-ingress
description: Formally check source replacement, external share delivery and stale analysis completion. Use when editing openUri, consumeShare, ShareDelivery, URI permission handling or source generation.
---

# Source import and external share state machine

**Why:** an old image analysis must not replace the image the user just opened. An acknowledgement of an older share must not silently dismiss the current pending share.

**Source mapping (code snapshot 06347f41, 2026-11-09):**
- app/src/main/java/com/changjietu/app/feature/editor/LongshotViewModel.kt — openUri, consumeShare, ackSharePending, clearSource; sourceGeneration, previewJob and stale-result checks.
- app/src/main/java/com/changjietu/app/core/ShareDelivery.kt — consume, pendingId and ack.
- app/src/main/java/com/changjietu/app/core/UriPersistencePolicy.kt — source-aware READ grants (this policy is *not* formally modeled here).
- Model: formal/longshot/specs/ShareIngress.tla and ShareIngress.cfg.

**Verification:** Run `bash formal/longshot/verify.sh` from the repo root. TLC v1.8.0 with MaxGeneration=3 exhausts picker/share openings, completions in arbitrary order and share acknowledgements. It checks TypeOK, NoStaleSourceDisplayed and PendingBelongsToConsumedShare. A model mutation that applies a stale result must trigger an invariant failure; Kotlin source anchors are also probed for removal.

**Review checklist:** Trace each atomic TLA+ action to the method's actual suspend boundary. Do not model a multi-statement Kotlin coroutine as atomic unless the UI dispatcher and synchronization support that assumption. If the code changes, update the TLA actions, run original and mutant, review the Kotlin unit/Android tests, and only then advance the sync SHA in this skill. Use upstream pstack /statechart on the TLA+ spec to draw the checked transition graph where that command is installed.

**Limits:** max three source generations; no actual ContentResolver or permission revocation; no process-death persisted state; no real Compose lifecycle; source contract checks are textual, not refinement proofs. A green model is not a claim that Android runtime permissions work.
