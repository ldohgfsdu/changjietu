# Longshot formal verification and statecharts

This module adopts the method described by Cursor pstack's create-formal-verification-skill and statechart. It is a **project-local** implementation: these commands are not installed in this chat environment.

Run from repository root: **bash formal/longshot/verify.sh**.

The script downloads upstream TLC 1.8.0 (official GitHub release; release SHA-1 verified), exhaustively checks four finite-state models, requires each deliberately broken model to fail on an invariant, and probes mapped Kotlin source anchors. GitHub Actions also runs the script.

| Subsystem | Bound | Primary property | Skill |
| --- | --- | --- | --- |
| Image ingress and share | 3 image generations, 3 shares | Old analysis cannot displace latest source | .cursor/skills/verify-longshot-ingress/SKILL.md |
| Processing completion | 3 successful results, 3 manual edits | Navigation only after success | .cursor/skills/verify-longshot-navigation/SKILL.md |
| Cut editing | 6 abstract rows, 2 re-encodes | Exclusive seam has no gap/overlap | .cursor/skills/verify-longshot-cuts/SKILL.md |
| Export selection | 3 slices, 2 exports | Nonempty immutable export snapshot | .cursor/skills/verify-longshot-export/SKILL.md |

The Mermaid diagrams in charts/ are hand-reviewed, user-language **projections**, not outputs of the upstream pstack statechart program. To get auto-marked counterexample statecharts, run pstack /statechart against specs/*.tla and the matching configs in an environment where pstack has been installed. Do not label the diagrams as automatically TLC-extracted.

**Last mapped product-code snapshot:** 06347f41eabe727b3931a407e4ec88f3e68eaf31. Advance each Skill's sync commit only after examining any changed Kotlin statements and rerunning the model and relevant JVM/Android tests. Model mutations prove that the *model* would detect broken behavior, not that a particular Kotlin mutant was compiled and tested.

**Not covered:** Android lifecycle/permission revocation, cache cleanup during in-progress export, cross-app reorder of images, JPEG text quality, real phone layout, crash recovery, unbounded concurrency. Bounded TLC safety checks are not end-to-end Android proofs. See each Skill for boundaries.
