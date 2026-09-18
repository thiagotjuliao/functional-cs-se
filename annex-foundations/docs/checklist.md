# Annex Track — Deliverables Contract

The annex track is **optional and non-blocking**: no main-curriculum module
waits on it. It exists to absorb the prerequisites the main modules assume you
already have, so that a gap discovered mid-module becomes a dedicated annex
entry instead of a detour.

Its completion bar, however, is identical to any other module. An annex is
complete only when **every** box below is `[x]`. Where a box asks for a measured
number, the number is written into that entry's file — an unrecorded
measurement is an unperformed measurement.

---

One file per annex entry.

| Entry | Topic | Boxes | Status |
| :-: | :--- | :-: | :--- |
| A1 | [Bitwise Arithmetic & Binary Representation](checklist/a1.md) | 34/34 | complete |

The answers that close each §G live in
[`challenge-log.md`](challenge-log.md), and the defects found along the
way in [`error-patterns.md`](error-patterns.md) — the latter is organised
by pattern rather than by entry, deliberately, and is not split.

---

## Annex Backlog

Concepts encountered in the main curriculum that were assumed rather than taught.
Add an entry the moment a gap is felt, with the module that exposed it — the
backlog is the annex's input queue, and a gap left unrecorded is a gap that
will be rediscovered under deadline in Block 4.

| Candidate | Exposed by | Status |
| :--- | :--- | :--- |
| Bitwise arithmetic & binary representation | B1-M1 discussion | **A1 — written** |
| Asymptotic analysis: formal O / Θ / Ω, amortised bounds | B1-M2 | *proposed* |
| IEEE-754: rounding, `NaN` ordering, why `Double` breaks equality laws | B1-M1 | *proposed* |
| Structural vs. reference equality, `hashCode`/`equals` contract | B2-M4 | *proposed* |
| Variance, bounds, and `Nothing` as the bottom type | B2-M5 | *proposed* |
| The JMM: happens-before, `volatile`, safe publication | B3-M9 | *proposed* |
| Character encodings: UTF-8/16 on the JVM, `Char` vs. code point | B4-M12 | *proposed* |
