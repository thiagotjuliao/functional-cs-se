# Module 1 — JVM Semantics & Immutability Allocation Stress

> **Block 1 · Week 1 · Complexity ⭐️⭐️**
> Milestone tag on completion: `b1-m1-jvm-semantics`

---

## How To Read This Guide

This guide assumes you can write Scala but have **never reasoned about where your
objects physically live** or what they cost. It builds from "what is a stack
frame" up to the cost model you will defend in a design review.

It is a staircase. Each Part depends only on the ones before it:

| Part | Content | Unlocks |
| :--- | :--- | :--- |
| **I** | Where memory lives, and what one object actually costs in bytes | E1, E2, E7 |
| **II** | Why allocation is nearly free and garbage is *exactly* free | E3 |
| **III** | Why immutability is cheaper for the collector, not more expensive | — |
| **IV** | Escape analysis: when an allocation costs literally nothing | E5 |
| **V** | Boxing — the one tax the compiler often cannot remove | E4 |
| **VI** | Collectors under churn: G1 and ZGC | GC observation |
| **VII** | The engineer's cost model | E6, E8, E9 |

**Every number printed in this guide was computed and verified before it was
written.** Where a result surprises you, reproduce it — the surprise is the
lesson.

---

# Part I — Where Memory Lives

## 1. The Question This Module Exists To Answer

Pure functional programming allocates *aggressively*. Every `map` builds a new
collection. Every `copy` on a case class builds a new object. A `foldLeft` over a
million-element list may create a million intermediate values.

An engineer trained on C++ or on imperative Java looks at this and concludes,
reasonably, that the paradigm is unaffordable. Let us put a number on the
intuition. A service allocating 1 GB per second, with objects averaging 32 bytes,
is creating:

```text
   1 GB/s ÷ 32 bytes  =  33.6 million objects per second
```

Thirty-three million allocations a second sounds like a catastrophe. It is
routine, and by the end of this module you will be able to say precisely why —
with numbers rather than opinions.

The conclusion this module drives toward, stated up front so you can watch it
being earned:

> **On a generational JVM, allocation is nearly free and *garbage* is exactly
> free. What costs money is *survival*.**

## 2. Two Memories

Every JVM thread has access to two quite different places to put data. Almost
every performance question in this module reduces to *which one* a value ends up
in.

```text
   THE STACK                          THE HEAP
   ─────────                          ────────
   one per thread, private            one per JVM, shared by all threads
   fixed-size records                 objects of any size
   freed automatically on return      freed by the garbage collector
   bounded (~512 KB – 1 MB)           bounded by -Xmx (gigabytes)
   holds: locals, parameters          holds: every object you ever create
```

Hold this picture. Parts I–IV are, in effect, an extended argument about how to
get values into the left column.

## 3. The Stack — Cheap, Automatic, Bounded

Calling a method **pushes a frame**; returning **pops it**. A frame is a
fixed-size record whose shape is determined *at compile time* and recorded in the
class file (JVM Specification §2.6):

| Frame component | Contents |
| :--- | :--- |
| **Local variable array** | `this`, parameters, local slots. One slot = 32 bits; `long`/`double` occupy two. |
| **Operand stack** | The working scratchpad that bytecode instructions push to and pop from. |
| **Frame data** | Constant pool reference, return address, exception table pointer. |

Because every size is known statically, pushing a frame is **pointer
arithmetic**: move the stack pointer down by a known constant. No allocator, no
synchronisation, no bookkeeping. Popping reclaims everything instantly — which is
why stack memory needs no garbage collector at all.

### 3.1 The Price: It Is Bounded

`-Xss` defaults to roughly 512 KB – 1 MB depending on platform. Exceed it and you
get `StackOverflowError`. A frame with a handful of locals costs on the order of
40–100 bytes, so the budget is:

```text
    stack     frame        nested calls available
   ───────   ───────      ────────────────────────
   512 KB      40 B              13,107
   512 KB     100 B               5,242
     1 MB      40 B              26,214
     1 MB     100 B              10,485
```

**That table is the entire reason Module 3 exists.** A naive `foldRight` over a
100,000-element list is a chain of 100,000 nested calls, and every row above says
it dies. Tail calls and trampolines are how you survive it.

> **Forward reference (Block 3):** a *virtual thread* (Project Loom) does not own
> a fixed OS stack. Its stack lives on the heap as a growable chunk, copied in
> and out on mount/unmount. That is exactly why you can have a million of them,
> and also why deep recursion inside one has a very different cost profile. Park
> this thought; we return to it in Module 8.

## 4. The Heap — What One Object Actually Costs

Every `new`, every case class instance, every boxed `Int`, every closure that
captures a variable — all of it lands on the heap.

### 4.1 The Anatomy Of An Object

An object is not just its fields. On 64-bit HotSpot with compressed oops (the
default whenever the heap is under ~32 GB), it looks like this:

```text
   +------------------+------------------+------------------+---------+
   | mark word        | klass pointer    | instance fields  | padding |
   | 8 bytes          | 4 bytes          | ...              | to 8B   |
   +------------------+------------------+------------------+---------+
           \_________  12-byte header  _________/
```

- The **mark word** carries the identity hash code, the GC age bits, and the lock
  state.
- The **klass pointer** points to the class metadata in Metaspace — it is how the
  JVM knows what type this object is.
- **References are 4 bytes each** when compressed, not 8.
- The total is **padded up to a multiple of 8 bytes**.
- **Arrays** carry an extra 4-byte length field, so their header is 16 bytes.

So the cost of any object is:

```text
   align8( 12  +  sum of field sizes )
```

where `align8(n)` rounds `n` up to the next multiple of 8.

### 4.2 Worked Examples

Apply the formula mechanically. All verified:

```text
   final case class Vec2(x: Double, y: Double)
      12 header + 8 + 8 fields = 28  ->  align8  ->  32 bytes

   java.lang.Integer (one int field)
      12 header + 4 field       = 16  ->  align8  ->  16 bytes

   a `::` cons cell (head + tail, two references)
      12 header + 4 + 4         = 20  ->  align8  ->  24 bytes

   final case class Point(x: Int, y: Int)
      12 header + 4 + 4         = 20  ->  align8  ->  24 bytes

   an object with no fields at all
      12 header + 0             = 12  ->  align8  ->  16 bytes
```

Read that last line twice. **An object holding nothing costs 16 bytes**, because
the header alone is 12 and padding takes it to 16. The header is not a rounding
error; on small objects it is most of the object.

### 4.3 The Consequence: Where `List[Int]` Costs 10×

Now compose the formula. One element of a `List[Int]` is *two* objects: the cons
cell, and the boxed `Integer` it points at.

```text
   one element of List[Int]  =  24 (cons cell)  +  16 (box)  =  40 bytes
   one element of Array[Int] =                                   4 bytes
```

Scale it to one million elements:

| Structure | Naive expectation | Actual heap cost |
| :--- | :---: | :---: |
| `Array[Int]` of 1,000,000 | 4 MB | `16 + 4,000,000` = **3.81 MiB** |
| `List[Int]` of 1,000,000 | 4 MB | `1,000,000 × 40` = **38.15 MiB** |

**A ten-fold tax, verified.** This is the single most expensive line of ignorance
in day-to-day Scala.

It is *not* a reason to abandon `List`. It is a reason to know **when** the 10×
matters: a list of a thousand elements wastes 36 KB and nobody will ever notice;
a list of ten million in a hot service is 400 MB of avoidable heap. **Exercise 7
makes you derive these numbers before you are allowed to measure them.**

---

# Part II — Why Allocation Is Cheap And Garbage Is Free

Part I established that objects cost bytes. This Part establishes the far less
obvious fact: **creating them is not the expensive event.**

## 5. The TLAB — Allocation Is Three Instructions

HotSpot gives each thread a **Thread-Local Allocation Buffer**: a private slab
carved out of the young generation. Allocating inside a TLAB is:

```text
   1. read the current bump pointer
   2. add the object size
   3. compare against the TLAB limit; if it fits, store the new pointer
```

No locks — the buffer is thread-private, so no other thread can be allocating
into it. No free lists. No searching for a hole big enough. It is competitive
with stack allocation in C.

Only when the TLAB is exhausted does the thread take a slow path and request a
fresh one from the shared heap.

> This is why **"create fewer objects" is, on its own, bad advice on the JVM.**
> The creation is three instructions. The question is what happens next.

## 6. Most Objects Die Young

The **weak generational hypothesis**, observed empirically across decades of
workloads (Ungar, 1984):

> **Most objects die young.** The mortality rate of newly allocated objects is
> extremely high; the ones that survive a few collections tend to survive
> indefinitely.

HotSpot exploits this by splitting the heap: a small **young generation**
(nursery) where everything is born, and an **old generation** for the survivors.
Young collections run often and cheaply; old ones run rarely and expensively.

## 7. A Copying Collector Charges Only For Survivors

The young generation is managed by a **copying collector**, and its cost model is
the pivotal fact of this module:

> **A copying collector's cost is proportional to the volume of *surviving* data,
> not to the volume of garbage.**

Dead objects are never touched, never visited, never written. Nothing is
"freed" — the collector copies the *survivors* out to another region and then
declares the entire original region empty in one stroke.

Watch what that means numerically, for a 512 MB nursery:

```text
   survival rate    copied (the work)    garbage (costs nothing)
   ─────────────    ─────────────────    ───────────────────────
        0.5 %             2.56 MB              509.44 MB
          1 %             5.12 MB              506.88 MB
          5 %            25.60 MB              486.40 MB
         50 %           256.00 MB              256.00 MB
```

At a 1% survival rate, the collector does 5 MB of work to reclaim half a
gigabyte. **The 507 MB of garbage cost exactly zero** — not "cost little", zero.
No code ran for any of it.

That is the whole trick, and it is why the top row of that table is where you
want your workload to live, and the bottom row is where it dies.

## 8. The Three Numbers That Matter

So the metric an engineer tunes is **not** allocation rate. There are three, and
conflating them is the most common mistake in JVM performance discussions:

| Metric | Meaning | What it controls |
| :--- | :--- | :--- |
| **Allocation rate** (B/s) | How fast you fill the nursery | The **frequency** of young GCs |
| **Promotion rate** (B/s) | How much survives into the old generation | The **cost** of each GC, and old-gen pressure |
| **Live set size** | Steady-state retained bytes | Pause **duration** and heap sizing |

Allocation rate buys you *frequency*, which is cheap. Promotion rate buys you
*cost*, which is not.

Worked example — how allocation rate translates into GC frequency:

```text
   1.0 GB/s into a 256 MB nursery  ->  a young GC every  250 ms  (4.0/s)
   1.0 GB/s into a 512 MB nursery  ->  a young GC every  500 ms  (2.0/s)
   2.0 GB/s into a 512 MB nursery  ->  a young GC every  250 ms  (4.0/s)
   0.1 GB/s into a 512 MB nursery  ->  a young GC every 5000 ms  (0.2/s)
```

Doubling the allocation rate doubles the *frequency* and leaves the *duration*
untouched, because duration follows survivors. Doubling the nursery halves the
frequency, almost for free.

**Now put Parts I and II together.** Functional programming has a very high
allocation rate and a characteristically **low promotion rate** — the
intermediate values in a `map` chain are dead before the next young collection
even runs. That combination is precisely the one generational collectors were
designed for.

> FP is not fighting the JVM's memory model. It is the workload that model was
> tuned for.

The pathology to fear is the *opposite* shape: a modest allocation rate where
most objects **survive**. A cache. A queue that grows. A memo table. Which brings
us to the most counter-intuitive result of the module.

**Exercise 3 builds the instrument** that lets you measure any of this.

---

# Part III — Why Immutability Is *Generationally Friendly*

Everyone's folk intuition says mutation is cheaper: update in place, allocate
nothing. This Part shows the intuition is backwards at the collector level, and
exactly why.

## 9. The Cross-Generational Problem

Generational collection has a correctness problem it must solve to work at all.

A young GC must find every reference pointing *into* the young generation, so it
knows what is still alive. Some of those references live in the old generation.
But scanning the entire old generation to find them would defeat the whole
purpose of having generations.

So those **old → young** pointers must be tracked as they are created.

## 10. Write Barriers And Card Tables

The JVM tracks them with a **write barrier**: every reference field store
compiles to *extra machine code* that records "this old-generation region now
points somewhere young" into a **card table**. (G1 additionally maintains
per-region **remembered sets**.) At GC time those dirty cards are scanned as
extra roots.

The cost is small per store, and it is paid on **every reference store you ever
perform**.

## 11. The Inversion

Now the observation that turns the folk intuition inside out:

> An immutable object's fields are written exactly once, at construction. At that
> moment the object is the **youngest thing in the heap**, so every reference it
> holds necessarily points to something **older or equal**.

Therefore:

```text
   immutable data structures generate  YOUNG -> OLD  edges  ->  free
   mutable   data structures generate  OLD -> YOUNG  edges  ->  barrier + dirty card
```

Consider the "optimisation" everyone reaches for:

```scala
// Anti-pattern: an old-generation cache mutated to point at fresh objects.
cache.put(key, freshlyComputedValue) // old -> young store: barrier + dirty card
```

That single line pays a write barrier **and** extends the value's lifetime,
promoting into the old generation an object that would otherwise have died for
free in the nursery (Part II.7). The immutable version that "wastefully" rebuilds
a structure frequently costs strictly less at the collector level.

## 12. Structural Sharing Sharpens It Further

**Structural sharing** (Module 2) makes the argument stronger still. A persistent
tree update rewrites only the path from the changed node to the root, so a new
version allocates `O(log n)` fresh nodes — and every one of those fresh nodes
points *down* into the old, shared subtrees.

Young → old. Free. Every time.

---

# Part IV — Escape Analysis: When Allocation Costs *Nothing At All*

Part II said allocation is cheap. This Part says it can be **absent**.

## 13. The Idea

If the JIT compiler can prove that an object never leaves the method that created
it, then nobody outside can observe whether the object exists at all. And if
nobody can observe it, the compiler is free to **not create it**.

That proof is **escape analysis** (Choi et al., IBM, 1999), performed by C2 —
HotSpot's optimising JIT — after inlining.

## 14. The Three Escape States

For each allocation site, C2 computes how far the reference can travel:

| Escape state | Meaning | Optimisation unlocked |
| :--- | :--- | :--- |
| **NoEscape** | The object never leaves the compiled method | **Scalar replacement** — the allocation is deleted outright |
| **ArgEscape** | Passed to a callee but does not outlive the caller | Lock elision, partial optimisation |
| **GlobalEscape** | Stored to a field, returned, thrown, published to a thread | Nothing — a real heap allocation |

## 15. Scalar Replacement — The Prize

`-XX:+EliminateAllocations` (on by default) takes a `NoEscape` object and
**dismantles it**: its fields become ordinary JIT-managed values living in
registers or the stack frame, and the object never exists.

Not "allocated cheaply". **Never allocated.**

```scala
def sumNorms(xs: Array[Double], ys: Array[Double]): Double =
  // A Vec2 is constructed per element and immediately consumed.
  // The reference never escapes -> C2 deletes the allocation entirely.
  // Steady-state cost: zero bytes.
  loop(0, 0.0)
```

This is the mechanism that makes idiomatic FP viable in hot loops. Tuples,
`Option`s, small case classes and closures in tight code frequently cost nothing
once the JIT has warmed up. It is also the mechanism that gets your `Vec2` from
Part I.4.2 — 32 bytes each, one per element — down to zero.

## 16. What Destroys It — The Engineer's Checklist

Escape analysis is a *whole-compiled-method* analysis. It therefore dies whenever
inlining dies:

1. **Megamorphic call sites.** A virtual call whose receiver has seen ≥ 3 distinct
   implementations at that site cannot be inlined, so the callee stays opaque and
   its arguments must escape. This is why "code to an interface with many
   implementations" — excellent design advice in general — carries a real cost in
   hot loops.
2. **Inlining budget exhaustion.** HotSpot refuses to inline methods over
   `-XX:MaxInlineSize` (35 bytecodes) unless they are hot, and over
   `-XX:FreqInlineSize` (325 bytecodes) essentially ever. **Long methods are
   optimisation barriers.** Small methods are not merely more readable; they are
   faster.
3. **Storing the object anywhere durable** — a field, an array, a returned
   collection. One store into a global structure and the whole chain escapes.
4. **Cold code.** Escape analysis is a C2 optimisation. Interpreted and
   C1-compiled code allocates for real. Everything in this Part applies **only
   after warmup** — typically thousands of invocations under tiered compilation.

That last point is not a footnote. It means a benchmark that runs your loop a
hundred times measures a different program from the one that runs in production.

## 17. How To *Prove* It, Rather Than Believe It

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
measurement with escape analysis switched off:

```bash
sbt "set fundamentals/Test/javaOptions += \"-XX:-DoEscapeAnalysis\"" fundamentals/test
```

The difference between the two runs **is** the scalar replacement. Same code, one
variable changed: that is a controlled experiment, and it is how a scientist
establishes a claim rather than repeating one.

**Exercise 5 asks exactly this of you**, and the checklist requires you to record
both numbers.

---

# Part V — Boxing: The One Tax The Compiler Often Cannot Remove

## 18. Why An `Int` Becomes An Object

The JVM has no generics at runtime. `List[Int]` erases to a container of
`Object`, and an `Object` must be a real heap object — so every `Int` is wrapped
in a `java.lang.Integer`, the 16-byte object from Part I.4.2.

That is where the second half of the 10× tax in Part I.4.3 comes from: 24 bytes
of cons cell, plus 16 bytes of box, to store 4 bytes of payload.

## 19. The Trap: `Integer.valueOf` Caches −128..127

The JDK keeps 256 pre-built `Integer` instances covering −128 to 127 and hands
those out instead of allocating:

```text
   boxing the value    5   ->  returns a cached instance   ->  0 bytes allocated
   boxing the value  500   ->  allocates                   ->  16 bytes
```

**A micro-benchmark over small integers therefore shows no allocation and lies to
you about production behaviour.** Test with values outside the cache — Exercise 4
does, deliberately.

## 20. The Escape Hatches

- **`Array[Int]` is a genuine primitive array** — `int[]` at the bytecode level,
  4 bytes per element, contiguous and cache-friendly. It is the escape hatch when
  the 10× is unaffordable.
  Caveat: calling *generic* combinators on it (`arr.foldLeft`) re-introduces
  boxing through the generic signature, so an allocation-free traversal needs an
  explicit recursive walk over indices. Exercise 4 makes you feel this.
- **Scala 3 `opaque type`** builds zero-overhead domain wrappers over primitives:
  the type exists at compile time and vanishes at runtime. That is Module 4's
  subject; for now, note simply that the box is not always inevitable.

---

# Part VI — Collectors Under Churn

You now know what the collector charges for. This Part is about which collector
is doing the charging.

## 21. G1 — Region-Based, Pause-Target Driven (default on JDK 21)

G1 partitions the heap into ~2048 equal **regions** (1–32 MB each), dynamically
labelled Eden, Survivor, Old, or Humongous. Collection is **evacuation**: live
objects are copied out of a chosen collection set of regions, which are then
reclaimed wholesale. G1 chooses that set to meet `-XX:MaxGCPauseMillis` (default
200 ms) — it is a *soft-real-time, pause-target* collector.

Two failure modes you must be able to name:

- **Humongous allocations.** An object larger than **50% of a region** cannot
  live in Eden. It is allocated directly into contiguous Old-generation regions
  and reclaimed only by the concurrent cycle. A workload that repeatedly
  allocates large arrays or long strings degrades badly — note that this is a
  *size* pathology, not a rate one.
- **Evacuation failure / to-space exhaustion.** If no free region is available to
  copy survivors into, G1 must abort and fix up the heap in place — a long, ugly
  pause. This is what an over-tight heap under high promotion looks like.

## 22. ZGC — Concurrent, Colored Pointers, Sub-Millisecond Pauses

ZGC stores GC metadata **inside the pointer itself** (colored pointers) and
installs a **load barrier**: every reference read checks the colour bits and, if
stale, self-heals the reference. This lets ZGC **relocate objects while the
application is running**, so pause times become independent of heap size —
typically well under 1 ms on heaps of hundreds of gigabytes.

The trade-off is honest and worth stating plainly: those barriers cost
throughput, on the order of a few percent.

> **ZGC buys latency with throughput.**

On JDK 21, generational ZGC is available and strongly preferred for
allocation-heavy workloads:

```bash
-XX:+UseZGC -XX:+ZGenerational
```

## 23. The Selection Rule

| Workload | Collector | Reason |
| :--- | :--- | :--- |
| Batch / ETL, throughput is everything | Parallel GC | No concurrent-phase overhead |
| General service, balanced | **G1** | Sane default, predictable |
| Latency-critical, large heap, high churn | **Generational ZGC** | Pauses independent of heap size |

---

# Part VII — The Engineer's Cost Model

Everything above, translated into decisions you will actually defend in a design
review.

1. **Do not "optimise" by pooling objects.** Object pools convert cheap young
   garbage into expensive old-generation live data (Part II.7), add barrier
   traffic (Part III.10), and introduce lifecycle bugs. They are justified only
   for genuinely expensive *external* resources — connections, file descriptors,
   direct buffers — which is Module 11's subject.
2. **Suspect every cache.** A cache is a machine for defeating the generational
   hypothesis. Bound it, measure its hit rate, and know its retained size.
3. **Keep hot methods small.** Below the inline threshold a method is transparent
   to the optimiser and its allocations can vanish (Part IV.16). Above it,
   nothing is optimised across the boundary.
4. **Watch polymorphism in hot paths.** Bimorphic call sites inline; megamorphic
   ones do not. A `sealed trait` with three implementations behaves very
   differently from an open interface with twelve.
5. **Size the heap for the live set, not the allocation rate.** A larger nursery
   reduces GC *frequency* almost for free (Part II.8); a larger old generation
   only defers an expensive problem.
6. **Measure before you argue.** `-Xlog:gc` and an allocation counter settle in
   ten minutes what a design review would debate for an hour.

**Exercises 6, 8 and 9** are where this cost model becomes code: a realistic
word-frequency pipeline, a rendering path under an allocation ceiling, and a
benchmarking harness honest enough to show you your own warmup.

---

# Instrument Cheat Sheet

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
`com.sun.management.ThreadMXBean#getThreadAllocatedBytes` (Part IV.17);
flight-recorder profiling uses the `jdk.ObjectAllocationSample` event.

---

# Self-Check — You Have Not Finished This Module Until You Can Answer

Answer in prose, without running code, into `docs/checklist.md`.

1. Derive the heap cost of `final case class Pair(a: Int, b: Long)` from the
   layout rules of Part I.4.1, showing the header, the fields and the padding.
   Then do the same for an `Array[Long]` of 1,000 elements.

2. A service allocates 800 MB/s and shows 3 ms young pauses. Its allocation rate
   doubles to 1.6 GB/s and pause *duration* stays at 3 ms. Explain precisely why,
   and state what would have to change for the duration to grow.

3. Why does adding an object pool to a hot path frequently make p99 latency
   *worse*?

4. Why does an immutable data structure produce cheaper write-barrier traffic
   than a mutable one, given that it performs strictly more stores overall?

5. You measure zero allocation in a loop that visibly constructs a case class per
   iteration. Name the optimisation, the compiler tier that performs it, and two
   distinct code changes that would silently switch it off.

6. `List[Int]` of one million elements: give the heap cost and derive it from the
   object layout rules. Do the same for `Array[Int]`, and explain the ratio.

---

# Where To Go Next

| If you want | Read |
| :--- | :--- |
| The normative account of stack frames (Part I.3) | JVM Specification §2.5–2.6 |
| Why allocation is three instructions (Part II.5) | Shipilëv, *JVM Anatomy Quark #4* |
| What scalar replacement actually deletes (Part IV.15) | Shipilëv, *JVM Anatomy Quark #18* |
| The original escape analysis algorithm | Choi et al. (1999), *Escape Analysis for Java* |
| Practical G1 behaviour and tuning (Part VI.21) | Oaks, *Java Performance*, 2nd ed., Ch. 5–6 |
| How to benchmark without lying to yourself (E9) | Shipilëv, *Nanotrusting the Nanotime* |

Full annotated list, with a suggested reading order: [`references.md`](./references.md).
