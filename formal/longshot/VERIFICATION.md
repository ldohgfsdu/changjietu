# Verification evidence — 2026-11-09

**Source baseline:** `master@06347f41eabe727b3931a407e4ec88f3e68eaf31`. The branch adds verification assets only; Android product code is unchanged.

## Primary check

[GitHub Actions #42651560457](https://github.com/ldohgfsdu/changjietu/actions/runs/42651560457) completed successfully.

TLC v1.8.0 model checker (one worker):

| Model | Generated states | Distinct states | Invariants | Mutant intentionally rejected |
|---|---:|---:|---|---|
| ShareIngress | 200 | 83 | TypeOK, NoStaleSourceDisplayed, PendingBelongsToConsumedShare | NoStaleSourceDisplayed |
| ProcessResult | 197 | 99 | TypeOK, NoPhantomNavigation, PendingOnlyAfterSuccess | NoPhantomNavigation |
| SliceCuts | 131 | 80 | TypeOK, NoGapNoOverlap, ExactTotalHeight | NoGapNoOverlap |
| ExportSelection | 1,474 | 344 | TypeOK, NoEmptySubmission, OnlySubmittedSlicesDelivered | NoEmptySubmission |

A total of **606 distinct abstract states** was enumerated across these four independent finite models. This number is the sum of *per-model* distinct state counts, **not** a global composed state-space count.

The source-contract gate found 8 required Kotlin anchors and demonstrated rejection when those phrases were removed in-memory; those probes cannot prove the Android implementation semantically refines the models. Existing Kotlin JVM tests are a separate validation layer.

## Actual pstack statechart execution

The same CI run downloaded the pstack implementation from `cursor/plugins` at commit `6b0300214a09fd6029cfb7077043125758cd1fd2`, ran it with Java 17, Node 22.18.0 and its pinned TLC 1.7.4 SANY interface, and generated `chart.mmd`, `report.md` and TLC outputs for each of four subsystem models.

[Download the formal-statecharts artifact](https://github.com/ldohgfsdu/changjietu/actions/runs/42651560457/artifacts/13689717315). CI reported 12 files uploaded, artifact ID 13689717315.

The charts in `charts/` are a separate, intentionally simplified **user-facing** representation of important flows. Do not confuse them with pstack's exhaustive state charts in the artifact.

## Counterexample shape in negative controls

- Source import: open first image, open a new one, older decode finishes last; a mutant incorrectly displays the older result.
- Navigation: successful result is recorded, then manual edits improperly increment navigation count; invariant detects phantom completion.
- Cut geometry: dragging a seam then writing the second segment from `cut+1` drops one row.
- Export: the selected-slice snapshot is replaced with an empty set immediately before exporting.

These are **broken model variants**, not confirmed Kotlin defects. The production models satisfy their stated properties under the enumerated bounds.

## Exclusions and next validation targets

Not proven: on-device user interactions, file access grants and revocation, temporary file eviction while exports run, actual JPEG seam text safety, arbitrary number of slices, external receiving-app order, app/process death recovery, or an Android code-to-TLA refinement relation. In particular, the export snapshot model assumes the slice files remain readable for the lifetime of the export. This is not a proven Android file-lifetime guarantee.

After a change in any mapped code path, update its subsystem Skill and model, and rerun `bash formal/longshot/verify.sh` and the pstack statechart CI workflow. Do not declare a production bug fixed based solely on a model pass.
