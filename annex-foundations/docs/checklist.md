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

- [ ] Read `docs/theory/annex1_bitwise.md` in full.
- [ ] Read Bryant & O'Hallaron Ch. 2 §§2.1–2.3, or convince yourself in writing
      that two's complement holds no surprises for you.
- [ ] Read JVM Specification §6.5, entries for `ishl`, `ishr`, `iushr`. Confirm
      the masked shift distance **in a REPL** rather than believing the guide.
- [ ] Read *Hacker's Delight* Ch. 2 (rightmost-bit manipulation) and Ch. 5
      (counting bits).
- [ ] Answer all six questions in the Self-Check section of the guide **in
      writing**, without re-reading it. Append the answers to this file under
      "Self-Check Answers".
- [ ] Derive `-x == ~x + 1` on paper, without case analysis on the sign bit.
- [ ] Convert three numbers by hand in each direction (decimal to binary and
      back), and read one negative 8-bit pattern as a signed value, showing the
      arithmetic. If Part I felt obvious, do it anyway — it takes five minutes
      and it is the foundation every later part stands on.

### B. Implementation — Exercises

All nine exercises live in `src/main/scala/cs/se/annex/a1/` and are validated
by the suites in `src/test/scala/cs/se/annex/a1/`.

- [ ] **E1 `Bits`** *(Easy)* — `testBit`, `setBit`, `clearBit`, `toggleBit`,
      `toBinaryString`.
- [ ] **E2 `TwosComplement`** *(Easy)* — `negate`, `signMask`, `absBranchless`,
      `sameSign`, `floorDiv2`.
- [ ] **E3 `PowersOfTwo`** *(Easy)* — `isPowerOfTwo`, `modPowerOfTwo`,
      `nextPowerOfTwo`, `log2Floor`.
- [ ] **E4 `PopCount`** *(Medium)* — `naive`, `kernighan`, `swar`, plus the
      three-way agreement law.
- [ ] **E5 `BitAdder`** *(Medium)* — `add`, `negate`, `subtract`, `multiply`,
      built from `^`, `&` and `<<` only.
- [ ] **E6 `BitSet64`** *(Medium)* — an `opaque type` over `Long` satisfying the
      Boolean-algebra laws.
- [ ] **E7 `Packing`** *(Medium)* — `packInts`/`unpackHi`/`unpackLo` and the
      RGBA byte codec, with roundtrip laws.
- [ ] **E8 `BitmapIndex`** *(Hard)* — the HAMT node primitive: `hasSlot`,
      `physicalIndex`, `insertAt`, `removeAt` over a persistent array.
- [ ] **E9 `VarIntCodec`** *(Hard)* — zig-zag plus LEB128, with a roundtrip law
      over the whole `Int` range and a size bound.

### C. Correctness Gate

- [ ] `sbt annex/test` — **all tests green**, zero ignored, zero skipped.
- [ ] `sbt annex/compile` succeeds under `-Wall -Werror` with **zero**
      warnings suppressed by annotation or configuration.
- [ ] `sbt scalafmtCheckAll` passes.

### D. Purity Gate

Verified by reading your own diff before committing:

- [ ] Zero occurrences of `var` in `src/main/scala`.
- [ ] Zero `while` loops and zero imperative `for` loops. Every bit-scanning
      algorithm is expressed as tail recursion (`@tailrec`) or as a fold.
- [ ] Zero `throw` and zero `try`/`catch`. Partiality — and there is real
      partiality here, starting with `Int.MinValue` — is encoded in return types
      or documented as a precondition the tests enforce.
- [ ] Zero mutable collections. `Array` appears only in E8, where it models a
      HAMT's dense child array, and every operation on it returns a **new**
      array rather than mutating the input.
- [ ] No use of `java.lang.Integer.bitCount` or its siblings **inside** the
      exercises that ask you to build them (E4, E3's `log2Floor`). They are the
      oracle the tests compare against, not the implementation.

### E. Empirical Gate — Record The Numbers

- [ ] **Intrinsic gap.** Using the `medianNanos` harness, per-call cost of
      counting bits over the same 100,000 random inputs:
      - `PopCount.naive`: `______ ns` · `kernighan`: `______ ns` ·
        `swar`: `______ ns` · `Integer.bitCount`: `______ ns`
      - Ratio of your best implementation to the intrinsic: `______ ×`

- [ ] **Controlled experiment.** Re-run with the intrinsic disabled:
      ```bash
      sbt "set annex/Test/javaOptions += \"-XX:-UsePopCountInstruction\"" annex/test
      ```
      - `Integer.bitCount` with the intrinsic off: `______ ns`
      - State in one sentence what the delta proves about who is actually
        executing your call: `______________________`

- [ ] **Data dependence.** `kernighan` over inputs with 1 set bit vs 31 set bits:
      - sparse: `______ ns` · dense: `______ ns`
      - Explain why `swar` shows no such gap: `______________________`

- [ ] **Bytecode reading.** Run `javap -c -p` on your compiled `Bits` and
      `TwosComplement`, and record:
      - The opcode sequence emitted for `~x`: `______________________`
      - The opcode sequence emitted for a literal `x / 2`: `______________`
      - Whether `floorDiv2` and `x / 2` compile to the same instructions, and
        why that answer is the point of E2: `______________________`

- [ ] **Allocation.** `BitSet64` operations over 100,000 iterations, measured
      with Module 1's `AllocationProbe` technique:
      - Bytes allocated: `______`
      - If it is not zero, name what escaped and why an `opaque type` failed to
        prevent it: `______________________`

### F. Engineering Hygiene

- [ ] All code formatted (`sbt scalafmtAll`) with no manual override.
- [ ] Every public definition carries a Scaladoc stating its **contract** —
      including, for every partial operation, the precondition and the behaviour
      at `Int.MinValue`, `0`, and negative inputs.
- [ ] Commits follow `docs/git-conventions.md` (`a1: <imperative summary>`), one
      commit per concept proven.
- [ ] Annotated milestone tag `a1-bitwise-arithmetic` created, using the message
      template in `docs/git-conventions.md`, with a real entry under `Learned:`.

### G. Oral Defence

- [ ] Answer the post-module conceptual challenges (Step 4 of the routine)
      without consulting the guide.

---

## Self-Check Answers — A1

> Write your Self-Check answers here before requesting the annex audit.

1.
2.
3.
4.
5.
6.

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
