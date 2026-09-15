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
- [x] Work `docs/quiz/b1-m1.html`, filtering by Part as you finish each one.
      Worked after the milestone was tagged, and the box added with it: the
      recall set was written as this module closed, when the guide's Self-Check
      was dropped, and the routine's box for it first appears in Module 2. It is
      recorded here so this module is held to the contract every later module is
      held to.

### B. Implementation — Exercises

All nine live in `src/main/scala/cs/se/block1/module1/`, one spec each under
`src/test/scala/cs/se/block1/module1/`, over the shared `Module1Harness`.

- [x] **E1 `Vec2`** — the extension methods `+`, `*`, `dot`, `norm`, and the
      commutative-monoid laws that pin them.
- [x] **E2 `Shape`** — the `enum` ADT with an exhaustive `area`, and `totalArea`
      as a fold whose identity is the empty list.
- [x] **E3 `AllocationProbe`** — `allocatedBytes` and `measure`: the instrument
      every later exercise depends on, proved monotonic, single-evaluation, and
      cheap enough not to disturb what it measures.
- [x] **E4 `Boxing`** — `sumBoxed` against the allocation-free `sumPrimitive`,
      agreeing on every input and diverging only in what they allocate.
- [x] **E5 `Escape`** — `sumNorms` (non-escaping) against `collectVecs`
      (escaping), and the allocation gap scalar replacement opens between them.
- [x] **E6 `WordStats`** — `wordFrequencies` and `topN`, with the total count
      preserved by the fold and an ordering that is total and deterministic.
- [x] **E7 `Footprint`** — `shallowSize`, `arrayOfIntSize`, `listOfIntSize`: the
      HotSpot layout derived rather than measured, and the tenfold `List[Int]`
      tax it predicts.
- [x] **E8 `Csv`** — `renderCsv` under an allocation ceiling: linear in the
      input, not quadratic.
- [x] **E9 `Bench`** — the `medianNanos` harness, proved to separate a heavy
      body from a light one rather than merely to report a number.

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

The exercise order is deliberate. **E1 comes first**, because it builds the cost
model that predicts what every later exercise measures. Module 1 put its
predictive exercise seventh of nine, and the consequence was that its
measurements arrived as isolated facts instead of as confirmations of a
derivation. Do E1 first, on paper, before writing a line of `MyList`.

### A. Theory Comprehension

- [x] Read `docs/theory/module2_structures.md` in full.
- [x] Work `docs/quiz/b1-m2.html`, filtering by Part as you finish each one.
- [x] Read Okasaki, *Purely Functional Data Structures*, Ch. 2.
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
- [x] **E4 `Folds`** — `foldLeft`, `foldRight`, `appended`, `concat`, and the
      stack-depth difference between the two folds.
- [x] **E5 `Building`** — `byAppend` and `byPrepend`, and the doubling table
      that proves their complexity classes.
- [x] **E6 `MyTree`** — the BST `enum`: `insert`, `contains`, `size`, `depth`.
- [x] **E7 `TreeFold`** — `foldInOrder`, `toMyList`, `treeMap`, and the ordering
      law that ties them together.
- [x] **E8 `SharingProof`** — measure with `AllocationProbe` and confirm, or
      refute, every prediction E1 made.
- [x] **E9 `Balance`** — `fromSorted` against `fromBalanced`, and what the
      depth difference does to the cost of one insert.

### C. Correctness Gate

- [x] `sbt fundamentals/test` — **all tests green**, zero ignored, zero skipped.
      65 tests: 28 from Module 1 and 37 from Module 2.
- [x] `sbt fundamentals/compile` succeeds under `-Wall -Werror`. **Not with zero
      suppressions — with two**, both by annotation, both documented at the
      site. Module 1 has none at all, so this is the first time the box needs an
      answer rather than a sweep, and the answer is different for each:

      - `@unused` on the parameter of `Sharing.prependCells(n: Int)`. This one
        is forced by the exercise's own signature. A prepend costs one cell
        whatever the list's length, so a *correct* implementation cannot read
        `n`, and under `-Werror` an unread parameter is an error. The signature
        exists to be read beside `appendCells(n)`, which does use it — the
        parallel is the lesson. The clause and the exercise cannot both be
        satisfied; recorded as a fourth occurrence of pattern 6 in
        [`error-patterns.md`](error-patterns.md), *a contract no implementation
        of that signature can satisfy*.
      - `@unchecked` on the match in `MyList.head` and `MyList.tail`. Not
        forced: it is a consequence of narrowing the domain with `require`
        instead of returning `Option`. With the precondition above it, the
        `Nil` branch the exhaustivity checker demands is a case that cannot
        arrive — but that argument is exactly the one §D's last box puts to
        §G, so the annotation stands or falls with that decision rather than on
        its own.
- [x] `sbt scalafmtCheckAll` passes.
- [x] Module 1's 28 tests still pass. This module adds to the suite; it does not
      replace it. Verified by running the two packages separately:
      `testOnly cs.se.block1.module1.*` reports 28, `module2.*` reports 37.

### D. Purity Gate

Verified by reading your own diff before committing:

- [x] Zero occurrences of `var` in `src/main/scala/cs/se/block1/module2`. Swept:
      no occurrence at all, in code or in prose.
- [x] Zero `while` loops and zero imperative `for` loops. Swept: every hit for
      `while` is a Scaladoc sentence. One iteration construct does exist —
      `(0 until 20).foreach(_ => body)` in `SharingProof.bytesOf` — and it is
      the warm-up, inside the instrument rather than inside a structure. It
      falls under the same exception Module 1 granted `AllocationProbe` and
      `Bench`: a measuring instrument may do what it measures must not.
- [x] Zero `throw` and zero `try`/`catch`. Swept: no occurrence of any of the
      three.

      Two `require` calls do exist, in `MyList.head` and `MyList.tail`, and
      `require` raises `IllegalArgumentException`. As in Module 1, they are not
      a violation of this box but the subject of the last one.
- [x] Zero mutable collections, and zero use of `scala.collection.immutable.List`
      *inside* your own structure's implementation. `MyList` is built from
      `MyList`, or the exercise proves nothing. Converting to `List` at the
      boundary, in `MyList.toScalaList`, is the one permitted crossing.

      Swept: no `mutable`, no `ListBuffer`, no `ArrayBuffer`. The only
      `scala.List` in the implementation is `toScalaList`'s own return type and
      the `scala.List.empty[A]` seed of its fold — the permitted crossing,
      taken once, at the boundary. Every other hit is prose.
- [x] Every recursive function that walks a whole structure is either
      `@tailrec` or documented as bounded by depth rather than by size. `MyTree`
      recursion is the second kind; `MyList` recursion must be the first.

      Seven `@tailrec` walks in `MyList`: `length`, `map`, `filter`, `reverse`,
      `foldLeft`, `concat` and `toScalaList`. `MyTree`'s recursion is bounded by
      depth and says so at the declaration.

      **One deliberate exception, and it is the point of an exercise rather than
      a gap.** `MyList.foldRight` is not `@tailrec` and cannot be made so
      directly: it must reach the end of the list before it can combine
      anything, so every element's frame waits on the stack. It is bounded by
      *size*, which this box forbids — and `Exercise4FoldsSpec` asserts that it
      overflows at a million elements, reporting `foldRight over 1,000,000
      overflowed = true`. A `foldRight` that survived that input would be one
      that secretly reversed the list, and hiding the cost is worse than paying
      it. Module 3 removes the limit properly.
- [x] `head` on an empty list: the decision is made, documented in the Scaladoc,
      and defended in §G. Module 1 asked the same question of
      `Escape.sumNorms`; the answer here may differ, but it may not be absent.

      The answer is the same as Module 1's and reached the same way: the domain
      is narrowed rather than the return type widened. `Option[A]` was rejected
      because it forces every correct caller to handle a case it has already
      excluded, and invites the `.get` that converts a loud defect into a silent
      one. `require` states the narrowed domain at the boundary; the match is
      `@unchecked` because the `Nil` branch the exhaustivity checker wants has
      been ruled out by the line above it.

      What is *not* settled by that, and is the §G question: `require` throws,
      so the gate two boxes up is satisfied only by reading it as forbidding
      `throw` as control flow rather than as domain enforcement. The same
      reading carries the `@unchecked` recorded in §C.

### E. Empirical Gate — Record The Numbers

Every number below must be produced by *your* structure, not by Scala's. Fill in
every blank, and compare each against what Exercise 1 predicted **before** you
ran it.

Every number was measured in the forked test JVM that `build.sbt` pins to
`-Xmx2g -XX:+UseG1GC`, after warm-up, with compressed oops on — which is what
makes the cell 24 bytes rather than 32, and every byte count here is a multiple
of that constant. The ratios are not: they survive a JVM without compressed oops
unchanged, which is the argument the doubling table makes below.

- [x] **Cell and node size.**
      - `Sharing.CellBytes`: `24` · `Sharing.NodeBytes`: `24`
      - These are `Footprint.shallowSize` calls from Module 1. If they disagree
        with 24, one of the two modules is wrong: `they agree — align(12 + 2*4)`
        `= align(20) = 24 and align(12 + 3*4) = align(24) = 24, so the node's`
        `third reference lands inside padding the cell was already paying for`

- [x] **List operations over `n = 100,000`.** Predicted from E1, then measured
      with `AllocationProbe` in E8. Every row is measured over `MyList`, not
      over `scala.List`: both cons cells are 24 bytes, so three of these four
      rows would agree either way, and `append` is the only one whose cost is
      decided by algorithm rather than by shape
      ([`challenge-log.md`](challenge-log.md), entry 18).

      The `measured` column has the element's box removed, with the raw reading
      beside it. Every operation that *introduces* an element boxes an `Int`
      above the `Integer` cache inside the measured window, and the model counts
      cells rather than elements — `Sharing.reverseCells`: *"Note what is not
      allocated: the elements"*. `reverse` introduces nothing and pays nothing,
      and that asymmetry is what identifies the 16 bytes instead of merely
      tolerating them (entry 20).

      | operation | E1 predicts (bytes) | measured (bytes) | agree? |
      | :--- | ---: | ---: | :---: |
      | `x :: xs` | `24` | `24` · raw `40` | exactly |
      | `xs :+ x` | `4,800,024` | `4,800,024` · raw `4,800,040` | exactly |
      | `xs.reverse` | `2,400,000` | `2,400,000` · no box | exactly |
      | `xs.map(identity)` | `2,400,000` | `6,397,952` | no — see below |

      - The fourth row disagrees with the model by design, in the same way
        `append` does and for a different reason. `mapCells(n) = n` counts the
        cells the result *retains*; there is no `mapAllocatedCells`. The
        measurement decomposes to the byte:
        ```text
        two spines          2 x 100,000 x 24       4,800,000
        one Integer each    (100,000 - 128) x 16   1,597,952
                                                   ---------
                                                   6,397,952   measured
        ```
        The two spines are the `@tailrec` tax `MyList.map` documents: the
        accumulator builds backwards, so a second pass restores the order and
        one spine is garbage before the method returns.

        The 1,597,952 is the experiment `Sharing.mapCells` was written to
        provoke, and the answer is not zero. `identity` allocates nothing, yet
        every element costs a fresh 16-byte `java.lang.Integer` — all but the
        128 the cache shares. `Function1` is specialised for `Int`, so
        `identity` unboxes its argument and the `int` that comes back must be
        boxed again to enter the cell. Compare `filter`, which returns the same
        elements in the same order and pays no box at all: its `p(h)` returns a
        primitive and the element is re-prepended as the *same reference*. The
        spine is the model's business; the box is `f`'s signature's.

        This row was closed late. It had no `measureMap` until now, and the
        figure standing in `MyList.map`'s Scaladoc in the meantime was
        `6,522,656` — wrong by 124,704 bytes, unanchored by any test, and
        therefore unable to be contradicted by a green suite. Recorded as
        pattern 12 of [`error-patterns.md`](error-patterns.md).
      - Where prediction and measurement differ, the difference is itself a
        result. Account for it: `E1 makes two predictions for append and they`
        `differ by a factor of two. appendCells(n) = n counts the spine the`
        `result retains; appendAllocatedCells(n) = 2n + 1 counts what the`
        `operation spends. AllocationProbe counts spending, so the measurement`
        `must be compared against the second, and it lands on it to the byte:`
        `200,001 cells at 24. The two models coincide only for an operation`
        `that produces no garbage, which is reverse - the one row that hits the`
        `model with no slack at all. The excess is the intermediate spine a`
        `@tailrec concat builds and discards, because it must walk xs forwards`
        `and emit backwards; the alternatives are a non-tail recursion, which`
        `overflows at this n, or the mutable builder that section D forbids.`
        `Recorded as entry 19.`

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

      - **On reproducing these.** `byPrepend` is exact: the four figures come
        back identical on every run, alone or with the whole suite. `byAppend`
        is exact only when its suite runs alone — verified by running
        `testOnly *Exercise5BuildingSpec` twice and getting the table above to
        the byte both times. Run inside `fundamentals/test`, where sbt forks one
        JVM for every suite, two of the four figures move:
        ```text
        n         alone (recorded)   whole suite        delta   vs. model
        -------   ----------------   ---------------   ------   ---------
          2,000         96,059,944        96,111,696   51,752   +0.05 %
         16,000      6,144,508,128     6,144,507,944      184   exact
        ```
        The second row is the one to read twice: with the suite it lands on
        `24n^2 + 32n - 4,056` exactly, and alone it is 184 bytes above. The
        drift is not in the algorithm — the ratios are `3.997 / 3.999 / 4.000`
        against `3.999 / 3.999 / 4.000`, and the complexity class is untouched.
        It is the shared JVM: the probe counts the calling thread, and what that
        thread did before entering the measured window is not fixed by the code
        being measured. An absolute byte count at this scale measures a *run*;
        the ratio measures the *algorithm*. That is the same argument the two
        `x prev` columns make above, arriving from the other direction.

- [x] **Tree sharing.** On a balanced tree of `2^20 − 1` = 1,048,575 nodes:
      - whole tree: `25,165,800 bytes` · one `insert`: `504 bytes` (raw `520`)
      - nodes copied: `21` · depth: `20` · sharing ratio: `49,932.14 ×`
      - Measurement and model are the same number, not merely close:
        ```text
        whole tree    1,048,575 x 24                =  25,165,800
        one insert    (balancedDepth(n) + 1) x 24
                      (20 + 1) x 24                 =         504
        ratio         25,165,800 / 504              =  49,932.14
        ```
        Leaving the element's box inside the measured window gives `48,396 ×`
        instead — wrong by 3%, and no assertion in the suite was tight enough to
        report it. Recorded as entry 20 and as pattern 10 of
        [`error-patterns.md`](error-patterns.md).
      - The nodes copied should exceed the depth by exactly one. Say why:
        `the insert rebuilds every node on the path from the root to the`
        `insertion point, which is 20 nodes and is the depth, and then builds`
        `the new leaf. The leaf is not on that path: the path ends at the empty`
        `subtree where the value goes, so it is walked to and not through. The`
        `count is therefore depth + 1, which is Sharing.treeInsertNodes as`
        `written, and 504 bytes is 21 nodes rather than 20.`

- [x] **Degeneration.** Insert 4,096 values in sorted order and in shuffled
      order, and record for each:
      - sorted: depth `4,096`, one insert costs `98,328 bytes` (raw `98,344`)
      - balanced: depth `13`, one insert costs `336 bytes` (raw `352`)
      - ratio: `292.643 ×`, against a model of `4,097 / 14` = `292.643 ×`,
        deviation `0.0000 %`
      - **The second row is `fromBalanced`, not a shuffle**, and the substitution
        is deliberate rather than an omission. A shuffled build has a depth that
        is logarithmic in expectation but varies from run to run, so it measures
        a distribution; `fromBalanced` measures the bound that distribution is
        approaching, and it is reproducible. The degenerate side — the side this
        box is about — is identical either way, because a sorted insertion order
        has no distribution at all: it produces the right spine every time.
      - Both figures are `depth + 1` nodes, and that `+ 1` is the finding:
        ```text
                            numerator   denominator    factor   vs. model
        -----------------   ---------   -----------   -------   ---------
        depths only              4,096            13   315.077    +7.67 %
        numerator +1 only        4,097            13   315.154    +7.70 %
        denominator +1 only      4,096            14   292.571    -0.02 %
        both, correct            4,097            14   292.643     0.00 %

        measured   98,328 / 336  =  292.6429
        model       4,097 /  14  =  292.6429      deviation 0.0000 %
        ```
        An additive constant is invisible against 4,096 and worth 7.1% against
        13, so the ratio is decided entirely by the *balanced* side — the side
        whose smallness is the property under test. Entry 21.
      - The measurement also depends on probing above the maximum rather than
        below the minimum. `insert(-1)` into a right spine stops at the root's
        empty left child, so it measures that tree's *cheapest* insert: 48 bytes
        against the balanced tree's 312, a factor of `0.15 ×` in a test whose
        subject is a factor of 292. Recorded as pattern 11 of
        [`error-patterns.md`](error-patterns.md).
      - Name the production inputs that arrive pre-sorted:
        `auto-increment primary keys, read back in insertion order by any query`
        `that declares ORDER BY id for determinism; event timestamps, which are`
        `non-decreasing because they are stamped as the events occur; and the`
        `merge phase of an external sort or an LSM compaction, whose input`
        `contract is that every run is already sorted.`

        None of the three is an edge case and none is an attack. In each one the
        ordering is a *consequence* of how the data was produced, and the code
        that produced it is correct: the `ORDER BY` is there to make a result
        reproducible, the clock moves forwards on its own, and a merge that
        accepted unsorted runs would not be a merge. The degenerate case is the
        one that arrives by default.

        The three differ in how reliably they degenerate, which is worth keeping
        apart:

        ```text
        source                  ordering        what it costs at n = 4,096
        ---------------------   -------------   --------------------------
        auto-increment key      total           depth 4,096 - the full spine
        event timestamp         near-total      a spine with short branches
        merged sorted runs      total per run   one spine per run
        ```

        Near-sorted is not a reprieve. A few out-of-order arrivals hang short
        branches off a spine that is otherwise the full height, so the depth
        stays within a small factor of `n`. The structure needs the order to be
        *shuffled*, and an input that is merely *not perfectly sorted* is not
        shuffled.

        And the cost is not only the `292 x` measured above. At depth 4,096 the
        `MyTree` recursion that §D documents as *bounded by depth rather than by
        size* stops being an argument and becomes a `StackOverflowError`, on the
        input a system is most likely to receive.

### F. Engineering Hygiene

- [x] All code formatted (`sbt scalafmtAll`) with no manual override.
      `scalafmtCheckAll` passes over all 19 Block 1 sources.
- [x] Every public definition carries a Scaladoc stating its **contract**, not a
      restatement of its name. Constants carry the rule from pattern 4 of
      `error-patterns.md`: name the assertion that pins the value, or state that
      none does.

      Audited mechanically, as Module 1 was: walk every `def`, `val` and `case`
      in `module2` and check the preceding non-blank line for a closing `*/`.
      Every public definition passes. What the audit reports and why each is not
      a gap:

      - Eleven method-**local** bindings — the `loop` helpers, `SharingProof`'s
        `ls` and `t`, `fromRange`'s nested `midPoints` and its `mid`. Not public
        definitions, and Module 1's audit excluded the same class.
      - The four `enum` cases: `MyList.Nil` and `Cons`, `MyTree.Leaf` and
        `Branch`. Their contract is stated by the enum's own Scaladoc
        immediately above them, and stated at more length than a per-case line
        could — `MyList`'s covers closure, recursion, the `Nil` singleton and
        what `+A` buys; `MyTree`'s states the BST invariant and, pointedly, what
        the invariant does *not* buy. Same reasoning Module 1 applied to the
        `Vec2` and `Shape` companions.
- [x] `error-patterns.md` has been read before committing, and any defect that
      instantiated an existing pattern was added as an occurrence to that entry
      rather than opening a new one.

      Two defects surfaced while closing these gates, and they went to different
      places for the reason the rule gives:

      - The `@unused` on `Sharing.prependCells` became a **third occurrence of
        pattern 6**, not a new entry. It is the same shape as the other two — a
        contract no implementation of that signature can satisfy — with the
        contract living in this checklist rather than in a Scaladoc. That the
        carrier differs is the interesting part, and it belongs inside the
        pattern, not beside it.
      - The unanchored measurements opened **pattern 12**, because none of the
        eleven covered it: 10 and 11 are defects of measurement, and this is a
        defect of *record-keeping about* measurement. Its own text argues the
        case.
- [x] Annotated milestone tag `b1-m2-persistent-structures` created, using the
      message template in `docs/git-conventions.md`, with a real entry under
      `Learned:`.

      Created after §G and not beside it, which is what the tag contract
      requires: *"the point at which every acceptance criterion of a module's
      checklist is `[x]` **and** the module's MUnit suite is green"*. Tagging on
      a green suite alone would have made the tag mean exactly what the suite
      already means.

      `Learned:` carries **retained against allocated** — the distinction that
      produced half this module's defects, including both that the suite could
      not see.

### G. Oral Defence

- [x] Work the post-module conceptual challenges (Step 4 of the routine) as a
      dialogue: attempt each one **before** the discussion, say "I don't know"
      plainly when that is the truth, and let `challenge-log.md` carry the
      complete answer the exchange produced. The box closes when every exercise
      has an entry there.

      **Coverage is complete.** Thirteen entries over three rounds, 16 to 28,
      and every one of the nine exercises has at least one:

      | exercise | entries |
      | :--- | :--- |
      | E1 `Sharing` | 27 |
      | E2 `MyList` | 24 |
      | E3 `Combinators` | 25 |
      | E4 `Folds` | 22 |
      | E5 `Building` | 26 |
      | E6 `MyTree` | 16, 17, 23 |
      | E7 `TreeFold` | 28 |
      | E8 `SharingProof` | 18, 19, 20 |
      | E9 `Balance` | 21 |

      The order held: every question was attempted before it was discussed.
      Five of the seven in the later rounds were answered unaided — the
      `concat` mechanism, the variance diagnosis and its repair, the `filter`
      idiom, the doubling constant, and the whole of `sharingRatio`'s blind
      spot, that one with the algebra. One was answered half right, and the
      half that was wrong (`contains(-1)` on a right spine) was the more useful
      half, because it repeated a documented trap. One was **"I don't know"**,
      said plainly and at the time, and the treeMap counterexample was then
      built from three steps with the last one left to me — which is the
      exchange the box exists to produce.

      **Two defects surfaced from the challenges rather than from the suite**,
      and both are now fixed: `MyTree.contains` never consulted the `Ordering`
      it asks for and was `O(n)` behind an `O(depth)` contract, and
      `MyList.concat`'s Scaladoc said *allocates* where it meant *retains*.
      Neither could have been caught by running anything — the suite was green
      through both.

      Nothing is carried. The one item that was — no assertion separating the
      old `contains` from the new — is now two tests in `Exercise6MyTreeSpec`,
      validated by reverting the implementation and confirming they fail while
      the other five stay green. The suite is 67 tests.
---

## Module 3 — Stack Optimization & Control Flow Elimination

**Milestone tag:** `b1-m3-stack-optimization`

The exercise order is deliberate, for the same reason it was in Module 2 and for
a sharper one here. **E1 comes first**, because it is the only instrument in the
module: the stack cannot be sampled the way `AllocationProbe` samples the heap,
so every depth claim in every later exercise is produced by E1 or by nothing.
Build it before you need it.

### A. Theory Comprehension

- [ ] Read `docs/theory/module3_stack.md` in full.
- [ ] Work `docs/quiz/b1-m3.html`, filtering by Part as you finish each one.
      33 questions over all 27 sections — coverage checked mechanically, by
      listing the guide's sections against the sections the questions cite.
      7 easy, 16 medium, 10 hard.
- [ ] Read JVM Specification §2.5.2 and §2.6, and §2.6.5 on why the JVM has no
      tail-call instruction.
- [x] Before writing any code, classify these six expressions by hand, and say
      for each whether the call to `f` is in tail position and why:
      `f(x)` · `1 + f(x)` · `if p then f(x) else 0` · `f(x) match { ... }` ·
      `try f(x) catch { ... }` · `f(g(x))`. Guide §5.

      **Worked as a dialogue, and worked late.** E1 through E3 were already
      implemented when this box was answered, so "before writing any code" was
      not honoured. Recorded rather than quietly ticked: the box measures
      whether the classification preceded the implementation, and here it did
      not.

      One rule decides all six — **follow the value.** If the value the call
      produces is handed straight back to the caller, untouched, the call is in
      tail position; if anything consumes it first, it is not. The property is
      read off the page, with nothing known about `f`.

      ```text
      expression                   call   tail?   what consumes the value
      --------------------------   ----   -----   ----------------------------
      f(x)                         f      yes*    nothing  (*if it is the body)
      1 + f(x)                     f      no      the +, pending in the frame
      if p then f(x) else 0        f      yes     nothing - the if already chose
      f(x) match { ... }           f      no      the pattern test (scrutinee)
      try f(x) catch { ... }       f      no      nothing - see below
      f(g(x))                      g      no      the invoke of f
                                   f      yes*    nothing  (*if it is the body)
      ```

      Four of the six fell out of the rule directly. Three points needed more
      than the rule, and two of them were corrections made in the exchange:

      - **`f(x)` is conditional, not automatic.** A call is in tail position
        only if it occupies the method's *result* position. `f(x)` written as a
        statement, with `g(x)` after it, is a call whose value is discarded and
        whose frame still has work ahead. Tail position is a property of a
        **call**, never of an expression — which `f(g(x))` makes unavoidable,
        since it holds two calls and earns two opposite verdicts.
      - **`if` and `match` are the same construct with the order reversed**, and
        the position of the call decides everything:
        ```text
        x match { case _ => f(x) }      tail   - the call is the RESULT
        f(x) match { case _ => ... }    not    - the call is the SCRUTINEE
        ```
        The `if` costs nothing because by the time `f(x)` starts, the
        condition has already been evaluated and the branch already taken:
        the selection happens *before* the call, not after it. The `match`
        scrutinee is the mirror image — the value returns and must still be
        tested against the patterns, and then the chosen branch must still run.
      - **`try` is the one the rule does not catch.** Nothing consumes the
        value: on a normal return it passes through the `try` untouched, which
        is exactly why the expression reads like tail position. What keeps the
        frame alive is not pending work but the **exception table** it carries —
        the map from bytecode ranges to handlers that makes the `catch`
        reachable while `f(x)` runs. Discarding the frame at the moment of the
        call would discard the handler with it, and a throw from any depth below
        would have nothing to land on. The frame survives for the *abnormal*
        return, and the exception never has to occur: the possibility alone
        costs the frame.

        This is why §D's last box confines `StackOverflowError` to
        `StackProbe`. The `try` that protects the instrument is the same
        construct the other eight exercises spend the module eliminating.

      **Tail position is necessary and not sufficient.** All six verdicts are
      syntactic, but *elimination* is not: the call also has to target the
      enclosing method, and that method must not be overridable, or the `goto`
      has no statically known target. Guide §26, and the reason §C requires
      every `@tailrec` in this module to sit on a `private`, `final` or
      method-local definition.
- [x] Predict, before running E2, the relationship between the largest `n` that
      `sumNaive` survives and the largest that `sumAcc` survives. State it as a
      *class*, not a number, and say why a number would be the wrong form of
      answer. Guide §23.

      **Worked as a dialogue, and worked late**, for the same reason as the box
      above: E2 was already implemented and already measured. Recorded rather
      than quietly ticked.

      **The class.** `sumNaive` has a stack ceiling; `sumAcc` has none. The two
      ceilings are not two sizes of the same thing — they are imposed by
      different resources entirely:

      ```text
      sumNaive   ceiling imposed by the STACK   one frame per level, ~14k
      sumAcc     ceiling imposed by the TYPE    one frame in total, 2^31 - 1
      ```

      `sumAcc`'s limit is `Int.MaxValue` because that is the largest value its
      *parameter* holds. Widen the parameter to `Long` and the limit rises with
      it, without `-Xss` being touched. Nothing about the stack participates.

      **Why a number is the wrong form of answer**, in two layers, the first of
      which is the one that decides the form:

      1. **It is a category error, not an imprecision.** To write "`sumAcc`
         survives 150,000× more" you must divide one ceiling by the other, and
         division presupposes two quantities of the same kind. The ratio
         therefore asserts, silently, that `sumAcc` *has* a stack ceiling —
         merely a larger one. It converts a categorical difference into a
         quantitative one and destroys the very fact it was meant to report.
         What one believes after accepting the number is that `sumAcc` fails
         too, eventually, at some large enough `n`, for the same reason. It
         never does. There is no `n` that overflows it, because no stack is
         growing. The question "how many times larger?" is not inaccurate; it is
         malformed.
      2. **And the surviving number is not a property of the code either.** Only
         `sumNaive` has a ceiling to quote, and it belongs to the machine: §23
         measures 13,123 at 256 KiB against 520,945 at 8 MiB — 40× from one flag
         — and within a single process the boundary rises by 2.51× as C2
         replaces interpreted frames with compiled ones. A test asserting that
         number is asserting `-Xss` and the JIT's progress, not the
         transformation.

      Hence what E2's spec asserts: that the tail-recursive spelling survives an
      input the naive one cannot, and never an absolute depth. Module 2's
      argument about ratios against absolutes, moved from the heap to the stack.

### B. Implementation — Exercises

All nine live in `src/main/scala/cs/se/block1/module3/`, one spec each under
`src/test/scala/cs/se/block1/module3/`, over a shared `Module3Harness`.

- [ ] **E1 `StackProbe`** *(do this first)* — `survives`, `maxDepth` by binary
      search, and `onStack` to run a body on a thread of a chosen stack size.
      The instrument every later exercise reports through.
- [ ] **E2 `TailShapes`** — `sumNaive`, `sumAcc` and `sumLoop`: three spellings
      of one function, agreeing on every input all three survive, and separated
      by a ceiling only one of them has.
- [ ] **E3 `Arithmetic`** — `gcd`, `power`, `digits`, `collatzLength`: tail
      recursion where the accumulator is not merely a running total.
- [ ] **E4 `Loops`** — `factorial`, `fibonacci`, `reverseDigits`: `while` loops
      transcribed by the rule in §9, the `var`s becoming parameters.
- [ ] **E5 `EarlyExit`** — `indexOf`, `forall`, `exists`, `takeWhile` over
      `MyList`: the branch that returns instead of recursing.
- [ ] **E6 `SafeFold`** — `foldRightSafe` over `MyList`, agreeing with
      `foldRight` wherever both survive, surviving where it does not, and paying
      exactly one spine for the privilege.
- [ ] **E7 `LazyFold`** — `foldRightLazy` with a by-name second argument, and
      `existsLazy` proving it stops at the first hit rather than at the end.
- [ ] **E8 `Rotations`** — `rotateLeft`, `rotateRight`, and the law that makes a
      rotation legal: the in-order walk is unchanged.
- [ ] **E9 `Avl`** — `balanceFactor`, `rebalance`, `insertBalanced`, and the
      depth bound that survives sorted input.

### C. Correctness Gate

- [ ] `sbt fundamentals/test` — **all tests green**, zero ignored, zero skipped.
- [ ] `sbt fundamentals/compile` succeeds under `-Wall -Werror`. If a warning is
      suppressed, name it here and say whether the exercise's own signature
      forced it, as Module 2's §C had to for `@unused`.
- [ ] `sbt scalafmtCheckAll` passes.
- [ ] Modules 1 and 2 still pass: 28 + 39 tests. This module adds to the suite.
- [ ] Every `@tailrec` in `module3` is on a `private`, `final`, or method-local
      definition. An annotation that compiles is not the same as a definition
      nobody can override out from under it. Guide §26.

### D. Purity Gate

- [ ] Zero occurrences of `var` in `src/main/scala/cs/se/block1/module3`, with
      **one documented exception**: `StackProbe`'s search is a measuring
      instrument and may use local mutability, exactly as `AllocationProbe` and
      `Bench` do in Module 1. Name the lines here: `______________________`
- [ ] Zero `while` loops in `src/main/scala/cs/se/block1/module3`, including in
      `StackProbe`. The instrument gets mutability, not control flow — this
      module is about eliminating `while`, and an instrument that uses one to
      measure the elimination is an embarrassment rather than an exception.
- [ ] Every recursive function that walks a whole structure is `@tailrec`, or
      the exercise is about why it cannot be. E6, E7 and E8 are the second kind
      and each must say so in its Scaladoc.
- [ ] `StackOverflowError` is caught in exactly one place — `StackProbe` — and
      its Scaladoc says why catching an `Error` is defensible there and nowhere
      else.

### E. Empirical Gate — Record The Numbers

Produced by *your* `StackProbe`, on this machine, with the JVM configuration
named. An unrecorded measurement is an unperformed measurement.

- [ ] **The ceiling.** Largest `n` surviving on the default test thread:
      - `sumNaive`: `______` · `sumAcc` at `n = 10,000,000`: `______`
      - `MyList.foldRight`: `______` · `MyList.foldLeft` at 200,000: `______`
      - The guide measured 14,335 and 14,990 for the first and third. If yours
        differ by more than a few percent, say what differs:
        `______________________`

- [ ] **The frame.** Run `maxDepth` on threads of 256 KiB, 512 KiB, 1 MiB and
      8 MiB, fit `stack = a × depth + b`, and record both coefficients:

      | stack | max depth | bytes/frame |
      | ---: | ---: | ---: |
      | 256 KiB | `______` | `______` |
      | 512 KiB | `______` | `______` |
      | 1 MiB | `______` | `______` |
      | 8 MiB | `______` | `______` |

      - `a` = `______ bytes/frame` · `b` = `______ bytes` of stack already used
      - Why `b` is not zero: `______________________`

- [ ] **The trade.** `foldRightSafe` against `foldLeft` over `n = 100,000`:
      - `foldLeft`: `______ bytes` · `foldRightSafe`: `______ bytes`
      - delta: `______ bytes` = `______ cells`. Name what those cells are:
        `______________________`
      - The guide measured a delta of 2,400,400. This is stack converted into
        heap; state the exchange rate you just paid, in one line:
        `______________________`

- [ ] **Laziness.** `existsLazy` finding a match at element 3 of 1,000,000:
      - elements visited: `______` · bytes allocated: `______`
      - and with no match present: `______` · `______`
      - Which of the two ceilings this removes, and which it does not:
        `______________________`

- [ ] **Balance.** Insert `0, 1, ..., 4095` in ascending order:
      - plain `MyTree.insert`: depth `______`
      - `Avl.insertBalanced`: depth `______` · the AVL bound
        `1.44 log2(n + 2)` = `______`
      - rotations performed over the whole build: `______`
      - bytes for one insert into the balanced result: `______`
      - Module 2 measured 98,328 bytes for one insert into the degenerate tree.
        The ratio you have just bought: `______ ×`

### F. Engineering Hygiene

- [ ] All code formatted (`sbt scalafmtAll`) with no manual override.
- [ ] Every public definition carries a Scaladoc stating its **contract**.
- [ ] Commits follow `docs/git-conventions.md` (`b1-m3: <imperative summary>`),
      one commit per concept proven.
- [ ] `error-patterns.md` read before committing; any defect instantiating an
      existing pattern added as an occurrence rather than opening a new entry.
      Pattern 11 — *a fixture that cannot exhibit the property under test* — has
      two occurrences already, and this module offers a third at every turn: a
      depth assertion on a tree that was never degenerate measures nothing, and
      a stack assertion on an input below the ceiling measures nothing either.

      Pattern 13 was opened by this module before its first exercise was
      implemented: *a measurement taken while its subject is still changing*.
      Read it before writing any assertion on a depth. A compiled frame is
      2.51× smaller than an interpreted one, so a cold probe returns a number
      that is already false, and the failing test blames the implementation.
- [ ] Annotated milestone tag `b1-m3-stack-optimization` created, using the
      message template, with a real entry under `Learned:`. Created **after**
      §G, as Module 2's was.

### G. Oral Defence

- [ ] Work the post-module conceptual challenges (Step 4 of the routine) as a
      dialogue: attempt each one **before** the discussion, say "I don't know"
      plainly when that is the truth, and let `challenge-log.md` carry the
      complete answer the exchange produced. The box closes when every exercise
      has an entry there.
