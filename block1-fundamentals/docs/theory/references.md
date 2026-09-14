# Block 1 — Curated References

Sources are ranked by *return on reading time* for this curriculum, not by fame.
A reference marked **[core]** is one whose absence will actually hurt you later.

---

## Module 1 — JVM Semantics & Immutability Allocation Stress

### Primary Specifications

- **[core]** Lindholm, Yellin, Bracha, Buckley — *The Java Virtual Machine
  Specification, Java SE 21 Edition*. Read **§2.5 (Run-Time Data Areas)** and
  **§2.6 (Frames)**. Around fifteen pages, and they are the ground truth for
  everything in Part I of the module guide.
- **JEP 439: Generational ZGC** — the design rationale for why a low-latency
  collector eventually had to become generational anyway. Short and unusually
  well written.

### Books

- **[core]** Shipilëv, Aleksey — *JVM Anatomy Quarks* (free, online). Not a book
  but denser than most. Mandatory quarks for this module:
  - **#4 TLAB Allocation** — why allocation is three instructions.
  - **#18 Scalar Replacement** — the definitive explanation of what escape
    analysis actually deletes, with bytecode and assembly.
  - **#8 Local Variable Reachability** — why an object can be collected while a
    local variable still "holds" it.
- **[core]** Oaks, Scott — *Java Performance: The Definitive Guide*, 2nd ed.
  Chapters 5–6 (garbage collection algorithms and tuning). The most practical
  treatment of G1 behaviour available.
- Jones, Hosking, Moss — *The Garbage Collection Handbook*, 2nd ed. The academic
  reference. Read **Ch. 9 (Generational GC)** and **Ch. 11 (Region-based)** for
  this module; the rest is a career-long resource, not a week-one read.
- Evans, Gough, Newland — *Optimizing Java*. Strongest chapters are the ones on
  JIT compilation and on the discipline of benchmarking honestly.
- Goetz et al. — *Java Concurrency in Practice*. Not yet — but keep it near the
  desk for Block 3.

### Papers

- **[core]** Choi, Gupta, Serrano, Sreedhar, Midkiff (1999) — *Escape Analysis
  for Java*, OOPSLA. The original algorithm HotSpot's C2 still descends from.
- Ungar, David (1984) — *Generation Scavenging: A Non-Disruptive High Performance
  Storage Reclamation Algorithm*. The paper that established the weak
  generational hypothesis.
- Detlefs, Flood, Heller, Printezis (2004) — *Garbage-First Garbage Collection*,
  ISMM. The G1 paper: regions, remembered sets, and the pause-target model.
- Österlund, Karlsson — ZGC design material on **colored pointers and load
  barriers**. Explains how relocation can be concurrent with mutation.

### Talks / Video Search Terms

Search these exact phrases; the speakers matter more than the venue:

- `"Shipilev" "JVM Anatomy Park"` — the talk version of the quarks.
- `"Shipilev" "Nanotrusting the Nanotime"` — how to benchmark on the JVM without
  lying to yourself. Watch this **before** writing Exercise 9.
- `"Cliff Click" "A JVM Does What?"` — JIT compilation and deoptimisation from
  the person who wrote C2.
- `"Per Liden" "ZGC"` — concurrent collection, from ZGC's architect.
- `"Jon Masamitsu" OR "Charlie Hunt" "G1 GC tuning"` — practical G1 diagnosis.

### Tools To Have Installed By The End Of The Week

- **JDK Mission Control + Flight Recorder** — already in your JDK 21. The
  `jdk.ObjectAllocationSample` event is the production-grade version of
  Exercise 3's instrument.
- **`jcmd`** — `jcmd <pid> GC.heap_info`, `VM.flags`, `Thread.print`. Zero
  install cost, works on any running JVM.
- **JOL (Java Object Layout)** — `org.openjdk.jol`. Prints the *actual* field
  layout and padding of a class. Use it to check your Exercise 7 arithmetic
  against reality once you have committed to an answer. **Do not** add it as a
  project dependency; run it standalone so the exercise stays honest.
- *(Deliberately not used)* **JMH** — the correct tool for real benchmarking, and
  out of scope here precisely because Exercise 9 asks you to feel the problems
  JMH solves.

---

## Reading Order Suggestion For Week 1

1. JVM Spec §2.5–2.6 (one sitting, ~40 min).
2. JVM Anatomy Quark #4, then #18 (~30 min).
3. Write Exercises 1–3. Stop and measure something.
4. Oaks, Ch. 5 (~90 min).
5. Choi et al. §§1–3 (skim the formalism, read the escape states carefully).
6. Write Exercises 4–9.
7. *Nanotrusting the Nanotime* before finalising Exercise 9.

---

## Module 2 — Manual Persistent Data Structures

### Books

- **[core]** Okasaki — *Purely Functional Data Structures* (1998). **Ch. 2** is
  this module almost exactly: lists, trees, and structural sharing, with the
  pictures this guide's §14 redraws. **Ch. 3** is balanced trees, which is where
  Block 2 picks up. Chapters 5 and 6 are amortisation and the persistence
  problem — leave them until Annex A2 exists, because they assume the formalism
  that annex will teach.
- **[core]** Cormen, Leiserson, Rivest, Stein — *Introduction to Algorithms*,
  **Ch. 3** only. The formal definitions of O, Θ and Ω, in about twenty pages.
  The rest of the book is a reference, not a read.
- Bird — *Thinking Functionally with Haskell*, Ch. 7. The same structures with
  laziness in the language rather than bolted on, which makes the contrast with
  Scala's strictness visible.

### Papers

- **Bagwell — *Ideal Hash Trees* (2001)**, §§1–3. The HAMT behind Scala's
  immutable `Map` and `Set`. Pairs directly with Annex A1, Exercise 8, and it is
  §15's arithmetic at a branching factor of 32.
- **Driscoll, Sarnak, Sleator, Tarjan — *Making Data Structures Persistent*
  (1989)**. The paper that named the field and separated *partial* from *full*
  persistence. Read the introduction even if you skip the proofs.

### Source To Read

- `scala.collection.immutable.List` — read `:::`, `reverse` and `foldRight`, and
  check each against §12 and §18. `foldRight` in particular is worth reading
  closely: the standard library does not implement it the naive way, and the
  reason is Exercise 4's last test.
- `scala.collection.immutable.Vector` — the 32-way trie. Do not try to follow it
  line by line yet; look only at how depth is bounded.

### Terms Worth A Video Rather Than A Chapter

- *"structural sharing persistent data structures"* — the animations make §14
  land faster than prose does.
- *"amortized analysis banker's method"* — preparation for Annex A2, not for
  this module.
---

## Module 3 — Stack Optimization & Control Flow Elimination

### Primary Specifications

- **JVM Specification §2.5.2, *Java Virtual Machine Stacks*.** Two pages, and
  they are the source for everything in Part I: what a frame contains, that the
  stack may be fixed or dynamic, and that exhaustion raises
  `StackOverflowError` rather than being recoverable in general.
- **JVM Specification §2.6, *Frames*.** Local variable array, operand stack,
  and why frame size is a property of the *method* rather than of the JVM —
  which is why the guide's §3 measures two different methods and gets two
  different depths.
- **JVM Specification §2.6.5.** Read this for what is *absent*: there is no
  tail-call instruction, which is why `@tailrec` must transform the method into
  a loop rather than ask the JVM for anything.

### Books

- **Bird & Wadler, *Introduction to Functional Programming*, ch. 4.**
  Accumulator passing derived rather than demonstrated — the transformation of
  the guide's §8, with the proof that it preserves the function.
- **Okasaki, *Purely Functional Data Structures*, ch. 3.** Balanced trees
  without mutation. §3.2 is red-black; the AVL treatment is the natural
  companion to Exercise 9, and the chapter's framing — *rebuilding the path is
  the price of persistence* — is Module 2's rule restated for a balanced
  structure.
- **Sedgewick & Wayne, *Algorithms*, §3.3.** The four rotation cases drawn
  rather than described. Read the pictures; ignore the Java, which mutates.
- **Abelson & Sussman, *SICP*, §1.2.1.** The distinction between a *recursive
  process* and a *recursive procedure*, which is the whole of the guide's Part
  II in two pages and forty years earlier.

### Papers

- **Hutton, *A tutorial on the universality and expressiveness of fold*.** Why
  `foldRight` is the fundamental one and `foldLeft` the derived one, which is
  the opposite of the order this module needs them in — worth reading precisely
  for that tension.
- **Bjarnason, *Stackless Scala With Free Monads* (2012).** The trampoline of
  the guide's §16, built properly. Read it after Exercise 6's
  `foldRightComposed` has shown you what problem it solves.
- **Steele, *Debunking the "Expensive Procedure Call" Myth* (1977).** The
  argument that a tail call *is* a goto, made when the claim was still
  controversial.

### Source To Read

- **`scala.collection.immutable.List.foldRight`.** One line, and it is §14's
  fix with the argument swap of §21 written out. Verified here: it survives a
  million elements, so it is not the naive recursion.
- **`scala.collection.immutable.List.map`.** A `while` loop mutating the tail
  pointer of the cell it just built. The purity gate forbids this in `module3`;
  the library's justification is that the mutation never escapes, which is the
  same argument `CLAUDE.md` makes for a micro-library engine.
- **`scala.util.control.TailCalls`.** The standard library's trampoline, about
  forty lines. Compare its shape against the guide's §16 sketch.

### Terms Worth A Video Rather Than A Chapter

- *tail call elimination* — for the animation of a frame being reused
- *AVL rotation* — for the four cases moving, which is much clearer than prose
- *JEP 444 virtual threads* — Ron Pressler on why a stack on the heap changes
  the arithmetic of §25
- *continuation passing style* — the generalisation of the accumulator, and
  where Block 3 goes next
