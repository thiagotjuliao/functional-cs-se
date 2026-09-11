# Block 1 — Deliverables Contract

A module is complete only when **every** box below is `[x]`. Partial completion
does not unlock the next module. Where a box asks for a measured number, write
the number into this file — an unrecorded measurement is an unperformed
measurement.

---

## Module 1 — JVM Semantics & Immutability Allocation Stress

**Milestone tag:** `b1-m1-jvm-semantics`

### A. Theory Comprehension

- [x] Read `docs/theory/module1_jvm.md` in full.
- [x] Read JVM Specification §2.5–2.6 and JVM Anatomy Quarks #4 and #18.
- [x] Derive, from the object-layout rules alone, the heap cost of a
      `List[Int]` and an `Array[Int]` of one million elements. Both numbers must
      match your Exercise 7 implementation.

### B. Implementation — Exercises

All nine exercises live in
`src/main/scala/cs/se/block1/module1/` and are validated by
`src/test/scala/cs/se/block1/module1/Module1Spec.scala`.

- [x] **E1 `Vec2`** — extension methods `+`, `*`, `dot`, `norm`.
- [x] **E2 `Shape`** — `enum` ADT with exhaustive `area` and `totalArea`.
- [x] **E3 `AllocationProbe`** — `allocatedBytes` and `measure`.
- [x] **E4 `Boxing`** — `sumBoxed` and the allocation-free `sumPrimitive`.
- [x] **E5 `Escape`** — `sumNorms` (non-escaping) and `collectVecs` (escaping).
- [x] **E6 `WordStats`** — `wordFrequencies` and `topN`.
- [x] **E7 `Footprint`** — `shallowSize`, `arrayOfIntSize`, `listOfIntSize`.
- [x] **E8 `Csv`** — `renderCsv` under an allocation ceiling.
- [x] **E9 `Bench`** — `medianNanos` harness.

### C. Correctness Gate

- [x] `sbt fundamentals/test` — **all tests green**, zero ignored, zero skipped.
- [x] `sbt fundamentals/compile` succeeds under `-Wall -Werror` with **zero**
      warnings suppressed by annotation or configuration.
- [x] `sbt scalafmtCheckAll` passes.

### D. Purity Gate

Verified by reading your own diff before committing:

- [x] Zero occurrences of `var` in `src/main/scala`. Swept: every hit is inside
      a Scaladoc block.
- [x] Zero `while` loops and zero imperative `for` loops. Swept: same, every hit
      is prose. Both recursive walks (`Boxing.sumPrimitive`, `Escape.sumNorms`)
      are `@tailrec`.
- [x] Zero `throw` and zero `try`/`catch` in exercise implementations. Swept:
      no occurrence of any of the three.

      Two `require` calls do exist, in `Escape.sumNorms` and
      `Escape.collectVecs`, and `require` throws. They are not a violation of
      this box but the subject of the next one, where the decision is recorded.
- [x] Zero mutable collections (`scala.collection.mutable.*`). Swept: no
      occurrence. `Array` appears as a *primitive layout*, not as a collection
      interface — `Array[Int]` is the control against which `List[Int]`'s 10×
      tax is measured, and `Array[Double]` is the input to the escape-analysis
      experiment.
- [x] Every function is total for its documented domain, or its partiality is
      encoded in the return type.

      Two functions needed a decision rather than a sweep, and one of them
      needed the contract repaired:

      - `Escape.sumNorms` / `collectVecs`. Domain: pairs of equal-length
        arrays; total within it. Unequal lengths are a **defect at the call
        site**, not a domain case — both arrays describe the same points, so
        they differ in length only if the caller built them wrongly — and the
        `require` marks the domain boundary rather than handling an input.
        `Option[Double]` was rejected deliberately: it would force every correct
        caller to handle an impossible case and invite the `.getOrElse(0.0)`
        that converts a loud defect into a silent wrong answer. Now recorded in
        the Scaladoc.
      - `Footprint.align`. The contract claimed totality over **every**
        non-negative `Int`, and that claim is unsatisfiable by any `Int => Int`:
        for the top seven inputs the answer is `2^31`, so `bytes + 7` wraps and
        `align(Int.MaxValue)` returns `Int.MinValue`. The domain is now
        documented as `[0, Int.MaxValue - 7]` and pinned by a test, including an
        assertion on the behaviour outside it. Recorded as pattern 6 in
        [`error-patterns.md`](error-patterns.md), alongside `Vec2.norm`'s
        promise of non-negativity, which `NaN` breaks the same way.
      - `Bench.medianNanos` returns `-1L` for `iterations < 1`. Total, and the
        sentinel is documented — but it is in-band signalling in the same `Long`
        domain as a legitimate duration, which the type cannot distinguish. Left
        as written and recorded, rather than closed silently.
- [x] The single permitted impurity is `AllocationProbe` / `Bench` reading the
      JVM's own instrumentation — these are *measuring instruments*, and the
      exception is deliberate. Three limits of the instrument are documented in
      [`challenge-log.md`](challenge-log.md), entry 4: it counts only the
      calling thread, it requires a forked JVM, and it can return a negative
      count if per-thread measurement is disabled between the two readings.

### E. Empirical Gate — Record The Numbers

The point of this module is measurement. Fill in every blank.

All numbers below were executed on 2026-09-11: JDK 21.0.9 HotSpot, 64-bit,
compressed oops on, Windows 11, in the forked test JVM that `build.sbt` pins to
`-Xmx2g -XX:+UseG1GC`.

- [x] **Baseline run.** `sbt fundamentals/test` with default flags.
      - `Escape.sumNorms` allocation over 200,000 elements: `48 bytes`
      - `Escape.collectVecs` allocation over 200,000 elements: `10,398,016 bytes`
      - Observed ratio: `216,625.33 ×`
      - Only 6,400,000 of those bytes are `Vec2` instances. The full
        decomposition, predicted from Exercise 7 and accurate to 48 bytes in
        10.4 MB:
        ```text
        Vec2[] of 200,000 references    16 + 4 x 200,000  =     800,016
        200,000 x Vec2 @ 32                               =   6,400,000
        (200,000 - 128) x Integer @ 16                    =   3,197,952
                                                              ----------
        predicted                                            10,397,968
        measured                                             10,398,016
        ```
        The third term is not `Vec2` at all: `Array.tabulate(n)(f: Int => Vec2)`
        calls `f` through `Function1`, which has no specialised variant with a
        reference return type. Every call therefore goes through the erased
        `apply(Object): Object` and boxes the loop index into a 16-byte
        `java.lang.Integer`. The `- 128` is the `Integer.valueOf` cache of
        Part V.19: omitting that term leaves a 2,048-byte gap, which is exactly
        128 boxes that were never allocated.

- [ ] **Controlled experiment.** Identical suite with escape analysis disabled:
      ```bash
      sbt "set fundamentals/Test/javaOptions += \"-XX:-DoEscapeAnalysis\"" fundamentals/test
      ```
      - `Escape.sumNorms` allocation with EA off: `6,400,048 bytes`
      - Observed suite result: `Exercise5EscapeSpec`, test *"sumNorms is
        scalar-replaced while collectVecs is not"*, failed at line 60. The other
        24 tests stayed green, and `collectVecs` moved by 120 bytes — it had
        nothing for escape analysis to remove in the first place.
      - Exercise 7 predicts the number on paper, to the byte:
        ```text
        6,400,048 - 48 = 6,400,000 = 200,000 x shallowSize(0, 0, 0, 2, 0)
                                   = 200,000 x 32
        ```
      - The escape-analysis test **is expected to fail** in this configuration.
        Confirm it fails, and state in one sentence why that failure is the
        proof rather than a defect: `______________________`

- [ ] **Boxing tax.** Allocation of `sumBoxed` vs `sumPrimitive` over 100,000
      elements, measured after warmup:
      - `sumBoxed`: `2,400,024 bytes` · `sumPrimitive`: `24 bytes`
      - The `sumBoxed` figure is *not* the `Integer` boxes of the list — those
        already existed before the probe took its first reading. It is the
        `Long` accumulator, re-boxed once per fold step:
        ```text
        2,400,024 - 24 = 2,400,000 = 100,000 x shallowSize(0, 0, 1, 0, 0)
                                   = 100,000 x 24
        ```
      - Explain the residual, if `sumPrimitive` is not exactly zero:
        `______________________`
        (Evidence to reason from, all measured: `bytesOf(())` costs 0 bytes,
        `bytesOf` of an `Int`-valued body costs 16, of a `Long`-valued body 24.
        The signature is `AllocationProbe.measure[A](body: => A)`.)

- [x] **GC observation.** Run the suite with `-Xlog:gc` and record:
      - Number of young collections: `3` — and `0` full collections.
      - Longest pause: `5.514 ms` (run-to-run range over two runs: 2.5–6.1 ms)
      - Collector actually in use: `G1` — pinned by `-XX:+UseG1GC` in
        `build.sbt`, not inherited as the JDK 21 default.
      - Raw log:
        ```text
        [0.004s][gc] Using G1
        GC(0) Pause Young (Normal) (G1 Evacuation Pause)  55M->7M(1022M)  2.512ms
        GC(1) Pause Young (Normal) (G1 Evacuation Pause)  73M->32M(1022M) 5.514ms
        GC(2) Pause Young (Normal) (G1 Evacuation Pause) 154M->33M(1022M) 2.628ms
        ```
        The committed heap is 1022M against an `-Xmx2g` ceiling: the whole
        suite's churn is absorbed by three young evacuations and never grows the
        heap to its limit.

- [x] **Warmup effect.** Using `Bench.medianNanos`, record the per-iteration cost
      of `sumNorms` at 100 iterations vs at 200,000 iterations:
      - Cold: `38,500 ns` · Warm: `600 ns` · Speedup: `64 ×`
      - Measured over arrays of 1,000 elements, four independent JVM runs:
        cold `38,200 / 38,700 / 38,650 / 38,500`, warm `600` in all four.
      - **Read the warm figure with its error bar.** `System.nanoTime()` on this
        machine advances in steps of 100 ns, which is why every timing in this
        module is a multiple of 100:
        ```text
        smallest non-zero delta between two nanoTime reads   =  100 ns
        consecutive reads returning an identical value       =  817,805 / 1,000,000
        cost of one nanoTime call                            =  17.6 ns
        ```
        The call is six times cheaper than the resolution of what it measures.
        `600 ns` is six ticks, so the speedup carries ±8% from quantisation
        alone — before any other source of noise. A one-tick measurement such as
        the `work(10) = 100 ns` reported by `Exercise9BenchSpec` carries no
        information at all: its true value lies somewhere in (0, 200).

### F. Engineering Hygiene

- [x] All code formatted (`sbt scalafmtAll`) with no manual override.
- [ ] Every public definition carries a Scaladoc stating its **contract**, not a
      restatement of its name.
- [x] Commits follow `docs/git-conventions.md` (`b1-m1: <imperative summary>`),
      one commit per concept proven.
- [ ] Annotated milestone tag `b1-m1-jvm-semantics` created, using the message
      template in `docs/git-conventions.md`, with a real entry under `Learned:`.

### G. Oral Defence

- [x] Work the post-module conceptual challenges (Step 4 of the routine) as a
      dialogue: attempt each one **before** the discussion, say "I don't know"
      plainly when that is the truth, and let `challenge-log.md` carry the
      complete answer the exchange produced.
      - Fifteen challenges over three rounds, all recorded in
        [`challenge-log.md`](challenge-log.md). Every one of the nine exercises
        has at least one entry, and the four questions carried over below have
        their own section there.
      - The order held. Of the eleven exercise challenges, three were answered
        unaided or nearly so (the boxed accumulator of E2 after one correction,
        the seed asymmetry, the mechanism behind E4's 24 bytes); the rest got an
        honest "I don't know" and were taught from there, along with all four
        carried-over questions. Both outcomes are the box working as intended —
        what is measured is whether the attempt came first, not whether it
        landed.
      - Two defects surfaced from the challenges rather than from the suite, and
        both were fixed: `Vec2.norm` now uses `Math.hypot`, and the boxing of
        `totalArea`'s fold is now measured by a test instead of assumed.

**Carried over from the removed Self-Check.** Four of its six questions ask
something no other artifact here covers. They are recorded here so the Step 4
round could put them rather than losing them with the section they came from,
and all four are now answered in
[`challenge-log.md`](challenge-log.md#carried-over-from-the-removed-self-check),
entries 12 through 15:

1. A service allocates 800 MB/s with 3 ms young pauses. The rate doubles to
   1.6 GB/s and the pause *duration* stays at 3 ms. Why — and what would have to
   change for the duration to grow? (Part II.8)
2. Why does adding an object pool to a hot path frequently make p99 latency
   *worse*? (Parts II.7, III.10, VII.1)
3. Why does an immutable structure produce cheaper write-barrier traffic than a
   mutable one, given that it performs strictly more stores overall?
   (Part III.11)
4. You measure zero allocation in a loop that visibly constructs a case class per
   iteration. Name the optimisation, the tier that performs it, and two distinct
   code changes that would silently switch it off. (Parts IV.15, IV.16)

The other two are already covered: the object-layout derivations are the fourth
box of §A and Exercise 7, and the `List[Int]` versus `Array[Int]` ratio is the
same box.

