# Annex Track — Curated References

The annex track covers the *assumed prerequisites* of the main curriculum:
material that textbooks on functional programming take for granted and that
systems code uses without comment. References are ranked by return on reading
time. **[core]** marks a source whose absence will actually hurt you later.

---

## A1 — Bitwise Arithmetic & Binary Representation

### Books

- **[core]** Warren, Henry S. Jr. — *Hacker's Delight*, 2nd ed. The definitive
  work, and unusually short. For this annex:
  - **Ch. 2** — manipulating rightmost bits; the derivations behind every idiom
    in Part V of the guide.
  - **Ch. 5** — counting bits; the SWAR `popcount` of Part VI is Warren's, and his
    derivation is worth reading rather than trusting.
  - **Ch. 3** — powers of two, rounding up, and the `log2` tricks.
  - Skip Ch. 6–8 for now; return to them for Block 4's codecs.
- **[core]** Bryant, Randal & O'Hallaron — *Computer Systems: A Programmer's
  Perspective*, 3rd ed. **Ch. 2 (Representing and Manipulating Information)** is
  the single best pedagogical treatment of two's complement in print. If Part I of
  the guide felt fast, read this chapter before touching the exercises.
- Knuth, Donald — *TAOCP Vol. 4A, §7.1.3 (Bitwise Tricks and Techniques)*. The
  scholarly treatment, including the history of each identity. A reference to
  consult, not a chapter to read straight through.
- Sedgewick & Wayne — *Algorithms*, 4th ed., §3.4. Read only the discussion of
  why hash table capacities are powers of two, and how bad hashes interact with
  masking.

### Primary Specifications

- **[core]** *The Java Virtual Machine Specification, Java SE 21*, **§6.5**, the
  entries for `ishl`, `ishr`, `iushr`, `lshl`, `lshr`, `lushr`. Three pages, and
  they are the normative statement of the masked shift distance from Part III.14. Read
  them once so that `1 << 32 == 1` is never surprising again.
- *The Java Language Specification*, **§5.1.2 (Widening Primitive Conversion)** —
  the normative source for the sign-extension trap of Part III.16.
- *The Scala Language Specification*, **§6.12.3 (Operators)** — the
  first-character precedence rule of Part III.17.

### Papers

- **[core]** Bagwell, Phil (2001) — *Ideal Hash Trees*, EPFL technical report.
  The origin of the HAMT. Part IV.20 of the guide is a compression of this
  paper; read §§1–3 to see the bitmap-plus-popcount argument in its original
  form. This is the paper Scala's `Vector` and `HashMap` descend from.
- Bagwell, Phil (2000) — *Fast And Space Efficient Trie Searches*. The
  predecessor; useful for seeing why the branching factor converged on 32.
- Muła, Kurz, Lemire (2017) — *Faster Population Counts Using AVX2
  Instructions*. Modern, and a good calibration of how far hardware has moved
  past hand-written SWAR.

### JDK Sources Worth Reading Directly

Reading the JDK is a skill this curriculum wants you to build. These three
methods are short, self-contained, and are the production versions of exercises
you are about to write:

- `java.lang.Integer.bitCount` — Warren's SWAR verbatim, with a comment saying
  so. Compare it against your Exercise 4.
- `java.lang.Integer.numberOfTrailingZeros` — a branchless binary search.
- `java.util.HashMap.hash` and `HashMap.tableSizeFor` — the mixing function
  (`h ^ (h >>> 16)`) and the round-up-to-power-of-two from Part IV.19, in production.

### Talks / Video Search Terms

- `"Shipilev" "The Black Magic of (Java) Method Dispatch"` — for how intrinsics
  and inlining actually reach the CPU.
- `"Matt Godbolt" "What Has My Compiler Done For Me Lately"` — C++, but the best
  existing demonstration of reading compiler output to see bit tricks emitted.
- `"two's complement" "why it works"` — any clear treatment; the goal is for Part I.6.4
  to feel inevitable rather than clever.
- `"Bagwell" OR "HAMT" "persistent data structures" popcount` — for the bridge
  into Module 2.

### Tools

- **Compiler Explorer** (godbolt.org) — paste the SWAR `popcount` and watch a
  modern compiler collapse it to `POPCNT`. The fastest possible demonstration of
  Part VI's punchline.
- **`javap -c -p`** — already in your JDK. Confirms that `~x` compiles to
  `iconst_m1; ixor`, that `x / 2` becomes a bias-corrected shift, and that your
  own code allocates nothing.
- **`-XX:+PrintIntrinsics`** (with `-XX:+UnlockDiagnosticVMOptions`) — proves
  which of your calls C2 replaced with a machine instruction.

---

## Reading Order Suggestion

1. Bryant & O'Hallaron Ch. 2, §§2.1–2.3 (~90 min). Non-negotiable if two's
   complement is new.
2. Guide Parts I–III, then write Exercises 1–3. Stop and read your own bytecode
   with `javap`.
3. *Hacker's Delight* Ch. 2 (~45 min), then Exercises 4–5.
4. JVM Spec §6.5 shift entries (~10 min). Verify the masking claim in a REPL.
5. Bagwell §§1–3 (~40 min), then Exercises 6–8.
6. *Hacker's Delight* Ch. 5, and compare against `Integer.bitCount` in the JDK
   source before finalising Exercise 4.
