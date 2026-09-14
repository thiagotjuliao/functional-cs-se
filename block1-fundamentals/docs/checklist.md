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

- [x] **Controlled experiment.** Identical suite with escape analysis disabled:
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
        proof rather than a defect: **the test asserts a property of the
        optimiser rather than of the code, so removing the optimiser must remove
        the property — a test that stayed green under `-XX:-DoEscapeAnalysis`
        would be proof that it had never been measuring escape analysis at all.**

        Note that only this one test flipped. The other 24 assert mathematics,
        which no JVM flag can change.

- [x] **Boxing tax.** Allocation of `sumBoxed` vs `sumPrimitive` over 100,000
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
        **the 24 bytes belong to the instrument, not to `sumPrimitive`.**
        `measure[A](body: => A)` takes its body by name, and `A` is erased to
        `Object`, so the `Long` that `sumPrimitive` returns is boxed into a
        `java.lang.Long` *between* the two readings of the counter. The
        tail-recursive walk itself allocates nothing at all.

        The three measurements that isolate it, and they agree with Exercise 7
        rather than with a guess:
        ```text
        bytesOf(())                    =   0     Unit boxes to a singleton
        bytesOf(an Int-valued body)    =  16     = shallowSize(0, 1, 0, 0, 0)
        bytesOf(a Long-valued body)    =  24     = shallowSize(0, 0, 1, 0, 0)
        ```
        The instrument has a floor, the floor depends on the *return type* of
        what is measured, and the floor is 24 here. Every assertion built on this
        probe must carry a tolerance for exactly that reason — which is why
        `Exercise4BoxingSpec` asserts `< 2_048` rather than `== 0`.

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
- [x] Every public definition carries a Scaladoc stating its **contract**, not a
      restatement of its name.

      Audited mechanically rather than by impression. Nine public definitions
      had no Scaladoc at all and now do: `Footprint`'s eight layout constants and
      `AllocationProbe.bean`. The constants follow the rule stated by pattern 4
      of [`error-patterns.md`](error-patterns.md) — each names the assertion that
      would fail if its value were wrong, or admits that none would and gives
      the range it could still take. `bean` carries the three preconditions its
      type cannot express.

      What the audit still reports, and why it is not a gap: ten method-**local**
      bindings (`loop`, `timedRuns`, `firstReading`, `headAndTail` and the like),
      which are not public definitions, and the companion objects `Vec2` and
      `Shape`, whose contract is stated by the class and the enum immediately
      above them and whose every member is documented.
- [x] Commits follow `docs/git-conventions.md` (`b1-m1: <imperative summary>`),
      one commit per concept proven.
- [x] Annotated milestone tag `b1-m1-jvm-semantics` created, using the message
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

---

## Module 2 — Manual Persistent Data Structures

**Milestone tag:** `b1-m2-persistent-structures`

The exercise order is deliberate and is not by difficulty. **E1 comes first
although it is the hardest tier**, because it builds the cost model that predicts
what every later exercise measures. Module 1 put its predictive exercise seventh
of nine, and the consequence was that its measurements arrived as isolated facts
instead of as confirmations of a derivation. Do E1 first, on paper, before
writing a line of `MyList`.

### A. Theory Comprehension

- [ ] Read `docs/theory/module2_structures.md` in full.
- [ ] Work `docs/quiz/b1-m2.html`, filtering by Part as you finish each one.
- [ ] Read Okasaki, *Purely Functional Data Structures*, Ch. 2.
- [x] Derive, before measuring anything, the number of cells allocated by
      `x :: xs`, by `xs :+ x` and by `xs.reverse` over a list of `n`. All three
      must match your Exercise 1 implementation. Derived as `1`, `n` and `n`:
      the prepend shares the whole old list, and the other two rebuild the
      spine while sharing every element it holds.
- [x] State, in one line each, what `O(1)`, `O(n)` and `O(log n)` predict about
      the *ratio* of costs when `n` doubles. This is the whole of §7 and it is
      what Exercise 5 measures.
      - **O(1)** — the ratio is `1`: the cost is invariant in `n`, so doubling
        `n` changes nothing to measure.
      - **O(n)** — the ratio is `2`: the cost doubles with `n`, and `O(n^2)`
        quadruples, which is the pair Exercise 5 separates.
      - **O(log n)** — the ratio is not a constant at all. Doubling `n` adds one
        *level*, an additive `+1` on a total that keeps growing, so the ratio
        descends toward `1`: `1.200` at `n = 16`, `1.091` at `1,024`, `1.048`
        at `2^20`, `1.032` at `2^30`.
      - The consequence, and the reason Exercise 9 measures depth rather than
        ratios: at large `n` the doubling test cannot tell `O(log n)` from
        `O(1)`, because `1.03` against `1.00` is smaller than the measurement
        noise. For a logarithm you count levels, not ratios.

### B. Implementation — Exercises

All nine live in `src/main/scala/cs/se/block1/module2/`, one spec each under
`src/test/scala/cs/se/block1/module2/`.

- [x] **E1 `Sharing`** *(do this first)* — the cost model: cells allocated and
      shared per operation, balanced depth, and the tree sharing ratio. Pure
      arithmetic, no data structure, no measurement.
- [x] **E2 `MyList`** — the `enum` ADT, `isEmpty`, `length`, `headOption`, and
      the variance that makes `Nil` serve every element type.
- [x] **E3 `Combinators`** — `map`, `filter`, `reverse`.
- [x] **E4 `Folds`** — `foldLeft`, `foldRight`, `append`, `concat`, and the
      stack-depth difference between the two folds.
- [x] **E5 `Building`** — `byAppend` and `byPrepend`, and the doubling table
      that proves their complexity classes.
- [x] **E6 `MyTree`** — the BST `enum`: `insert`, `contains`, `size`, `depth`.
- [x] **E7 `TreeFold`** — `foldInOrder`, `toList`, `treeMap`, and the ordering
      law that ties them together.
- [ ] **E8 `SharingProof`** — measure with `AllocationProbe` and confirm, or
      refute, every prediction E1 made.
- [ ] **E9 `Balance`** — `fromSorted` against `fromBalanced`, and what the
      depth difference does to the cost of one insert.

### C. Correctness Gate

- [ ] `sbt fundamentals/test` — **all tests green**, zero ignored, zero skipped.
- [ ] `sbt fundamentals/compile` succeeds under `-Wall -Werror` with **zero**
      warnings suppressed by annotation or configuration.
- [ ] `sbt scalafmtCheckAll` passes.
- [ ] Module 1's 28 tests still pass. This module adds to the suite; it does not
      replace it.

### D. Purity Gate

Verified by reading your own diff before committing:

- [ ] Zero occurrences of `var` in `src/main/scala/cs/se/block1/module2`.
- [ ] Zero `while` loops and zero imperative `for` loops.
- [ ] Zero `throw` and zero `try`/`catch`.
- [ ] Zero mutable collections, and zero use of `scala.collection.immutable.List`
      *inside* your own structure's implementation. `MyList` is built from
      `MyList`, or the exercise proves nothing. Converting to `List` at the
      boundary, in `toList`, is the one permitted crossing.
- [ ] Every recursive function that walks a whole structure is either
      `@tailrec` or documented as bounded by depth rather than by size. `MyTree`
      recursion is the second kind; `MyList` recursion must be the first.
- [ ] `head` on an empty list: the decision is made, documented in the Scaladoc,
      and defended in §G. Module 1 asked the same question of
      `Escape.sumNorms`; the answer here may differ, but it may not be absent.

### E. Empirical Gate — Record The Numbers

Every number below must be produced by *your* structure, not by Scala's. Fill in
every blank, and compare each against what Exercise 1 predicted **before** you
ran it.

- [x] **Cell and node size.**
      - `Sharing.CellBytes`: `24` · `Sharing.NodeBytes`: `24`
      - These are `Footprint.shallowSize` calls from Module 1. If they disagree
        with 24, one of the two modules is wrong: `they agree — align(12 + 2*4)`
        `= align(20) = 24 and align(12 + 3*4) = align(24) = 24, so the node's`
        `third reference lands inside padding the cell was already paying for`

- [ ] **List operations over `n = 100,000`.** Predicted from E1, then measured
      with `AllocationProbe` in E8:

      | operation | E1 predicts (bytes) | measured (bytes) | agree? |
      | :--- | ---: | ---: | :---: |
      | `x :: xs` | `______` | `______` | |
      | `xs :+ x` | `______` | `______` | |
      | `xs.reverse` | `______` | `______` | |
      | `xs.map(identity)` | `______` | `______` | |

      - Where prediction and measurement differ, the difference is itself a
        result. Account for it: `______________________`

- [x] **The doubling table.** Build a list of `n` elements both ways and record
      the bytes, for `n` = 2,000 / 4,000 / 8,000 / 16,000:

      | `n` | `byAppend` | × prev | `byPrepend` | × prev |
      | ---: | ---: | ---: | ---: | ---: |
      | 2,000 | `96,059,944` | — | `155,944` | — |
      | 4,000 | `384,123,944` | `3.999` | `315,944` | `2.026` |
      | 8,000 | `1,536,251,944` | `3.999` | `635,944` | `2.013` |
      | 16,000 | `6,144,508,128` | `4.000` | `1,275,944` | `2.006` |

      - The two `× prev` columns are the proof. State the complexity class each
        one demonstrates, and why the ratio is the evidence rather than the
        absolute number: `byAppend quadruples per doubling, which is O(n^2);`
        `byPrepend doubles, which is O(n). The ratio is the evidence because it`
        `is a property of the algorithm, while the absolute number is a`
        `property of this machine — 24-byte cells under compressed oops, and a`
        `16-byte Integer for each boxed element. Run the same code on a JVM`
        `without compressed oops and every byte count changes; the ratios do`
        `not move.`

      Both columns close to the byte, which is worth recording because each
      term names something in the source:

      ```text
      byPrepend(n)  =  48n  +  32n  -  4,056   bytes
      byAppend(n)   =  24n^2 + 32n  -  4,056   bytes

      n         measured          model             delta
      -------   ---------------   ---------------   -----
        2,000        96,059,944        96,059,944       0
        4,000       384,123,944       384,123,944       0
        8,000     1,536,251,944     1,536,251,944       0
       16,000     6,144,508,128     6,144,507,944     184

        2,000           155,944           155,944       0
        4,000           315,944           315,944       0
        8,000           635,944           635,944       0
       16,000         1,275,944         1,275,944       0
      ```

      - `24n^2` — and the square is literal, not asymptotic. `appended` is
        `concat(Cons(x, Nil))`, and `concat` reverses before it prepends, so
        appending to a list of `k` cells costs `2k + 1`. Summed over
        `k = 0 .. n-1` that is `1 + 3 + 5 + ... + (2n - 1)`, the sum of the
        first `n` odd numbers, which is exactly `n^2`.
      - `48n` — `2n` cells: `n` prepends and one final `reverse`. The same
        double spine as `map` and `filter`, for the same `@tailrec` reason.
      - `32n - 4,056` — boxing, in both. Two `Integer` per element at 16 bytes
        each: one in `Cons(x, Nil)`, one crossing `Function2`, which has no
        specialised variant with a reference return type. The subtraction is
        the `Integer.valueOf` cache: values `0..127` are shared and cost
        nothing. Module 1, §19.

- [ ] **Tree sharing.** On a balanced tree of `2^20 − 1` nodes:
      - whole tree: `______ bytes` · one `insert`: `______ bytes`
      - nodes copied: `______` · depth: `______` · sharing ratio: `______ ×`
      - The nodes copied should exceed the depth by exactly one. Say why:
        `______________________`

- [ ] **Degeneration.** Insert 4,096 values in sorted order and in shuffled
      order, and record for each:
      - sorted: depth `______`, one insert costs `______ bytes`
      - shuffled: depth `______`, one insert costs `______ bytes`
      - ratio: `______ ×`
      - Name the production inputs that arrive pre-sorted:
        `______________________`

### F. Engineering Hygiene

- [ ] All code formatted (`sbt scalafmtAll`) with no manual override.
- [ ] Every public definition carries a Scaladoc stating its **contract**, not a
      restatement of its name. Constants carry the rule from pattern 4 of
      `error-patterns.md`: name the assertion that pins the value, or state that
      none does.
- [ ] Commits follow `docs/git-conventions.md` (`b1-m2: <imperative summary>`),
      one commit per concept proven.
- [ ] `error-patterns.md` has been read before committing, and any defect that
      instantiated an existing pattern was added as an occurrence to that entry
      rather than opening a new one.
- [ ] Annotated milestone tag `b1-m2-persistent-structures` created, using the
      message template in `docs/git-conventions.md`, with a real entry under
      `Learned:`.

### G. Oral Defence

- [ ] Work the post-module conceptual challenges (Step 4 of the routine) as a
      dialogue: attempt each one **before** the discussion, say "I don't know"
      plainly when that is the truth, and let `challenge-log.md` carry the
      complete answer the exchange produced. The box closes when every exercise
      has an entry there.
