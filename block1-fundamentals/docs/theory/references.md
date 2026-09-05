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
