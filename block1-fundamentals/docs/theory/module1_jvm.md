# Module 1 — JVM Semantics & Immutability Allocation Stress

> **Block 1 · Week 1 · Complexity ⭐️⭐️**
> Milestone tag on completion: `b1-m1-jvm-semantics`

---

## 0. The Question This Module Exists To Answer

Pure functional programming allocates *aggressively*. Every `map` builds a new
collection. Every `copy` on a case class builds a new object. A `foldLeft` over a
million-element list may create a million intermediate values. An engineer
trained on C++ or on imperative Java looks at this and concludes, reasonably,
that the paradigm is unaffordable.

That conclusion is wrong, and the reason it is wrong is the single most important
thing you will learn this week:

> **On a generational JVM, allocation is nearly free and *garbage* is nearly
> free. What costs money is *survival*.**

By the end of this module you must be able to explain — with numbers, not
opinions — why a Scala service allocating 1 GB/second can hold a p99 latency
under 5 ms, and precisely which coding decisions destroy that property.

---

## 1. The Two Memories

### 1.1 The Stack — Per-Thread, Cheap, Bounded

Each JVM thread owns a private **stack** (JVM Specification §2.5.2). Calling a
method pushes a **frame**; returning pops it. A frame is a fixed-size record
determined *at compile time* and recorded in the class file:

| Frame component | Contents |
| :--- | :--- |
| **Local variable array** | `this`, parameters, local slots. One slot = 32 bits; `long`/`double` occupy two. |
| **Operand stack** | The working scratchpad that bytecode instructions push to and pop from. |
| **Frame data** | Constant pool reference, return address, exception table pointer. |

Because the sizes are static, pushing a frame is pointer arithmetic. There is no
allocator, no synchronisation, no bookkeeping. Popping it reclaims everything
instantly — which is why stack memory needs no garbage collector at all.

The price is that the stack is **bounded**. `-Xss` defaults to roughly 512 KB –
1 MB depending on platform. Exceed it and you get `StackOverflowError`. A rough
budget: a frame with a handful of locals costs ~40–100 bytes, so a 1 MB stack
buys you on the order of **10,000–25,000 nested calls**. That number is the
entire reason Module 3 exists — a naive `foldRight` over a 100,000-element list
dies, and you will need tail calls and trampolines to survive it.

> **Forward reference (Block 3):** a *virtual thread* (Project Loom) does not own
> a fixed OS stack. Its stack lives on the heap as a growable chunk, copied in
> and out on mount/unmount. That is exactly why you can have a million of them,
> and also why deep recursion inside a virtual thread has a very different cost
> profile. Park this thought; we return to it in Module 8.

### 1.2 The Heap — Shared, GC-Managed, Where All Objects Live

Every `new`, every case class instance, every boxed `Int`, every closure that
captures a variable — all of it lands on the heap. The heap is shared across
threads, and this is where the entire cost model of functional programming plays
out.

**Object layout on 64-bit HotSpot with compressed oops** (the default whenever
the heap is under ~32 GB):

```text
+------------------+------------------+------------------+---------+
| mark word        | klass pointer    | instance fields  | padding |
| 8 bytes          | 4 bytes          | ...              | to 8B   |
+------------------+------------------+------------------+---------+
        \_________  12-byte header  _________/
```

- The **mark word** carries the identity hash code, the GC age bits, and the
  lock state.
- The **klass pointer** points to the class metadata in Metaspace.
- **References** are 4 bytes each (compressed), not 8.
- The total is padded up to a multiple of **8 bytes**.
- **Arrays** carry an extra 4-byte length field, so their header is 16 bytes.

Work through the consequences, because they are brutal and non-obvious:

| Value | Naive expectation | Actual heap cost |
| :--- | :---: | :---: |
| `final case class Vec2(x: Double, y: Double)` | 16 B | `align8(12 + 16)` = **32 B** |
| A boxed `java.lang.Integer` | 4 B | `align8(12 + 4)` = **16 B** |
| A `::` cons cell (2 references) | 8 B | `align8(12 + 8)` = **24 B** |
| One element of a `List[Int]` | 4 B | 24 (cell) + 16 (box) = **40 B** |
| `Array[Int]` of 1,000,000 | 4 MB | 16 + 4,000,000 ≈ **4 MB** |
| `List[Int]` of 1,000,000 | 4 MB | **40 MB — a 10× tax** |

That last row is the single most expensive line of ignorance in day-to-day
Scala. It is not a reason to abandon `List`; it is a reason to know *when* the
10× matters.

---

## 2. Allocation Is Cheap. Survival Is Expensive.

### 2.1 The TLAB: Allocation Is Three Instructions

HotSpot gives each thread a **Thread-Local Allocation Buffer** — a private slab
carved out of the young generation. Allocating inside a TLAB is:

```text
1. read the current bump pointer
2. add the object size
3. compare against the TLAB limit; if it fits, store the new pointer
```

No locks. No free lists. No fragmentation search. It is competitive with stack
allocation in C. Only when the TLAB is exhausted does the thread take a slow path
and request a fresh one.

This is why "reduce the number of objects you create" is, on its own, **bad
advice** on the JVM. Creating an object is not the expensive event.

### 2.2 The Weak Generational Hypothesis

Empirically observed across decades of workloads (Ungar, 1984):

> **Most objects die young.** The mortality rate of newly allocated objects is
> extremely high; the objects that survive a few collections tend to survive
> indefinitely.

HotSpot exploits this with a **copying collector** in the young generation. Here
is the crucial asymptotic property:

> **A copying collector's cost is proportional to the volume of *surviving*
> data, not to the volume of garbage.**

Dead objects are never touched, never visited, never written. They are not
"freed" — the collector copies the survivors elsewhere and declares the entire
region empty. If a young collection finds a 512 MB nursery containing 2 MB of
live data, it does 2 MB of work. **The 510 MB of garbage cost exactly zero.**

### 2.3 The Real Metric

So the metric an engineer actually tunes is not allocation rate. It is:

| Metric | Meaning | Why it matters |
| :--- | :--- | :--- |
| **Allocation rate** (B/s) | How fast you fill the nursery | Determines the *frequency* of young GCs |
| **Promotion rate** (B/s) | How much survives into the old generation | Determines the *cost* of each GC and old-gen pressure |
| **Live set size** | Steady-state retained bytes | Determines pause duration and heap sizing |

Functional programming has a **very high allocation rate** and a
**characteristically low promotion rate** — intermediate values from a `map`
chain are dead before the next young collection even runs. That combination is
precisely the one generational collectors were designed for. FP is not fighting
the JVM's memory model; it is the workload that model was tuned for.

The pathology to fear is the opposite shape: a modest allocation rate where most
objects *survive*. A cache. A queue that grows. A memo table. Which brings us to
the most counter-intuitive result of the module.

---

## 3. Why Immutability Is *Generationally Friendly*

Generational collection has a correctness problem: a young GC must find every
reference *into* the young generation without scanning the entire old generation
(which would defeat the purpose). Those cross-generational pointers —
**old → young** — must be tracked.

The JVM tracks them with a **write barrier**. Every reference field store
compiles to extra machine code recording "this old-generation region now points
somewhere young" into a **card table** (G1 additionally maintains per-region
**remembered sets**). At GC time those dirty cards are scanned as extra roots.

Now the key observation:

> An immutable object's fields are written exactly once, at construction. At that
> moment the object is the **youngest thing in the heap**, so every reference it
> holds necessarily points to something **older or equal**.
>
> **Immutable data structures generate young → old edges, which are free.
> Mutable data structures generate old → young edges, which are expensive.**

This inverts the folk intuition completely. The mutable "optimisation" —

```scala
// Anti-pattern: an old-generation cache mutated to point at fresh objects.
cache.put(key, freshlyComputedValue) // old -> young store: barrier + dirty card
```

— pays a write barrier on every store *and* extends the value's lifetime,
promoting garbage that would otherwise have died for free in the nursery. The
immutable version that "wastefully" rebuilds a structure often costs strictly
less at the collector level.

**Structural sharing** (Module 2) sharpens this further: a persistent tree update
rewrites only the path to the root, so the new version allocates `O(log n)` fresh
nodes — all of which point *down* into the old, shared subtrees. Young → old.
Free.

---

## 4. Escape Analysis: When Allocation Costs *Nothing At All*

### 4.1 The Analysis

C2, HotSpot's optimising JIT compiler, performs **escape analysis** (Choi et al.,
IBM, 1999) after inlining. For each allocation site it computes how far the
reference can travel:

| Escape state | Meaning | Optimisation unlocked |
| :--- | :--- | :--- |
| **NoEscape** | The object never leaves the compiled method | **Scalar replacement** — the allocation is deleted outright |
| **ArgEscape** | Passed to a callee but does not outlive the caller | Lock elision, partial optimisation |
| **GlobalEscape** | Stored to a field, returned, thrown, published to a thread | Nothing — a real heap allocation |

**Scalar replacement** (`-XX:+EliminateAllocations`, on by default) is the prize.
A `NoEscape` object is dismantled: its fields become ordinary JIT-managed values
living in **registers or the stack frame**, and the object never exists. Not
"allocated cheaply" — *never allocated*.

```scala
def sumNorms(xs: Array[Double], ys: Array[Double]): Double =
  // A Vec2 is constructed per element and immediately consumed.
  // The reference never escapes -> C2 deletes the allocation entirely.
  // Steady-state cost: zero bytes.
  loop(0, 0.0)
```

This is the mechanism that makes idiomatic FP viable in hot loops. Tuples,
`Option`s, small case classes, and closures in tight code frequently cost nothing
once the JIT has warmed up.

### 4.2 What Destroys It — The Engineer's Checklist

Escape analysis is a *whole-compiled-method* analysis, so it dies whenever
inlining dies:

1. **Megamorphic call sites.** A virtual call whose receiver has seen ≥ 3 distinct
   implementations at that site cannot be inlined, so the callee stays opaque and
   its arguments must escape. This is why "code to an interface with many
   implementations" — excellent design advice — carries a real cost in hot loops.
2. **Inlining budget exhaustion.** HotSpot refuses to inline methods over
   `-XX:MaxInlineSize` (35 bytecodes) unless they are hot, and over
   `-XX:FreqInlineSize` (325 bytecodes) essentially ever. **Long methods are
   optimisation barriers.** Small methods are not merely readable; they are
   faster.
3. **Storing the object anywhere durable** — a field, an array, a returned
   collection. One store into a global structure and the whole chain escapes.
4. **Cold code.** Escape analysis is a C2 optimisation. Interpreted and
   C1-compiled code allocates for real. Everything in this section applies **only
   after warmup** — typically thousands of invocations under tiered compilation.

### 4.3 How To *Prove* It (Not Believe It)

The exercise set uses the cleanest empirical instrument available without a debug
JVM build:

```scala
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory

// Cumulative bytes allocated by the current thread. HotSpot-specific.
val bean = ManagementFactory.getThreadMXBean.asInstanceOf[ThreadMXBean]
bean.getThreadAllocatedBytes(Thread.currentThread().threadId())
```

Take a reading, run the workload, take another, subtract. Then run the identical
measurement with escape analysis disabled:

```bash
sbt "set fundamentals/Test/javaOptions += \"-XX:-DoEscapeAnalysis\"" fundamentals/test
```

The difference between the two runs **is** the scalar replacement. That A/B is a
controlled experiment: same code, one variable changed. This is how a scientist
establishes the claim, and it is what Exercise 5 asks of you.

---

## 5. Boxing: The One Tax The Compiler Often Cannot Remove

The JVM has no generics at runtime. `List[Int]` erases to a container of
`Object`, so every `Int` must be wrapped in a `java.lang.Integer` — the 16-byte
object from the table in §1.2.

Two consequences worth memorising:

- **`Integer.valueOf` caches −128..127.** Micro-benchmarks over small integers
  therefore show *no* allocation and lie to you about production behaviour. Test
  with values outside the cache.
- **`Array[Int]` is a genuine primitive array** — `int[]` at the bytecode level,
  4 bytes per element, contiguous, cache-friendly. It is the escape hatch when
  the 10× tax is unaffordable. Note that calling generic combinators on it
  (`arr.foldLeft`) re-introduces boxing through the generic signature, so an
  allocation-free traversal needs an explicit recursive walk over indices.

Scala 3 gives you `opaque type` to build zero-overhead domain wrappers over
primitives — the type exists at compile time and vanishes at runtime. That is
Module 4's subject; for now, simply note that the box is not always inevitable.

---

## 6. Collectors Under Churn: G1 vs ZGC

### 6.1 G1 — Region-Based, Pause-Target Driven (default on JDK 21)

G1 partitions the heap into ~2048 equal **regions** (1–32 MB each), dynamically
labelled Eden, Survivor, Old, or Humongous. Collection is **evacuation**: live
objects are copied out of a chosen collection set of regions, which are then
reclaimed wholesale. G1 picks that set to meet `-XX:MaxGCPauseMillis` (default
200 ms) — it is a *soft-real-time, pause-target* collector.

Two failure modes you must be able to name:

- **Humongous allocations.** An object larger than **50% of a region** cannot
  live in Eden. It is allocated directly into contiguous Old-generation regions
  and is reclaimed only by the concurrent cycle. A workload that repeatedly
  allocates large arrays or long strings degrades badly.
- **Evacuation failure / to-space exhaustion.** If no free region is available to
  copy survivors into, G1 must abort and fix up the heap in place — a long, ugly
  pause. This is what an over-tight heap under high promotion looks like.

### 6.2 ZGC — Concurrent, Colored Pointers, Sub-Millisecond Pauses

ZGC stores GC metadata **inside the pointer itself** (colored pointers) and
installs a **load barrier**: every reference read checks the colour bits and, if
stale, self-heals the reference. This lets ZGC **relocate objects while the
application runs**, so pause times become independent of heap size — typically
well under 1 ms on heaps of hundreds of gigabytes.

The trade-off is honest and worth stating: those barriers cost throughput, on the
order of a few percent. **ZGC buys latency with throughput.** On JDK 21,
generational ZGC is available and strongly preferred for allocation-heavy
workloads:

```bash
-XX:+UseZGC -XX:+ZGenerational
```

### 6.3 The Selection Rule

| Workload | Collector | Reason |
| :--- | :--- | :--- |
| Batch / ETL, throughput is everything | Parallel GC | No concurrent-phase overhead |
| General service, balanced | **G1** | Sane default, predictable |
| Latency-critical, large heap, high churn | **Generational ZGC** | Pauses independent of heap size |

---

## 7. The Engineer's Cost Model (SE Application)

Translating all of the above into decisions you will actually defend in a design
review:

1. **Do not "optimise" by pooling objects.** Object pools convert cheap young
   garbage into expensive old-generation live data, add barrier traffic, and
   introduce lifecycle bugs. They are justified only for genuinely expensive
   external resources (connections, file descriptors, direct buffers) — which is
   Module 11's subject.
2. **Suspect every cache.** A cache is a machine for defeating the generational
   hypothesis. Bound it, measure its hit rate, and know its retained size.
3. **Keep hot methods small.** Below the inline threshold, a method is
   transparent to the optimiser and its allocations can vanish. Above it, nothing
   is optimised across the boundary.
4. **Watch polymorphism in hot paths.** Bimorphic call sites inline; megamorphic
   ones do not. A `sealed trait` with three implementations behaves very
   differently from an open interface with twelve.
5. **Size the heap for the live set, not the allocation rate.** A larger nursery
   reduces GC *frequency* almost for free; a larger old generation only defers an
   expensive problem.
6. **Measure before you argue.** `-Xlog:gc` and an allocation counter settle in
   ten minutes what a design review would debate for an hour.

---

## 8. Instrument Cheat Sheet

```bash
# What the collector is doing, with timestamps and heap deltas
-Xlog:gc*:file=gc.log:time,uptime,level,tags

# Which methods the JIT compiled, and at which tier
-XX:+PrintCompilation

# Which calls were inlined, and why not (diagnostic)
-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining

# The controlled experiment for Exercise 5
-XX:-DoEscapeAnalysis          # disable the analysis entirely
-XX:-EliminateAllocations      # keep the analysis, disable scalar replacement

# Collector selection
-XX:+UseG1GC                   # default on JDK 21
-XX:+UseZGC -XX:+ZGenerational # low-latency, high-churn
```

In-process, allocation is measured with
`com.sun.management.ThreadMXBean#getThreadAllocatedBytes`; flight-recorder
profiling uses the `jdk.ObjectAllocationSample` event.

---

## 9. Self-Check — You Have Not Finished This Module Until You Can Answer

1. A service allocates 800 MB/s and shows 3 ms young pauses. Its allocation rate
   doubles to 1.6 GB/s and pause *duration* stays at 3 ms. Explain precisely why,
   and state what would have to change for the duration to grow.
2. Why does adding an object pool to a hot path frequently make p99 latency
   *worse*?
3. Why does an immutable data structure produce cheaper write-barrier traffic
   than a mutable one, given that it performs strictly more stores overall?
4. You measure zero allocation in a loop that visibly constructs a case class per
   iteration. Name the optimisation, the compiler tier that performs it, and two
   distinct code changes that would silently switch it off.
5. `List[Int]` of one million elements: give the heap cost and derive it from the
   object layout rules. Do the same for `Array[Int]`.

Curated sources for this module live in [`references.md`](./references.md).
