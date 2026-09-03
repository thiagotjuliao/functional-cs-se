# Block 1 — Deliverables Contract

A module is complete only when **every** box below is `[x]`. Partial completion
does not unlock the next module. Where a box asks for a measured number, write
the number into this file — an unrecorded measurement is an unperformed
measurement.

---

## Module 1 — JVM Semantics & Immutability Allocation Stress

**Milestone tag:** `b1-m1-jvm-semantics`

### A. Theory Comprehension

- [ ] Read `docs/theory/module1_jvm.md` in full.
- [ ] Read JVM Specification §2.5–2.6 and JVM Anatomy Quarks #4 and #18.
- [ ] Answer all five questions in §9 (Self-Check) of the module guide **in
      writing**, without re-reading the guide. Append the answers to this file
      under "Self-Check Answers".
- [ ] Derive, from the object-layout rules alone, the heap cost of a
      `List[Int]` and an `Array[Int]` of one million elements. Both numbers must
      match your Exercise 7 implementation.

### B. Implementation — Exercises

All nine exercises live in
`src/main/scala/cs/se/block1/module1/` and are validated by
`src/test/scala/cs/se/block1/module1/Module1Spec.scala`.

- [ ] **E1 `Vec2`** — extension methods `+`, `*`, `dot`, `norm`.
- [ ] **E2 `Shape`** — `enum` ADT with exhaustive `area` and `totalArea`.
- [ ] **E3 `AllocationProbe`** — `allocatedBytes` and `measure`.
- [ ] **E4 `Boxing`** — `sumBoxed` and the allocation-free `sumPrimitive`.
- [ ] **E5 `Escape`** — `sumNorms` (non-escaping) and `collectVecs` (escaping).
- [ ] **E6 `WordStats`** — `wordFrequencies` and `topN`.
- [ ] **E7 `Footprint`** — `shallowSize`, `arrayOfIntSize`, `listOfIntSize`.
- [ ] **E8 `Csv`** — `renderCsv` under an allocation ceiling.
- [ ] **E9 `Bench`** — `medianNanos` harness.

### C. Correctness Gate

- [ ] `sbt fundamentals/test` — **all tests green**, zero ignored, zero skipped.
- [ ] `sbt fundamentals/compile` succeeds under `-Wall -Werror` with **zero**
      warnings suppressed by annotation or configuration.
- [ ] `sbt scalafmtCheckAll` passes.

### D. Purity Gate

Verified by reading your own diff before committing:

- [ ] Zero occurrences of `var` in `src/main/scala`.
- [ ] Zero `while` loops and zero imperative `for` loops.
- [ ] Zero `throw` and zero `try`/`catch` in exercise implementations.
- [ ] Zero mutable collections (`scala.collection.mutable.*`).
- [ ] Every function is total for its documented domain, or its partiality is
      encoded in the return type.
- [ ] The single permitted impurity is `AllocationProbe` / `Bench` reading the
      JVM's own instrumentation — these are *measuring instruments*, and the
      exception is deliberate.

### E. Empirical Gate — Record The Numbers

The point of this module is measurement. Fill in every blank:

- [ ] **Baseline run.** `sbt fundamentals/test` with default flags.
      - `Escape.sumNorms` allocation over 200,000 elements: `______ bytes`
      - `Escape.collectVecs` allocation over 200,000 elements: `______ bytes`
      - Observed ratio: `______ ×`

- [ ] **Controlled experiment.** Identical suite with escape analysis disabled:
      ```bash
      sbt "set fundamentals/Test/javaOptions += \"-XX:-DoEscapeAnalysis\"" fundamentals/test
      ```
      - `Escape.sumNorms` allocation with EA off: `______ bytes`
      - The escape-analysis test **is expected to fail** in this configuration.
        Confirm it fails, and state in one sentence why that failure is the
        proof rather than a defect: `______________________`

- [ ] **Boxing tax.** Allocation of `sumBoxed` vs `sumPrimitive` over 100,000
      elements, measured after warmup:
      - `sumBoxed`: `______ bytes` · `sumPrimitive`: `______ bytes`
      - Explain the residual, if `sumPrimitive` is not exactly zero:
        `______________________`

- [ ] **GC observation.** Run the suite with `-Xlog:gc` and record:
      - Number of young collections: `______`
      - Longest pause: `______ ms`
      - Collector actually in use: `______`

- [ ] **Warmup effect.** Using `Bench.medianNanos`, record the per-iteration cost
      of `sumNorms` at 100 iterations vs at 200,000 iterations:
      - Cold: `______ ns` · Warm: `______ ns` · Speedup: `______ ×`

### F. Engineering Hygiene

- [ ] All code formatted (`sbt scalafmtAll`) with no manual override.
- [ ] Every public definition carries a Scaladoc stating its **contract**, not a
      restatement of its name.
- [ ] Commits follow `docs/git-conventions.md` (`b1-m1: <imperative summary>`),
      one commit per concept proven.
- [ ] Annotated milestone tag `b1-m1-jvm-semantics` created, using the message
      template in `docs/git-conventions.md`, with a real entry under `Learned:`.

### G. Oral Defence

- [ ] Answer the post-module conceptual challenges (Step 4 of the routine)
      without consulting the guide.

---

## Self-Check Answers

> Write your §9 answers here before requesting the module audit.

1.
2.
3.
4.
5.
