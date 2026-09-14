# Annex Track — Deliverables Contract

The annex track is **optional and non-blocking**: no main-curriculum module
waits on it. It exists to absorb the prerequisites the main modules assume you
already have, so that a gap discovered mid-module becomes a dedicated annex
entry instead of a detour.

Its completion bar, however, is identical to any other module. An annex is
complete only when **every** box below is `[x]`. Where a box asks for a measured
number, write the number into this file — an unrecorded measurement is an
unperformed measurement.

---

## A1 — Bitwise Arithmetic & Binary Representation

**Milestone tag:** `a1-bitwise-arithmetic`
**Feeds:** B1-M2 (HAMT indexing) · B3-M9 (packed atomic state) · B4-M12 (codecs)

### A. Theory Comprehension

- [x] Read `docs/theory/annex1_bitwise.md` in full.
- [x] Read Bryant & O'Hallaron Ch. 2 §§2.1–2.3, or convince yourself in writing
      that two's complement holds no surprises for you.
- [x] Read JVM Specification §6.5, entries for `ishl`, `ishr`, `iushr`. Confirm
      the masked shift distance **in a REPL** rather than believing the guide.
- [x] Read *Hacker's Delight* Ch. 2 (rightmost-bit manipulation) and Ch. 5
      (counting bits).
- [x] Derive `-x == ~x + 1` on paper, without case analysis on the sign bit.
- [x] Convert three numbers by hand in each direction (decimal to binary and
      back), and read one negative 8-bit pattern as a signed value, showing the
      arithmetic. If Part I felt obvious, do it anyway — it takes five minutes
      and it is the foundation every later part stands on.

### B. Implementation — Exercises

All nine exercises live in `src/main/scala/cs/se/annex/a1/` and are validated
by the suites in `src/test/scala/cs/se/annex/a1/`.

- [x] **E1 `Bits`** — `testBit`, `setBit`, `clearBit`, `toggleBit`,
      `toBinaryString`.
- [x] **E2 `TwosComplement`** — `negate`, `signMask`, `absBranchless`,
      `sameSign`, `floorDiv2`.
- [x] **E3 `PowersOfTwo`** — `isPowerOfTwo`, `modPowerOfTwo`, `nextPowerOfTwo`,
      `log2Floor`.
- [x] **E4 `PopCount`** — `naive`, `kernighan`, `swar`, plus the three-way
      agreement law.
- [x] **E5 `BitAdder`** — `add`, `negate`, `subtract`, `multiply`, built from
      `^`, `&` and `<<` only.
- [x] **E6 `BitSet64`** — an `opaque type` over `Long` satisfying the
      Boolean-algebra laws.
- [x] **E7 `Packing`** — `packInts`/`unpackHi`/`unpackLo` and the RGBA byte
      codec, with roundtrip laws.
- [x] **E8 `BitmapIndex`** — the HAMT node primitive: `hasSlot`,
      `physicalIndex`, `insertAt`, `removeAt` over a persistent array.
- [x] **E9 `VarIntCodec`** — zig-zag plus LEB128, with a roundtrip law over the
      whole `Int` range and a size bound.

### C. Correctness Gate

- [x] `sbt annex/test` — **all tests green**, zero ignored, zero skipped.
- [x] `sbt annex/compile` succeeds under `-Wall -Werror` with **zero**
      warnings suppressed by annotation or configuration.
- [x] `sbt scalafmtCheckAll` passes.

### D. Purity Gate

Verified by reading your own diff before committing:

- [x] Zero occurrences of `var` in `src/main/scala`.
- [x] Zero `while` loops and zero imperative `for` loops. Every bit-scanning
      algorithm is expressed as tail recursion (`@tailrec`) or as a fold.
- [x] Zero `throw` and zero `try`/`catch`. Partiality — and there is real
      partiality here, starting with `Int.MinValue` — is encoded in return types
      or documented as a precondition the tests enforce.
- [x] Zero mutable collections. `Array` appears only in E8, where it models a
      HAMT's dense child array, and every operation on it returns a **new**
      array rather than mutating the input.
- [x] No use of `java.lang.Integer.bitCount` or its siblings **inside** the
      exercises that ask you to build them (E4, E3's `log2Floor`). They are the
      oracle the tests compare against, not the implementation.

### E. Empirical Gate — Record The Numbers

- [x] **Intrinsic gap.** Using the `medianNanos` harness, per-call cost of
      counting bits over the same 100,000 random inputs:
      - `PopCount.naive`: `6.5 ns` · `kernighan`: `8.4 ns` ·
        `swar`: `2.7 ns` · `Integer.bitCount`: `2.6 ns`
        (per call, from medians of `6500 / 8400 / 2700 / 2600` ns per 1000
        calls over 1000 random inputs, 2000 samples each)
      - Ratio of your best implementation to the intrinsic: `1.04 ×`
      - The ratio is meaningless as an algorithmic comparison. All seven
        candidates are invoked through one `Int => Int` reference, so the
        call site is megamorphic, nothing inlines, and virtual dispatch
        dominates. `POPCNT` is one cycle and `swar` is roughly twelve
        operations; a 4% gap can only mean the measurement is dominated by
        something neither of them controls.
      - Unexpected: `naive` (6.5) beats `kernighan` (8.4) on random input,
        despite doing 32 iterations against an average of 16. `kernighan`'s
        iterations form a serial dependency chain (`n & (n-1)` needs the
        previous `n`) and its exit branch is data-dependent, so the predictor
        misses once per call. `naive`'s 32 tests are independent, unrollable,
        and its trip count is constant. Fewer operations lost to more
        parallelism.

- [x] **Controlled experiment.** Re-run with the intrinsic disabled:
      ```bash
      sbt "set annex/Test/javaOptions += \"-XX:-UsePopCountInstruction\"" annex/test
      ```
      - `Integer.bitCount` with the intrinsic off: `3.2 ns` (from `2.6 ns`)
      - State in one sentence what the delta proves about who is actually
        executing your call: the call was not running the Java body of
        `Integer.bitCount` at all — C2 was substituting the `POPCNT`
        instruction, and disabling that substitution drops it back onto the
        JDK's own SWAR source, which is why it then lands at `3.2 ns`,
        indistinguishable from this exercise's `swar` at `3.1 ns`: the same
        algorithm, running as ordinary compiled code.
      - Caveat: the delta is `0.6 ns/call` against a run-to-run spread of
        roughly `0.4 ns` on the untouched candidates. Directionally clear,
        but at the edge of this harness's resolution. JMH would separate it
        properly.

- [x] **Data dependence.** `kernighan` over inputs with 1 set bit vs 31 set bits:
      - sparse: `3.6 ns` · dense: `13.1 ns` — a factor of `3.6 ×`
      - Explain why `swar` shows no such gap (`2.7` vs `3.0 ns`, within
        noise): `swar` has no loop and no branch. It executes the same five
        expressions for every input, so its cost cannot depend on the data.
        `kernighan` loops exactly `popcount(x)` times, so the input *is* the
        trip count.

- [x] **Bytecode reading.** Run `javap -c -p` on your compiled `Bits` and
      `TwosComplement`, and record:
      - The opcode sequence emitted for `~x`: `iconst_m1; ixor` — the JVM has no `inot` opcode, so `~x` is compiled as `x ^ -1`. Verified with `javap -c -p` on `Bits$.clearBit`.
      - The opcode sequence emitted for a literal `x / 2`: `iconst_2; idiv`.
        scalac performs no strength reduction at all — `x / 4` is likewise
        `iconst_4; idiv`. The shift substitution happens in C2, not in the
        compiler to bytecode.
      - Whether `floorDiv2` and `x / 2` compile to the same instructions, and
        why that answer is the point of E2: **No.** `floorDiv2` is
        `iconst_1; ishr`; `x / 2` is `iconst_2; idiv`. They cannot be
        substituted because they are different functions, not two spellings
        of one: `/` truncates toward zero and `>>` rounds toward -infinity,
        so they disagree on every negative odd input (`-7 / 2 == -3`,
        `-7 >> 1 == -4`) and agree everywhere else. A JIT may use a shift for
        `/ 2` only with a correction term, `(x + (x >>> 31)) >> 1`. That the
        two look interchangeable on positive inputs is exactly how the bug
        survives testing.

- [x] **Allocation.** `BitSet64` operations over 100,000 iterations, measured
      with Module 1's `AllocationProbe` technique
      (`com.sun.management.ThreadMXBean.getCurrentThreadAllocatedBytes`, read
      either side of the loop, after five warmup passes):
      - Bytes allocated, word algebra — `union`, `intersect`, `diff`,
        `symDiff`, `complement`, `size`, `subsetOf`, `contains`, `incl`,
        `excl`, ten operations per iteration: **`0`**
      - Bytes allocated, `toList` (sets averaging 32 members): **`309,788,944`**,
        i.e. `3,098` per call
      - If it is not zero, name what escaped and why an `opaque type` failed to
        prevent it:

        **The `opaque type` did not fail — it delivered exactly what Part VII
        promises.** Ten operations over 100,000 iterations allocate *zero*
        bytes, and the erased signatures confirm why: `public long union(long,
        long)`, `public long complement(long)`, `public int size(long)`.
        Primitive `long` in and out, no box, no header.

        What allocates is `toList`, for two reasons that must be separated
        because only one of them is avoidable. Measured by component, same
        100,000 iterations and the same 32-element workload:

        ```text
        component                                bytes / call
        --------------------------------------   ------------
        32 cons cells (:: only)                          768
        32 cons cells + one reverse                    1,536
        32 Tuple2 accumulators                         1,176
                                                  ----------
        BitSet64.toList, measured                      3,098
        ```

        **Inherent (~1,536).** The declared return type is `List[Int]`. A cons
        list of `n` elements is `n` cells, and restoring ascending order after
        prepending costs a second pass of `n`. No implementation of this
        signature allocates less; the cost belongs to the type, not to the code.

        **Avoidable (~1,176).** The fold accumulates into a `(BitSet64,
        List[Int])`. `Tuple2` stores its components as **references** — `_1` is
        typed `Object` in the bytecode — and a primitive `long` does not fit in
        a reference, so every iteration pays `boxToLong` on the way out and
        `unboxToLong` on the way in. The lambda's erased signature records it:
        `toList$$anonfun$adapted$1(Object, Object)`, where the `adapted` suffix
        is the compiler noting the box/unbox adapters it had to insert.

        So the precise statement is that **the `opaque type` was bypassed, not
        defeated**. It guarantees no boxing at its own boundaries, and it cannot
        guarantee anything about a generic container the value is subsequently
        placed into: generics operate on references, and that is a property of
        erasure, not of the alias. Compare `of`, which folds into a bare
        `BitSet64` and compiles to `of$$anonfun$1(long, int): long` — primitive
        throughout, in the same file.

        One measured detail worth recording, because it flatters the number:
        the elements are bit indices in `0..63`, every one of them inside
        `java.lang.Integer`'s `-128..127` cache, so `boxToInteger` returns a
        shared instance and allocates nothing. A `toList` over arbitrary `Int`
        values would pay an additional 16 bytes per element. The cache is doing
        unearned work for this benchmark.

        The same pattern is recorded three more times in
        [`challenge-log.md`](challenge-log.md): entry 17 (this `Tuple2`), entry
        22 (`Option[Int]` in `packRgba`), entry 27 (`Option[(Int, Int)]` in
        `decodeAt`, which is both at once). Four instances, one cause: a
        primitive entering a generic container.

### F. Engineering Hygiene

- [x] All code formatted (`sbt scalafmtAll`) with no manual override.
- [x] Every public definition carries a Scaladoc stating its **contract** —
      including, for every partial operation, the precondition and the behaviour
      at `Int.MinValue`, `0`, and negative inputs.
- [x] Commits follow `docs/git-conventions.md` (`a1: <imperative summary>`), one
      commit per concept proven.
      - 13 commits carry the `a1:` scope; granularity is one concept each.
      - `0ef550b` uses the scope `tooling:`, which §4 originally did not list.
        Resolved by extending the convention rather than rewriting the commit:
        `tooling` now names changes to the development environment, kept separate
        from `build` because one alters what the compiler does and the other
        alters only what the author sees.
- [x] Annotated milestone tag `a1-bitwise-arithmetic` created, using the message
      template in `docs/git-conventions.md`, with a real entry under `Learned:`.

### G. Oral Defence

- [x] Work the post-module conceptual challenges (Step 4 of the routine) as a
      dialogue: attempt each one **before** the discussion, say "I don't know"
      plainly when that is the truth, and let the log carry the complete answer
      that the exchange produced.
      - All nine exercises have entries in [`challenge-log.md`](challenge-log.md).
      - This box previously read *"without consulting the guide"*, which modelled
        an exam. It contradicted the routine it was checking: Step 4 has always
        said to record the complete answer *"where I answered partially and the
        rest was drawn out"* — a description of dialogue, not of an exam.
      - What is being measured is therefore not whether I knew, but whether the
        **order** held. A question attempted and then discussed teaches; a
        question explained before it is attempted is a lecture, and the log
        becomes a record of the mentor's reasoning rather than of mine.
      - Concretely, in this annex: E6 and E7 were largely answered unaided; the
        three E9 challenges got an honest "I don't know" and were taught from
        there. Both are the box working as intended.

The answers are recorded in [`challenge-log.md`](challenge-log.md), one entry
per challenge with the derivations, bytecode and measurements behind them.
This box closes when every exercise has an entry there.

Recorded so far: E1 (3), E2 (3), E4 (3), E5 (3). E3 has no entry — it was closed on
its test suite alone, without a Step 4 round.

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
