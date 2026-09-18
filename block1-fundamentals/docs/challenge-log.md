# Block 1 — Conceptual Challenge Log

The Step 4 audit of the module routine asks precise technical questions about
design choices, and the answers are where the understanding actually lives. A
green test suite proves the code works; it proves nothing about whether the
author can say *why*. These files are the record of those answers.

One file per module. **Numbering is continuous across the whole block**, so
that any challenge can be cited by number alone; this table is how a bare
number is resolved to a file.

| Module | Topic | Challenges | Entries |
| :-: | :--- | :-: | :-: |
| 1 | [JVM Semantics & Immutability Allocation Stress](challenge-log/b1-m1.md) | 1–15 | 15 |
| 2 | [Manual Persistent Data Structures](challenge-log/b1-m2.md) | 16–28 | 13 |
| 3 | [Stack Optimization & Control Flow Elimination](challenge-log/b1-m3.md) | 29–44 | 16 |

Every number in them was executed and verified before being written —
bytecode read with `javap -c -p`, allocation taken from the `AllocationProbe`
instrument of Exercise 3, layout arithmetic cross-checked against the
`Footprint` implementation of Exercise 7, and stack ceilings measured with a
warmed `maxSurviving` (challenge 41 records what happened before it was
warmed).

Each module's file is the evidence behind the **§G, Oral Defence** box of the
matching [checklist](checklist.md). That box closes when every exercise has an
entry.
