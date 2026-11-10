# changjietu — implicit state-machine audit

**Baseline examined:** `master b008d50c9ae6904a1a9bdf8efca0871bc19d9f32` (2026-11-10).
**Scope:** all `app/src/main/java` Kotlin production files, the two Actions workflows and the known TLC specs. The machine-readable `registry.json` contains **29** separately owned control states or resource lifecycles. A pure stateless image transform is not automatically classified as a state machine.

## Verdict

The existing four abstract TLC models are valuable but **not exhaustive**: ingress, success-navigation, pixel seams and export selection omit other mutable states. In particular, a stable exported *selection* does not imply stable file contents, file existence, granted permissions or a still-active Android consumer.

**Three high-risk priorities** (risk, not confirmed reproduced defects):

- **RES-01 — file lifetime:** `LongshotViewModel.cleanupOldEntries` deletes old slice or ZIP cache entries according to a count, without any active export or external-receiver lease. A file can be queued for MediaStore, ZIP or FileProvider access while an unrelated new operation cleans it. Before changing retention, model owned directories, snapshot references, active jobs and handoff expiry; test the interleavings with a fake filesystem.
- **PROC-03 — last-wins re-encoding:** `reprocessFromRanges` cancels its prior job and uses a source snapshot, then conditionally publishes the new editor. Need a deterministic scheduler test for overlapping drag/merge/delete, selection retention and switch-source cancellation; do not assume a cancellation immediately stops all blocking IO.
- **CI-01 — release:** verify/assemble/sign/draft/readback/publish is already explicitly guarded. Preserve monotonic versionCode, signer continuity, fail-closed draft and checksum readback. Never let a docs/audit PR trigger publication.

**One low-risk change in this branch:** `PROC-01` now sets `_editor.isProcessing=true` **before** `viewModelScope.launch`, so the existing guard rejects a fast second generate tap before the async body starts. It does not alter image geometry, selection, persistence or export. Source-order contract checks are supplemented by Android CI; the real-device double-tap test remains outstanding.

## Registry taxonomy

| Group | IDs | Main UX contract |
| --- | --- | --- |
| Navigation | NAV-01, PROC-02 | No unexpected or duplicate screen transitions |
| Import, share and source | ING-01, URI-01, SRC-01–03 | Last opened source wins; failed URI can recover |
| Settings and geometry | CFG-01–02, CUT-01–04 | Manual position stays stable; exact end-exclusive seams |
| Processing | PROC-01, PROC-03–04 | No duplicate work, no stale publish, rollback on failure |
| Selection and output | SEL-01, OUT-01–05 | No unwanted file omission or incomplete output |
| Local UI | UI-01–03 | Busy, dialog and edit draft have predictable back/rotation behavior |
| History | REC-01 | Reopen staleness gives a recoverable result |
| Resources | RES-01–02 | Referenced files and decoded pixel buffers outlive their readers |
| Delivery | CI-01 | No unsigned, unverified or accidentally published release |

## UX / DX / AX decision rule

1. **UX:** prioritize silent data loss, double submissions, unexpected navigation and unrecoverable failures over cosmetic changes. A stale file reference is higher priority than adding new controls.
2. **DX:** first make current state owners and transitions discoverable, then add scenario tests. Do not wrap every Boolean in a new enum without eliminating a real invalid state.
3. **AX:** keep the registry source-linked and machine-readable, with an executable verification gate. A green check establishes mapping and the four bounded model properties; it cannot certify real-device functionality.
4. **No regressions:** preserve current exported formats, filenames, sorting, pixel ranges and Android minSdk; keep behavior changes isolated. Require both Android verify and formal gate to pass on the exact PR head.

## Further acceptance gates (not completed here)

- Simulate older image analysis and reprocess jobs completing after source replacement; verify that neither editor nor navigation shows stale output.
- Model concurrent ZIP/photo exports and cache cleanup with an injected IO clock: no referenced file deleted until every reader has finished. Preserve external-share TTL or handoff semantics.
- Run API 26–28 deny/grant/rotate and API 29+ MediaStore failure tests, including a revoked URI from Recents.
- On device, test 4–9 text slices with adjacent boundary pixel comparison, quick double-tap generation, cut editing and results selection across rotation.
- Separately test multi-image share recipient ordering (the receiving app may reorder).
- Do not interpret a missing manual test as proof that the behavior is safe.

## Repeatable audit

```bash
python3 tools/verify_state_machines.py
bash formal/longshot/verify.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:lintVitalRelease --no-daemon
```

The first script checks entry uniqueness, source linkage, future stateful-file discovery and a mandatory failing control mutation for initial-process admission. It is **not a semantic static analyzer**. The second performs four *bounded* TLC model checks and negative controls. Android tests and real devices are a separate layer.
