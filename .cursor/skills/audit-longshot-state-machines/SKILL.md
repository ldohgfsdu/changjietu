---
name: audit-longshot-state-machines
description: Review or extend all control, UI, async and resource state machines in changjietu. Use before changing ViewModel, navigation, editor, exports, resource cleanup, storage permissions or release workflows.
---

# Audit changjietu state machines (UX / DX / AX)

**Authoritative entrypoint:** `docs/state-machines/registry.json`; open `docs/state-machines/AUDIT.md` for prioritization. This skill is an **audit index**, not an additional assertion that every machine has been formally proved.

1. Identify the affected registry IDs by source file, mutable flag, coroutine job, callback or resource lifetime. Prefer a single owner of each decision; preserve current exported behavior until tested.
2. Write the state, triggering event, guard, side effects, failure and recovery in the affected entry; classify confirmed source behavior separately from hypotheses and desired invariants.
3. Run `python3 tools/verify_state_machines.py`. Update anchors and relevant inventory entries for every new or moved state-bearing file.
4. For ingress, result navigation, zero-overlap cuts and export selection, run `bash formal/longshot/verify.sh`; consult the existing subsystem-specific verification Skills, mutate the model and check a failing variant.
5. For any production logic change run `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:lintVitalRelease --no-daemon` in a configured Android SDK environment, or check the equivalent PR GitHub Actions result.
6. Do not infer that CI green proves visual alignment, Android permission recovery, external sharing order, or the absence of races; require real Android runtime tests for those. Never claim zero regressions without checking the affected flows.
7. For a new risky domain, create a separate subsystem verification Skill and explicit model/test before replacing scattered flags. Record the code SHA under which the model corresponds to the code and advance only after retesting.

**Rules:** no broad enum rewrite, implicit backward-compatibility removal, new runtime dependency, speculative file deletion or auto APK publication in a state-machine audit. Keep a minimal behavior-preserving fix isolated in a PR. Prioritize UX: no duplicate work or lost data; DX: one state owner and deterministic test; AX: reproducible path, evidence and failure trace.

**Model coverage boundary:** only four existing TLC models were previously checked. The remaining entries are an auditable inventory, not formal certificates.
