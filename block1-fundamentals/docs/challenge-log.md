# Block 1 — Conceptual Challenge Log

The Step 4 audit of the module routine asks precise technical questions about
design choices, and the answers are where the understanding actually lives. A
green test suite proves the code works; it proves nothing about whether the
author can say *why*. This file is the record of those answers.

It is the evidence behind **§G, Oral Defence** in [`checklist.md`](checklist.md).
That box closes when every exercise has an entry here.

Every number below was executed and verified before being written — bytecode
read with `javap -c -p`, allocation taken from the `AllocationProbe` instrument
of Exercise 3, layout arithmetic cross-checked against the `Footprint`
implementation of Exercise 7.

The file spans the whole block: Module 1 holds challenges 1 to 15, Module 2
continues from 16 further down. Numbering is continuous so that any challenge can
be cited by number alone.

---

# Module 1 — JVM Semantics & Immutability Allocation Stress

| Exercise | Challenges | Status |
| :--- | :--- | :--- |
| E1 `Vec2` | 1 | recorded |
| E2 `Shape` | 2 | recorded |
| E3 `AllocationProbe` | 1 | recorded |
| E4 `Boxing` | 1 | recorded |
| E5 `Escape` | 1 | recorded |
| E6 `WordStats` | 2 | recorded |
| E7 `Footprint` | 1 | recorded |
| E8 `Csv` | 1 | recorded |
| E9 `Bench` | 1 | recorded |
| Carried over from the removed Self-Check | 4 | recorded |

---

## E1 — `Vec2`

### 1. For which inputs does `norm` return `Infinity` or `0.0` when the true answer is comfortably representable?

```scala
def norm: Double = Math.sqrt(v.dot(v))      // dot = x*x + y*y
```

The defect is not in the result. It is in the **intermediate**: `x * x`
overflows long before `sqrt(x*x + y*y)` would.

A `Double` reaches `1.7976931348623157e308`, so the square of anything past the
square root of that ceiling is gone:

```text
   sqrt(Double.MAX_VALUE)   =  1.3407807929942596e154
   t * t                    =  1.7976931348623155e308     still finite
   (t * 1.0000001)^2        =  Infinity                   overflowed
```

and it collapses to zero from below, at the square root of the smallest
subnormal:

```text
   sqrt(Double.MIN_VALUE)   =  2.2227587494850775e-162
   u * u                    =  4.9e-324                   still representable
   (u * 0.5)^2              =  0.0                        gone
```

So the two families are `|x|` or `|y|` above ~`1.34e154`, and both below
~`2.22e-162`. Between those bounds the computation is exact.

What makes this a defect rather than a limit of the type:

```text
   input                  Math.sqrt(x*x + y*y)     Math.hypot(x, y)
   --------------------   ----------------------   ----------------------
   (1e200,   1e200)       Infinity                 1.414213562373095e200
   (1e-200,  1e-200)      0.0                      1.414213562373095e-200
   (3.0,     4.0)         5.0                      5.0
```

The true norm of `(1e200, 1e200)` is `sqrt(2) x 1e200`, roughly `1.41e200` —
**one hundred and eight orders of magnitude below the ceiling**. The answer fit
with enormous room to spare; it was the route to it that overflowed.

`Math.hypot` exists for exactly this. It factors out the larger magnitude before
squaring anything:

```text
   hypot(x, y)  =  |x| * sqrt(1 + (y/x)^2)        with |x| >= |y|
```

The quotient `y/x` always lies in `[0, 1]` and can never overflow; the final
multiplication by `|x|` overflows only when the true result genuinely does not
fit.

**A third case, which no implementation can satisfy.** The Scaladoc promises the
result is *"non-negative for every input"*. For `Vec2(Double.NaN, 0.0)` both
`sqrt` and `hypot` return `NaN`, and `NaN >= 0.0` is `false`. The contract is
what needs a clause about `NaN`, not the code. This is the second time IEEE-754
has surfaced underneath this module — the first was `Double.valueOf` having no
cache, challenge 3 — and the annex backlog item covering it will have to be
written before Block 2 relies on equality laws.

**Decision: the code was changed.** `norm` is now `Math.hypot(v.x, v.y)`, the
Scaladoc records the divergence from the literal `sqrt(v.dot(v))` as deliberate,
and `Exercise1Vec2Spec` gained a test that pins both magnitudes with a relative
tolerance, keeping the naive form alongside as the control that shows what is
being prevented. The change carried a real risk: `Math.sqrt` compiles to one
machine instruction while `Math.hypot` is a Java method with branches and
rescaling, and `sumNorms` must stay under `-XX:FreqInlineSize` or Exercise 5
collapses. It survived — `sumNorms` still measures 48 bytes — which is why the
whole suite was run rather than just this exercise's.

---

## E2 — `Shape`

### 2. How many bytes per element does `totalArea` allocate?

```scala
def totalArea(shapes: List[Shape]): Double =
  shapes.foldLeft(0.0)((acc, s) => acc + s.area)
```

**24 bytes per element** — one `java.lang.Double` per fold step, and nothing
else. Measured against `Boxing.sumBoxed` in the same JVM run, over the same
100,000 elements:

```text
   totalArea over 100,000 shapes   =  2,400,048 bytes   ->  24 per element
   sumBoxed  over 100,000 ints     =  2,400,024 bytes   ->  24 per element
```

The first answer attempted was *32 bytes, the average size of a `Shape`*. The
layout arithmetic behind it is correct — a `Rectangle` or `Triangle` is
`align8(12 + 8 + 8)` = 32 and a `Circle` is `align8(12 + 8)` = 24 — but it
answers a different question. **The shapes already exist.** They arrive inside
the `List[Shape]` parameter, allocated by whoever built the list, long before
the probe takes its first reading. What `totalArea` allocates is what the *fold*
creates, not what the *data* cost.

The mechanism is erasure:

```scala
def foldLeft[B](z: B)(op: (B, A) => B): B
```

`B` is a type parameter, and type parameters are erased to `Object`. A `Double`
is not an `Object`, so every intermediate accumulator must become a heap object
to fit the signature — and since the previous one has been consumed, that is a
fresh object per step. Exercise 7 gives its size without measuring:

```text
   shallowSize(0, 0, 0, 1, 0)  =  align8(12 + 8)  =  24 bytes
```

`Boxing.sumPrimitive` escapes the same tax for the same reason in reverse: its
tail-recursive `loop` declares `acc: Long` concretely, so no erasure, no box.

Worth recording separately: **C2 did not scalar-replace the box**, even though
the combinator is small. The `s.area` call pattern-matches over a three-case
`enum`, and the boxed accumulator survives it. The prediction was made before
the measurement precisely because it could have gone the other way.

The test that produced these numbers is now part of the suite, in
`Exercise2ShapeSpec`. Its assertion is deliberately loose and deliberately a
comparison rather than a constant — whether a given JVM scalar-replaces a
generic fold's accumulator is a property of that JVM, and an experiment must not
prejudge its own result.

### 3. Both folds cost 24 bytes per element. Why is `totalArea`'s total 24 bytes higher?

Read the totals rather than the averages:

```text
   totalArea  =  2,400,048  =  100,000 x 24  +  48
   sumBoxed   =  2,400,024  =  100,000 x 24  +  24
```

The per-element term is identical and the probe residual is identical — both
functions return a primitive, which `measure`'s by-name parameter boxes on the
way out. The extra 24 is a **constant** term, so it cannot come from the 100,000
steps. It comes from the one thing that happens once: the seed.

`z: B` is erased exactly like the accumulator, so `foldLeft(0.0)` and
`foldLeft(0L)` both need a box before the first step. The asymmetry is not in
`foldLeft`; it is in what `valueOf` does with each. Scala boxes through
`BoxesRunTime.boxToDouble` and `boxToLong`, which call `java.lang.Double.valueOf`
and `java.lang.Long.valueOf`, and those two do not behave alike:

```text
   Long.valueOf(0L)      == Long.valueOf(0L)        ->  true     same instance
   Long.valueOf(128L)    == Long.valueOf(128L)      ->  false
   Integer.valueOf(0)    == Integer.valueOf(0)      ->  true
   Boolean.valueOf(true) == Boolean.valueOf(true)   ->  true

   Double.valueOf(0.0)   == Double.valueOf(0.0)     ->  false    always fresh
   Double.valueOf(1.0)   == Double.valueOf(1.0)     ->  false
   Float.valueOf(0.0f)   == Float.valueOf(0.0f)     ->  false
```

Part V.19 of the guide documents the -128..127 cache for `Integer`. What it does
not say is the part that bites here: that cache exists for **`Integer`, `Long`,
`Short`, `Byte`, `Character` and `Boolean`**, and **does not exist for `Double`
or `Float`**. Floating point has no small range of common values to cache, so
`valueOf` allocates unconditionally.

`0L` falls inside -128..127 and costs nothing. `0.0` has no cache to fall into
and costs 24 bytes.

The cleanest demonstration removes the fold entirely. On empty lists no step
runs and only the seed remains:

```text
   bytesOf(())                       =   0     control: BoxedUnit is a singleton
   bytesOf(Shape.totalArea(Nil))     =  48
   bytesOf(Boxing.sumBoxed(Nil))     =   0
```

The 48 is the seed box plus the probe boxing the returned `Double`. The absolute
zero on the other side is cached twice over: the seed `0L`, and the *result*,
which is also `0L` and also inside the cache.

Two sums over empty lists, mathematically identical, one costing 48 bytes and
the other costing literally nothing.

**Why it matters outside this exercise.** A metrics counter held as
`Map[String, Double]` and updated by a fold allocates a box per update and
reuses none; the same code over `Long` reuses the first 256. In a hot service
that is the difference between a cheap counter and one that fills Eden by
itself.

---

## E3 — `AllocationProbe`

### 4. Describe a reasonable `body` that allocates hundreds of megabytes and for which `measure` reports almost nothing.

```scala
def allocatedBytes: Long = bean.getCurrentThreadAllocatedBytes
```

**Current thread.** The counter measures what the *calling* thread allocated.
Every byte allocated on any other thread is invisible to it.

```scala
AllocationProbe.measure {
  val f = Future { List.fill(10_000_000)("x") }   // allocates on the pool
  Await.result(f, 1.minute)                       // the caller only waits
}
```

Hundreds of megabytes allocated; `measure` reports the cost of the `Future`, the
`Promise` and the wait — a few hundred bytes. No defect anywhere: the instrument
answered precisely the question it was asked, which was not the question being
posed.

The same holds for anything that leaves the thread: `ExecutorService`, `.par`,
`Arrays.parallelSort`, a `Cleaner`, and — the case that matters here —
**virtual threads**. Block 3 builds an effect runtime on `Thread.ofVirtual`, and
from that point every measurement taken through this probe is wrong by
construction rather than by accident.

**Two further limits of the instrument, neither expressed by its `Long` return
type.**

*It requires a forked JVM.* Run in the Scala REPL, the probe's initialiser
throws:

```text
   java.lang.ClassCastException:
     class com.sun.management.internal.HotSpotThreadImpl cannot be cast to
     class com.sun.management.ThreadMXBean
     (HotSpotThreadImpl is in module jdk.management of loader 'bootstrap';
      ThreadMXBean is in unnamed module of loader
      dotty.tools.repl.AbstractFileClassLoader)
```

Type identity on the JVM is the pair *(binary name, defining loader)*, not the
name alone. The REPL's loader supplies its own `ThreadMXBean`, so the cast
compares two distinct interfaces that happen to share a name. Under
`Test / fork := true` there is no REPL or sbt loader in the chain, the interface
comes from the bootstrap loader once, and the cast is trivially valid. Every
measurement in this module therefore depends on the fork that `build.sbt`
configures for an unrelated reason.

*It can return a negative count, which the Scaladoc forbids.* The contract says
*"the returned byte count must never be negative"*. Measured:

```text
   isThreadAllocatedMemorySupported   =  true
   isThreadAllocatedMemoryEnabled     =  true

   reading with measurement enabled   =  26,410,112
   reading with measurement disabled  =  -1
   delta (after - before)             =  -26,410,113
```

`getCurrentThreadAllocatedBytes` returns `-1` when per-thread allocation
measurement is disabled, and it is disabled at runtime through
`setThreadAllocatedMemoryEnabled(false)`. Should that happen between the two
readings, `measure` returns a large negative number. The code checks
`isThreadAllocatedMemoryEnabled` nowhere.

---

## E4 — `Boxing`

### 5. How many distinct values does the boxing test's input array contain?

```scala
val arr = Array.fill(100_000)(Random(Seed).between(1_000, 100_000))
```

**One.** The signature is the whole answer:

```scala
def fill[T: ClassTag](n: Int)(elem: => T): Array[T]
```

`elem` is **by-name**, so it is re-evaluated at all 100,000 positions — and what
is re-evaluated includes the `Random(Seed)`. Each iteration builds a *fresh*
generator from the *same* seed and asks it for its first number. Same seed, same
sequence, same first element, every time:

```text
   as written    :       1 distinct value, all equal to 15429
   as intended   :  62,957 distinct values

   (as intended:  val rng = Random(Seed)  outside, rng.between(...) inside)
```

The generator was constructed inside the parameter it was supposed to drive. The
cost is one value repeated a hundred thousand times, plus a hundred thousand
`Random` instances built and discarded during setup.

**The measurement survives it**, and the reason is worth recording:

```text
   15429 outside the Integer cache of -128..127   ->  true
   distinct box identities in arr.toList          ->  100,000
```

The value sits outside the cache, so `toList` boxes every element separately and
the 2,400,024 bytes recorded in §E are correct. The test passes for the right
reason, on a degenerate input — which is exactly the combination
[`error-patterns.md`](error-patterns.md) exists to make visible, and it is
recorded there as pattern 5.

The defect is in test code that shipped with the exercise set rather than in the
implementation. Note that the same file gets it right elsewhere: the correctness
test binds `val rng = Random(Seed)` outside the loop and calls `rng.between`
inside it. Both spellings live in one file.

---

## E5 — `Escape`

### 6. `sumNorms` is the zero-allocation control, yet it measures 48 bytes. Where do they come from?

Raised during the §E review rather than in a Step 4 round, and recorded here
because the answer changes what the §E baseline number means.

**Not one of the 48 bytes is a `Vec2`.** They are two unrelated 24-byte objects,
and only one of them belongs to `sumNorms` at all.

Four measurements, each taken after 20 warmup calls on the same inputs:

```text
   bytesOf(1.5)                                 =  24 bytes
   bytesOf(Escape.sumNorms(xs, ys))             =  48 bytes
   bytesOf(same function, constant message)     =  24 bytes
   bytesOf(same function, require removed)      =  24 bytes
```

The first line isolates the instrument. `AllocationProbe.measure[A](body: => A)`
takes its body by name, so a `Double`-valued body is boxed into a
`java.lang.Double` *between* the two readings of the counter. Exercise 7 gives
its size without measuring anything:

```text
   shallowSize(0, 0, 0, 1, 0)  =  align8(12 + 8)  =  24 bytes
```

That accounts for 24 of the 48 in every row above, including the rows where
`sumNorms` itself allocates nothing. The remaining 24 belong to the `require`,
and the bytecode says exactly which part of it:

```text
   public double sumNorms(double[], double[]);
       0: getstatic     #40   // scala/Predef$.MODULE$
       3: aload_1
       4: arraylength
       5: aload_2
       6: arraylength
       7: if_icmpne     14
      10: iconst_1
      11: goto          15
      14: iconst_0
      15: aload_1
      16: aload_2
      17: invokedynamic #58   // apply:([D[D)Lscala/Function0;     <-- here
      22: invokevirtual #62   // scala/Predef$.require:(ZLscala/Function0;)V
      25: ...
```

`Predef.require` is declared `require(requirement: Boolean, message: => Any)`.
The message is **by-name**, which on the JVM means a `Function0`. Read the
descriptor of the `invokedynamic` at offset 17: `([D[D)Lscala/Function0;`. It
takes both arrays as arguments, because the message interpolates them:

```scala
s"both input arrays must be of the same size: got ${xs.length} and ${ys.length}"
```

A lambda that captures is instantiated on every evaluation of the
`invokedynamic`; only a **non-capturing** lambda gets the cached singleton that
`LambdaMetafactory` installs in its call site. So a fresh two-reference object
is allocated on every call to `sumNorms`, whether or not the requirement holds —
and Exercise 7 predicts its size too:

```text
   shallowSize(2, 0, 0, 0, 0)  =  align8(12 + 4 + 4)  =  24 bytes
```

```text
   48  =  24  java.lang.Double   the probe boxing sumNorms' return value
       +  24  Function0          the captured message thunk, never invoked
       +   0  Vec2               200,000 of them, all scalar-replaced
```

Two consequences worth stating plainly.

**The cost is the interpolation, not the guard.** The row with a constant
message measures 24 bytes — identical to the row with no `require` at all. A
constant message captures nothing, so the thunk becomes a singleton and the
allocation disappears. `require` is free; `s"..."` inside it is not.

**The §E baseline ratio is understated.** With the closure gone, `sumNorms`
would measure 24 bytes against `collectVecs`' 10,398,016, and the recorded
216,625.33× would read 433,250.67×. The measurement in §E is honest; it was the
reading of it that was wrong — "48 bytes of residue from a scalar-replaced
loop" is not what the instrument reported.

**Decision: the code stays as written.** The diagnostic value of a message that
names both lengths is worth 24 bytes on a path that is not hot, and the entire
point of `sumNorms` — that escape analysis removes 6.4 MB of `Vec2` — is
unaffected: the disputed 24 bytes are constant in the input size, while the
6,400,000 bytes are not. What was missing was never the optimisation; it was
this entry.

---

## E6 — `WordStats`

### 7. `topN` sorts the whole map to return `n` entries. What does that cost, and is it defensible?

```scala
frequencies.toList.sortBy((s, n) => (-n, s)).take(n)
```

The obvious answer is `O(m log m)` where `O(m log n)` was available — a bounded
priority queue keeps only `n` candidates and never sorts the tail. That is
correct, and it is the less interesting half.

The interesting half is **how many times the key function runs**. Read the
definition:

```scala
def sortBy[B](f: A => B)(implicit ord: Ordering[B]): C = sorted(ord on f)
```

`ord on f` builds an `Ordering` whose `compare(x, y)` is
`ord.compare(f(x), f(y))`. The key is therefore recomputed **twice per
comparison**, not once per element. Counted on real maps built by
`wordFrequencies`:

```text
   entries    key calls    per entry    2 x m log2(m)
   -------    ---------    ---------    -------------
     1,000       17,448         17.4           19,932
    10,000      240,722         24.1          265,754
   100,000    3,069,586         30.7        3,321,928
```

The measured counts track `2 m log2 m`, as the definition predicts. And every
one of those calls constructs `(-n, s)` — a `Tuple2`, which is generic and
therefore boxes the `Int` as well:

```text
   Tuple2         shallowSize(2, 0, 0, 0, 0)  =  24 bytes
   Integer(-n)    shallowSize(0, 1, 0, 0, 0)  =  16 bytes
                                                 --
   per key call                                   40 bytes
```

Three million key calls to return the top ten of 100,000 entries is on the order
of 120 MB allocated. (That byte figure is derived from the two `shallowSize`
values above, which were measured; the product itself was not measured
directly.)

The standard fix is decorate–sort–undecorate: compute the key once per element,
sort the decorated pairs, drop the decoration. `m` key evaluations instead of
`2 m log m`.

**Verdict: defensible, at this scale.** A word-frequency map over a document has
thousands of entries, not millions, and one readable line is worth more than
three optimised ones. What is *not* defensible is writing the line without
knowing that `sortBy` recomputes — because it is the same line that appears in
code sorting a million records, where the constant stops being free.

### 8. The tie-break relies on `-n` reversing the order. For which `Int` does it not?

```text
   Int.MinValue   =  -2147483648
   -Int.MinValue  =  -2147483648
   equal?            true
```

`Int.MinValue`, and the reason is two's complement: the range `[-2^31, 2^31 - 1]`
is asymmetric. `+2^31` is not representable in 32 bits, so negating the most
negative value overflows back onto itself. This is the same fact `A1`'s
`TwosComplement.negate` was built on, derived there from `-x == ~x + 1` without
case analysis on the sign bit.

Had a count reached `Int.MinValue`, it would sort as the **smallest** key rather
than the largest: the most frequent word in the map would land at the end of the
list, and `take(n)` would return the rarest entries with no error anywhere.

**What guarantees it never arrives.** `wordFrequencies` starts each count at 1
and increments by 1, so a count is bounded by `words.length`. Reaching
`Int.MinValue` requires overflowing past `Int.MaxValue` — 2^31 occurrences of a
single word in one `List[String]`. That list needs 2^31 cons cells alone:

```text
   2^31 x shallowSize(2, 0, 0, 0, 0)  =  2,147,483,648 x 24  =  51.5 GB
```

before counting a single `String`. Under the suite's `-Xmx2g` it is impossible;
on any real heap, for a `List`, it is impossible.

Note what that means: **nothing in the type prevents it.** `Int` accepts
`Int.MinValue` happily. The heap prevents it. That is an *environmental*
invariant rather than a structural one, and environmental invariants are exactly
the kind worth writing into the Scaladoc — because they evaporate the day the
`List` becomes an `Iterator` over a 500 GB file.

---

## E7 — `Footprint`

### 9. `BooleanBytes` may be 0, 1, 2, 3 or 4 with the suite green. Design the assertion that pins it.

The technique comes straight out of pattern 4 of
[`error-patterns.md`](error-patterns.md): `align8` swallows any error smaller
than 8, so **multiply the constant until the error outlives the rounding**. A
`Boolean` is one byte, so eight of them suffice:

```text
   shallowSize(0, 0, 0, 0, 8)   =   align8(12 + 8 x BooleanBytes)

   BooleanBytes = 0   ->  align8(12)  =  16
   BooleanBytes = 1   ->  align8(20)  =  24      <- correct
   BooleanBytes = 2   ->  align8(28)  =  32
   BooleanBytes = 3   ->  align8(36)  =  40
   BooleanBytes = 4   ->  align8(44)  =  48
```

Every candidate now produces a different result, because the step between them
became 8 — exactly the granularity that was hiding them. One assertion does it:

```scala
assertEquals(Footprint.shallowSize(0, 0, 0, 0, 8), 24, "eight booleans: 12 + 8 -> 24")
```

Recomputing the latitude table with that assertion added to the suite:

```text
   constant           correct   before                    after
   ----------------   -------   -----------------------   ---------------------
   HeaderBytes             12   9, 10, 11, 12             9, 10, 11, 12
   ArrayHeaderBytes        16   13, 14, 15, 16            13, 14, 15, 16
   ReferenceBytes           4   3, 4, 5, 6                3, 4, 5, 6
   AlignmentBytes           8   8                         8
   IntegerBytes             4   4                         4
   LongBytes                8   5, 6, 7, 8, 9, 10, 11     5, 6, 7, 8, 9, 10, 11
   DoubleBytes              8   7, 8, 9, 10               7, 8, 9, 10
   BooleanBytes             1   0, 1, 2, 3, 4             1              <- pinned
```

It pins `BooleanBytes` alone and nothing else. The same recipe with the
multiplier adjusted pins the others: `shallowSize(0, 0, 2, 0, 0)` separates
`LongBytes`, and so on — each constant needs a coefficient large enough that a
one-unit error exceeds 8 bytes.

**With one exception, and it is structural.** `HeaderBytes` cannot be pinned by
any assertion on `shallowSize` whatsoever.

Proof: distinguishing `H` from `H - 1` requires `align8(H - 1 + S)` and
`align8(H + S)` to differ for some field sum `S`, which requires a multiple of 8
in the half-open interval `(H - 1 + S, H + S]`. But if `H + S` is itself that
multiple, then `H - 1 + S` rounds up *to* `H + S` as well. The two are equal for
every `S`.

`HeaderBytes` enters every expression with coefficient 1 and can never be
multiplied by anything. The model can observe its **residue class modulo 8**,
never its value. That is a limit of the instrument rather than of the suite, and
it belongs in a comment on the constant.

---

## E8 — `Csv`

### 10. `renderCsv` is linear, as required — but it allocates 9 bytes per character of output. Where do the other 8 go?

```scala
rows.map(_.mkString(","))   .mkString("\n")
//   \___ stage 1 ____/      \_ stage 2 _/
```

Measuring each stage in isolation, over the spec's 2,000 rows × 8 cells × 6
characters:

```text
   total    bytesOf(renderCsv)                 =  1,017,720
     stage 1   rows.map(_.mkString(","))       =    684,472    67%
     stage 2   lines.mkString("\n")            =    345,720    34%
```

The two stages sum slightly above the fused total because C2 shares a little
more when the whole expression is compiled as one; and this total differs from
the 1,049,968 recorded in §E by run-to-run variation, not by a change in shape.

**Stage 1 dominates.** Two thirds of the cost is building 2,000 intermediate row
strings that are entirely garbage: each is copied into stage 2's buffer and
discarded. Those intermediates total 110,000 characters — 98% of the final
output length, materialised once in order to be copied once more.

One row costs 312 bytes to produce 55 characters:

```text
   bytesOf(oneRow.mkString(","))  =  312 bytes      (55 characters of output)

   survives into `lines`:
      String (the object)     shallowSize(1, 1, 0, 0, 1)  =   24
      byte[55] (the content)  align8(16 + 55)             =   72
                                                             ---
                                                              96
   pure garbage, the mkString machinery:                  =  216
      scala.collection.mutable.StringBuilder  (the wrapper)
      java.lang.StringBuilder                 (the real one)
      the byte[] growth series:  16 -> 34 -> 70
      (the JDK grows by  newCapacity = (old << 1) + 2)
```

216 bytes of scaffolding for 96 bytes of result — and the result is garbage too,
one stage later. Stage 2 pays the same bill at scale: a single `StringBuilder`
growing to 111,999 bytes by doubling, whose whole allocation history sums to
roughly twice the final size, plus the output `String` and its own `byte[]`.

**The contract is met.** `O(output length)` holds: double the input and the cost
doubles rather than quadrupling, and the naive control allocated 113 MB against
this 1 MB. What the decomposition shows is that the **constant** is ~9, and that
it lives entirely in the intermediate materialisation.

**And it is close to optimal under the stated constraints.** The exercise forbids
an explicit `StringBuilder`, and without one a single pass is not expressible.
There is exactly one free improvement:

```text
   rows.map(...)           .mkString("\n")  =  1,017,720
   rows.iterator.map(...)  .mkString("\n")  =    969,760
   saving                                   =     47,960

   2,000 x shallowSize(2, 0, 0, 0, 0)       =     48,000
```

`iterator` never materialises the intermediate `List`, and what is saved is
exactly its 2,000 cons cells — the fifth time in this module that Exercise 7's
arithmetic predicts a measured number.

---

## E9 — `Bench`

### 11. `body()` is one call site shared by every benchmark. What does that degrade, and why does it make the numbers pessimistic?

```scala
Array.tabulate(n) { _ =>
  val start = System.nanoTime()
  body()                                 // one call site, JVM-wide
  System.nanoTime() - start
}
```

`body()` is `scala.Function0.apply`, an `invokeinterface`. HotSpot keeps a
**type profile** at every virtual call site with room for at most **two**
receiver types — `-XX:TypeProfileWidth=2` is the default — and what the JIT can
do with the site depends on how many concrete types have passed through it:

```text
   1 type    monomorphic   inline the body directly, behind a type guard
   2 types   bimorphic     inline both, behind a two-way guard
   3+ types  MEGAMORPHIC   inline nothing; dispatch through the itable per call
```

Every distinct lambda handed to `medianNanos` is another concrete type at that
one site. `Exercise9BenchSpec` already sends two — `() => work(10)` and
`() => work(1_000)` — so the site is bimorphic before any further benchmark
exists. The third one makes it megamorphic, **permanently**, for that JVM.

The consequence is not mainly the dispatch cost, which is small. It is that
**without inlining the body stops being optimisable in the context of the loop**:
nothing it allocates can be scalar-replaced, nothing can be constant-folded,
nothing can be vectorised. The `sumNorms` measured at 48 bytes in §E would
measure 6.4 MB if the site calling it could not inline it.

Hence the direction of the error: the harness reports numbers that are
**pessimistic**, because they include the cost of an optimisation the harness
itself prevented. The effect is also retroactive and asymmetric — the first
benchmark in a suite ran while the site was still monomorphic, the last did not,
so a comparison between two benchmarks in one run can be skewed by the order
they happen to execute in.

The name for this is **profile pollution**, and JMH's defence against it is the
`@Fork` annotation: each benchmark runs in a **fresh JVM**, with the profiles
reset, precisely so that the previous benchmark cannot contaminate the next.

Note the trap in this project's own build:

```scala
Test / fork := true
```

That forks **once for the whole suite**. It solves the problem it was written to
solve — sbt's own JVM contaminating the measurements — and does nothing about
this one. Inside that single JVM every benchmark still shares one `body()`.

---

## Carried over from the removed Self-Check

Four questions the Self-Check asked that no other artifact here covers. They were
recorded at the foot of [`checklist.md`](checklist.md) when that section was
dropped, specifically so that a Step 4 round would put them rather than lose them
with the section they came from.

### 12. Allocation doubles from 800 MB/s to 1.6 GB/s and the young pause stays at 3 ms. Why?

Because **a copying collector charges only for survivors** (Part II.7).

A young collection never visits garbage. It starts from the roots, copies what is
live out of Eden, and then declares the whole of Eden free in one stroke — a
pointer reset. Dead objects are not traced, not marked, not swept. The pause is
proportional to the **live set** and to nothing else.

```text
                          800 MB/s       1.6 GB/s
   --------------------   ------------   ------------
   Eden fills in          T              T/2
   collections/second     N              2N
   survivors/collection   S              S
   PAUSE DURATION         3 ms           3 ms
   total time in GC       3N ms/s        6N ms/s
```

The **frequency** doubled; the **duration** did not, because what the duration
measures did not. The bill arrived in throughput — twice as much time per second
inside the collector — and not in per-event latency.

**What would make the duration grow: the survivors growing.** Two routes, and the
second is the interesting one.

1. Objects living longer: a cache that retains, a buffer that accumulates, a
   larger in-flight working set because concurrency rose.
2. **The rate itself, if it climbs far enough.** With Eden filling in `T/2`,
   objects have had half as long to die before the collector arrives. With
   unchanged lifetimes the *fraction* still alive at collection time goes up. A
   pause that holds at 3 ms is therefore an implicit claim about the workload:
   that object lifetimes are much shorter than `T/2`. Keep doubling and that
   stops being true, promotion rate rises, and the duration grows — worse, it
   grows in the old generation, where collection is expensive.

In one line: **allocating more garbage is nearly free; allocating more survivors
is what costs.**

### 13. Why does an object pool frequently make p99 latency worse?

It is challenge 12 applied in bad faith. A pool exists to avoid allocation, and
what it avoids is the cheap part. Two distinct mechanisms.

**Mechanism 1 — the pool converts young garbage into old-to-young bookkeeping.**

Pooled objects are long-lived by construction, so they get promoted to the old
generation. Every time one is checked out and a freshly created object is stored
into one of its fields, the result is an **old to young** reference — precisely
the case the write barrier and card table exist to track (Part III.10).

```text
   without a pool:   young object  ->  young object      no barrier traffic
                                                         dies in Eden, cost zero

   with a pool:      OLD object    ->  young object      barrier fires
                                                         card dirtied
                                                         every young GC scans it
```

The pool did not remove work; it **moved** it — out of a region reclaimed by a
pointer decrement, into a structure that must be maintained and rescanned at
every young collection. And by challenge 12, the live set grew, so the pause grew
with it.

**Mechanism 2 — the pool must be thread-safe, and allocation already beat that.**

Allocating in Eden is a TLAB bump: each thread owns its slice, and allocation is
a compare, an add and a store — **three instructions, zero coordination between
threads** (Part II.5).

A shared pool is a shared mutable structure. Check-out and return go through a
CAS or a lock. Under load the CAS fails and retries; under more load it retries
more. p99 is exactly where those retries land — the median never notices. The hot
path traded a lock-free, thread-local operation for a global contention point, to
save something already cheaper than the CAS that replaced it.

*(A third, thrown in: bump-pointer allocation leaves consecutively allocated
objects contiguous. Pool objects are scattered across the old generation, and
every access is a potential cache miss.)*

### 14. Why is an immutable structure's write-barrier traffic cheaper, given that it performs strictly more stores?

Because the barrier does not charge per store. It charges per store **in one
direction**.

The card table answers exactly one question: *during a young collection, which
old objects point at young ones?* Without it, every young collection would have
to scan the entire old generation for roots. With it, only the dirty cards.

```text
   MUTABLE structure - update a field

      old object (already promoted)  .field  =  new object (young)
      -------------------------------------------------------------
      OLD -> YOUNG        barrier fires, card dirtied
      1 store, and it costs

   IMMUTABLE structure - rebuild the path

      new node (young)  ->  shared existing subtree (old)
      new node (young)  ->  new node (young)
      -------------------------------------------------------------
      YOUNG -> OLD        nothing to record
      YOUNG -> YOUNG      nothing to record
      log2(n) stores, and all of them are free
```

Young to old is free because a young collection already scans the whole of young
as part of its ordinary work. A reference leaving a young object will be found
regardless; there is nothing to note down.

The immutable structure never writes into an old object — it never writes into
any object that already existed. Every store target is a freshly allocated node,
therefore young, therefore outside the barrier's scope. That is how it performs
`log2(n)` stores and still generates less GC work than a single mutable
assignment.

The inversion in one line: **counting stores is the wrong metric; what is billed
is their direction.**

### 15. Zero allocation in a loop that visibly constructs a case class per iteration. Name the optimisation, the tier, and two code changes that silently disable it.

**The optimisation:** *scalar replacement*. The object is never allocated; its
fields become values in registers, as though the variables had been written out
by hand.

**What enables it:** *escape analysis*, proving the reference does not leave the
compiled scope — `NoEscape` (Part IV.14).

**The tier: C2**, the optimising compiler, tier 4. C1 — tiers 1 through 3 — does
**not** perform escape analysis. That is half the explanation of the 64x warm-up
speedup recorded in §E: the early iterations run interpreted and then
C1-compiled, allocating for real, and the allocation only disappears once C2
takes over.

**Two changes that switch it off in silence:**

1. **Grow the method past the inlining budget.** Escape analysis runs *after*
   inlining, over the resulting compilation unit. If the allocation site and all
   its uses do not end up in one unit, there is nothing to prove. Add code to the
   enclosing method until it passes `-XX:FreqInlineSize` (325 bytecodes for hot
   methods) or `-XX:MaxInlineSize` (35 otherwise) and inlining stops. No error,
   no warning, no red test — the allocation simply returns. This is the risk the
   `Math.hypot` change in Exercise 1 ran, which is why the whole suite was
   measured rather than that one spec.

2. **Make the consuming call site megamorphic.** Challenge 11 again: three or
   more receiver types at one virtual site and C2 stops inlining; without
   inlining the object crosses into a callee the analysis cannot see, and
   escaping into an unknown callee is escaping. Note the shape of this one — the
   offending change can live in **another file**, written by someone else, who
   merely passed a new lambda to a shared utility.

**Two more worth knowing:**

3. **Store the reference anywhere observable** — a field, an array, a collection,
   the return value. This is literally `collectVecs`: the same arithmetic, the
   same construction, and 10.4 MB because the references leave the method.
4. **Ask for the object's identity hash code**, directly or otherwise. The
   identity hash lives in the mark word, and the mark word exists only if the
   object does. Asking for it forces materialisation.

**What the four have in common:** all compile cleanly under `-Wall -Werror`, all
pass the entire suite, and none produces a warning, a log line or an exception.
The only symptom is a change in the allocation counter — which is why §E demands
the measured number instead of the claim. An optimisation whose only evidence is
a counter is an optimisation that can only be defended with a counter.

---

# Module 2 — Manual Persistent Data Structures

Numbering continues from 15. Module 1's challenges are above; the discipline is
the same — the derivation, the measurement or the listing, never the verdict
alone.

| Exercise | Challenges | Status |
| :--- | :--- | :--- |
| E6 `MyTree.fromRange` | 2 | recorded |
| E8 `SharingProof` | 3 | recorded |
| E9 `Balance` | 1 | recorded |

---

## E6 — `MyTree.fromRange`

The question arrived from the other direction for once: the Scaladoc asserts that
`fromRange(0, 15)` has depth 4, and I challenged the assertion, having computed
8. The reply was that the midpoint of 0 and 15 is 7 or 8, that one side then
holds 7 values and the other 6, and that `max(1 + 7, 6 + 1) = 8`.

Two things were wrong with that, and only the second matters.

The interval is half-open: `fromRange(0, 15)` covers `0 until 15` = {0..14}, so
the root is `(0 + 15) / 2 = 7` and both halves hold seven values, not seven and
six. Arithmetic, quickly repaired.

The substantive error is that `7` was placed where the recurrence requires a
*depth*, not a *count*. A subtree of seven elements has depth 7 only if it is a
chain — and `fromRange` recurses on the halves, so the half is balanced too:

```text
D(0) = 0
D(n) = 1 + D((n - 1) / 2)

n =  1  ->  1 + D(0) = 1
n =  3  ->  1 + D(1) = 2
n =  7  ->  1 + D(3) = 3
n = 15  ->  1 + D(7) = 4
```

```text
level 1                     7
level 2           3                  11
level 3       1       5          9       13
level 4     0   2   4   6      8  10   12  14
```

The closed form is `D(n) = ceil(log2(n + 1))`, which is exactly what
`Sharing.balancedDepth` must return — and it is why the Scaladoc chose 15 rather
than 16 as its example: `15 = 2^4 - 1` is the largest tree of depth 4, so the
example pins the bound at its tight point.

The interesting part is what the wrong answer turned out to be right about. The
implementation standing at the time chose the midpoint **once**, for the root,
and inserted the remaining values with `foldLeft`:

```text
size  = 15
depth = 8
Branch(7,
  Branch(0,Leaf,Branch(1,Leaf,Branch(2,Leaf,Branch(3,Leaf,
    Branch(4,Leaf,Branch(5,Leaf,Branch(6,Leaf,Leaf))))))),
  Branch(8,Leaf,Branch(9,Leaf,Branch(10,Leaf,Branch(11,Leaf,
    Branch(12,Leaf,Branch(13,Leaf,Branch(14,Leaf,Leaf)))))))
```

`max(1 + 7, 1 + 7) = 8`. The predicted 8 described the tree that existed, not the
tree the contract demanded: the values arrive ascending, each is greater than
everything already placed, and each becomes the right child of the last. It is
Exercise 9's degeneracy, reached inside the function whose whole purpose is to
avoid it. Recorded as occurrence 1 of
[`error-patterns.md`](error-patterns.md) pattern 9.

### 16. Reverse the two operands of `#:::` in `midPoints`. The same fifteen values are emitted. What depth results, and why?

The repaired implementation generates the midpoints recursively into a
`LazyList` and still folds them through `insert`. The line carrying the whole
guarantee is the concatenation:

```scala
mid #:: (midPoints(lo, mid - 1) #::: midPoints(mid + 1, hi))
```

**The answer given: depth 15, a strictly ascending chain.** Correct. Measured,
with the two orders side by side over `0 until 15`:

```text
concatenation                                   sequence emitted                   size   depth
---------------------------------------------   --------------------------------   ----   -----
mids(lo,mid-1) #::: (mid #:: mids(mid+1,hi))    0,1,2,3,4,5,6,7,8,9,10,11,12,13,14   15      15
mid #:: (mids(lo,mid-1) #::: mids(mid+1,hi))    7,3,1,0,2,5,4,6,11,9,8,10,13,12,14   15       4
```

The in-order concatenation emits the sorted sequence — that is what in-order
traversal of a search tree *is* — and `insert` on a sorted sequence produces the
chain. The set of values is identical, the multiset is identical, and the depth
differs by a factor of nearly four. Nothing about the swap is visible at the call
site.

The parentheses deserve a line of their own, because the line was first written
without any:

```text
as first written                                 parses as
----------------------------------------------   ------------------------------------------------
mid #:: mids(lo,mid-1) #::: mids(mid+1,hi)       mid #:: (mids(lo,mid-1) #::: mids(mid+1,hi))
```

Both `#::` and `#:::` end in a colon, so both are **right**-associative, and
equal precedence groups to the right. The grouping is therefore not the one the
eye reads left to right — and here it happens not to matter, because
`mid #:: (l #::: r)` and `(mid #:: l) #::: r` denote the same sequence. That is
luck rather than design: the two spellings coincide only because `mid` sits at
the front of both, and nothing in the expression says so. The parse is now
declared in the source, which costs one pair of parentheses and removes the need
to know the rule.

### 17. Is the pre-order version correct by luck or by construction? Name the property that guarantees the balance.

**The answer given: by construction** — the midpoint is emitted first, then all
midpoints below it, then all midpoints above it, and the same holds recursively
inside each half. That is exactly right, and the property has a name.

The emission is a **pre-order traversal**: every node is emitted before any value
in either of its subtrees. That is the whole guarantee, in one sentence:

> When `insert` reaches a value, the node that must become its parent has already
> been placed, so the value lands where `midPoints` decided it should.

The general statement is that inserting the pre-order sequence of a binary search
tree into an empty tree, with an `insert` that performs no rebalancing,
reconstructs that tree exactly. It is the reason pre-order is the traversal a BST
is serialised in when the shape must survive the round trip; in-order
serialisation preserves the *contents* and loses the *shape*, which is the
15-versus-4 above.

One correction to the reasoning as offered: the `LazyList`'s laziness plays no
part in the guarantee. `foldLeft` forces the sequence in full, and the identical
code over a strict `List` produces the identical tree. Laziness changes *when*
the elements are produced; the balance depends only on the *order*, which is
fixed by the concatenation. The two are worth keeping apart, because a change
that preserves laziness while altering the order — the operand swap of challenge
16 — destroys the invariant without touching the evaluation strategy.

### What the correct version still costs

Not a challenge, but the measurement belongs with them. `midPoints` computes the
shape — it picks a midpoint, it recurses on the halves — then flattens it into a
sequence, and `insert` rediscovers by comparison the position each value was
already assigned. Measured at a million values, median of five runs after warm-up:

```text
fromRange(0, 1_000_000)            Branch allocations   median
--------------------------------   ------------------   --------
Branch assembled on the recursion           1,000,000     4.4 ms
midPoints + foldLeft(insert)               18,951,445   493.1 ms
```

Both trees have depth 20 and a million nodes. The difference is that `insert`
rebuilds every node on the path it walks, so the construction costs the tree's
**internal path length**, `O(n log n)`, where direct assembly costs its **size**,
`O(n)`. Recorded as occurrence 3 of [`error-patterns.md`](error-patterns.md)
pattern 9.

---

## E8 — `SharingProof`

### 18. The three list measurements build with `scala.List` and only the tree measurement uses your structure. Given that both cells are 24 bytes, does the difference matter?

The question arrived as a question rather than a challenge — *"is it the default
API or my `MyList`?"* — which is the better version of it, because the answer was
not visible from the numbers.

It was the default API, and the tree was not:

```scala
val ls = List.from(0 until n)      // scala.collection.immutable.List
bytesOf(ls.prepended(n + 1))
...
val t = MyTree.fromRange(0, n)     // MyTree
bytesOf(t.insert(n + 1))
```

Within one exercise the two halves disagreed, and that asymmetry is the tell.

**Why the sizes could not decide it.** They are identical, and identical for a
reason that has nothing to do with either structure being right:

```text
                          header   fields         raw   aligned
-----------------------   ------   ------------   ---   -------
scala.::                      12    2 refs = 8     20        24
MyList.Cons                   12    2 refs = 8     20        24
```

So `prepend` measured 40 either way and `reverse` measured 2,400,000 either way.
A suite that is green while measuring the wrong structure proves the layout of
`scala.::`, and Exercise 1 made no predictions about `scala.::`.

**What did decide it** is that one of the three operations is implemented
differently on the two structures, and the measurement separates them:

```text
                     scala.List    MyList
------------------   -----------   ---------
append, n = 100,000    2,400,120   4,800,040
```

`scala.List.appended` reaches `n` cells by building through a mutable
`ListBuffer`. `MyList.appended` cannot: §D forbids the buffer. The two agree on
everything whose cost is decided by *shape* and diverge on the one thing whose
cost is decided by *algorithm* — which is exactly the reach of the coincidence,
and is recorded as occurrence 2 of [`error-patterns.md`](error-patterns.md)
pattern 2.

### 19. Count the cells `appended` allocates for a list of `n`. Compare with `appendCells(n) = n`. Which side is wrong?

**Answered unaided: 2n** — *"reverse + n iterations"* — and the measurement
confirms it to the byte. `appended` delegates to `concat`, and `concat` opens
with `loop(xs.reverse)`:

```text
step                                   cells
------------------------------------   ---------
Cons(x, Nil)                                   1
xs.reverse, discarded on the way out   n = 100,000
loop, prepending each onto the result  n = 100,000
                                       ---------
                                         200,001
```

```text
200,001 x 24                =  4,800,024
+ one boxed java.lang.Integer      +  16
                               ---------
measured                        4,800,040        exactly
```

**Neither side is wrong**, which is the answer the question was fishing for.
They are different quantities:

* `appendCells(n) = n` counts the cells the **result keeps**. True: the returned
  list holds `n` copies of the old spine, and nothing in it is garbage.
* `AllocationProbe` counts the bytes the thread **spent**. A `@tailrec` `concat`
  has to walk `xs` forwards and emit backwards, so it materialises an
  intermediate spine and then consumes it.

The two coincide only when an operation produces no garbage, and `reverse` is
the proof: it measures `n x 24` on the nose, because it has no temporary.

**Why the constant is not an inefficiency to remove.** Three routes reach an
append, and the module's rules admit one of them:

```text
route                        cells allocated   fails how
--------------------------   ---------------   -------------------------------
non-tail recursion                         n   one frame per cell; overflows at
                                               the sizes this module measures
mutable builder (ListBuffer)               n   what scala.List does; forbidden
                                               by §D of the checklist
@tailrec, via reverse                 2n + 1   nothing — it is the price
```

So the factor of two is the price of the constraint, not a defect. The model was
split rather than corrected: `appendCells` kept its meaning and
`appendAllocatedCells` was added beside it, written as
`appendCells(n) + n + 1` so that the three terms name themselves — the rebuilt
spine, the discarded spine, the cell for `x`.

One property of that spelling worth keeping: it survives the overflow guard at
`n = 2,000,000,000` only because `appendCells` returns `Long`, so the whole
expression promotes before it doubles. Written `2 * n + 1` in `Int` it wraps
negative, and the suite asserts against exactly that.

### 20. The exercise's Scaladoc asks you to subtract a floor and you subtracted none. Was anything owed?

Raised from the other side again — *"não desconsiderei nenhum piso"* — and the
answer has two halves, one reassuring and one not.

**The floor the Scaladoc means was zero, and skipping it was correct.**
`AllocationProbe.measure[A]` boxes its result when `A` is a primitive, because
the by-name parameter erases to `Function0[Object]`. Every body in
`SharingProof` returns a reference — a `MyList`, a `MyTree` — so nothing is
boxed on the way out. That is a fact about the *return type* and it is worth
stating in the Scaladoc, because the first measurement that returns an `Int`
pays 16 bytes silently.

**A different quantity was in the window, and it was owed a subtraction.** Every
measurement that introduces an element introduces an `Int` into a structure
whose `A` is erased, so the element is boxed *inside* the measured region:

```text
operation      model       measured    gap
------------   ---------   ---------   ----
prepend               24          40    +16
tree insert          504         520    +16
reverse        2,400,000   2,400,000      0
```

`100,001` and `1,048,576` are both above the `Integer` cache (`-128..127`), so
each costs one `java.lang.Integer`: header 12 + `int` 4 = 16, already aligned.
`reverse` introduces no element and pays nothing, which is what identifies the
culprit rather than merely tolerating it.

The model says explicitly that this is not its business —
`Sharing.reverseCells`: *"Note what is not allocated: the elements"* — so the box
belongs outside the comparison. With it removed, three assertions stopped being
ceilings:

```text
                  before            after
---------------   ---------------   -----------------------
prepend           <= 48 (got 40)    == 24 (got 24)
tree insert       +- 3 nodes        +- 1 node (got 504)
append            within 10%        within 1%
sharing ratio     48,396 x          49,932 x, model 49,932 x
```

The last line is the cost of leaving it: the ratio §E of the checklist asks for
was wrong by 3%, and no assertion in the suite was tight enough to say so.
Recorded as pattern 10 of [`error-patterns.md`](error-patterns.md).

---

## E9 — `Balance`

### 21. Your degeneration factor diverges from the spec by 7.7%. Where does the missing `+1` come from, and why does it move one side of the ratio and not the other?

```scala
fromSorted(n).depth / fromBalanced(n).depth.toDouble      // 4096 / 13 = 315.077
```

An `insert` does not allocate `depth` nodes. It allocates `depth + 1`: every node
on the path is rebuilt, **and** a leaf is created. The module already said so,
four lines from the function that got it wrong —
`Sharing.treeInsertNodes = balancedDepth(n) + 1L`, whose Scaladoc reads *"the
count therefore exceeds the depth by exactly one"*. It is also where Exercise 8's
504 bytes came from: 21 nodes at 24, for a depth of 20.

```text
                    numerator   denominator    factor    against the spec
-----------------   ---------   -----------   -------   ----------------
depths only              4096            13   315.077          +7.67 %
numerator +1 only        4097            13   315.154          +7.70 %
denominator +1 only      4096            14   292.571          -0.02 %
both, correct            4097            14   292.643            0.00 %
```

The third row is the answer. **The `+1` is invisible against 4096 and worth 7.1%
against 13**, so the ratio is decided entirely by the balanced side — the side
whose smallness is the property under test. An additive constant is negligible
exactly where the quantity is large, which in a comparison between a degenerate
structure and a balanced one is never the side that matters.

Measured, after the fixture was repaired (see below), on JDK 21.0.9 HotSpot,
forked:

```text
                              bytes    box off     nodes
---------------------------   -------  ---------   -----
sorted.insert(4096)            98,344     98,328    4097
balanced.insert(4096)             352        336      14

98,328 / 336  =  292.6429
  4,097 /  14  =  292.6429        deviation 0.0000 %
```

### What the fixture measured instead

Not a challenge — a defect in the spec, found while confirming the answer above,
and worth recording because the failure it would have produced named the wrong
file.

The test probed both trees with `insert(-1)`. `fromSorted` builds a spine to the
right, so a value below the minimum is the *cheapest* insert that tree admits: it
is less than the root, the root's left child is empty, and the walk stops.

```text
probe    sorted (bytes / nodes)   balanced (bytes / nodes)    factor
------   ----------------------   -------------------------   ------
-1                    48 /    2                 312 /    13     0.15
4096              98,344 / 4097.7                352 / 14.7    292.6
```

The degenerate tree measured six times *cheaper* than the balanced one, in a test
whose subject is that it is three hundred times more expensive. And because `-1`
sits inside the `Integer` cache, that probe also allocated no box — which is why
its two rows are exact multiples of 24 and hid challenge 20 on the same path.

The assertion standing at the time, `measured > predicted / 3.0`, would have
failed on the corrected formula with the message *"the insert is not copying the
path it walks"* — pointing at `MyTree.insert`, which was correct throughout. The
spec now probes above the maximum, subtracts the box, asserts the *direction*
before the magnitude, and compares against the model within 5%. Recorded as
pattern 11 of [`error-patterns.md`](error-patterns.md).
---

## E4 — `Folds` and `concat`

### 22. `concat`'s Scaladoc says the cost is `xs.length`. Exercise 8 measures 200,001 cells for the same call. Which is wrong?

**Answered unaided, and with the mechanism rather than the verdict:** *"A está
errada, `concat` aloca `2n` porque reverte antes."* The mechanism is exactly
right, and the refinement is about where the defect sits.

The number in the sentence is not wrong. `concat` produces `n` new cells chained
onto a shared `ys`, and those `n` are what the result keeps. The other `n`, from
the reversal, are garbage before the method returns.

```text
concat(ys) over an xs of n cells

  xs.reverse          n cells      built, walked, discarded
  loop prepends       n cells      the result's own spine
                     ---------
  allocated          2n
  retained            n            <- what the sentence describes, correctly

appended(x) = concat(Cons(x, Nil))

  Cons(x, Nil)        1 cell       allocated by appended, not by concat
  concat             2n
                     ---------
  total              2n + 1        = 200,001 at n = 100,000
  measured                           200,001 cells (4,800,024 bytes / 24)
```

**The defect is the verb.** The sentence read *"**Allocates** one cell per
element of `xs`"*. Replace `Allocates` with `retains` and it is correct word for
word. It is the same split `Sharing` separates into two functions on purpose —
`appendCells` against `appendAllocatedCells` — and it was missing from the one
function where the second spine is actually created.

Recorded as a fifth occurrence of pattern 10 in
[`error-patterns.md`](error-patterns.md), and it is the first of that pattern's
occurrences to live in a comment rather than in an assertion: nothing in the
suite reads a Scaladoc, so no tolerance was too wide — there was no comparison
at all. The contract is now repaired to state both numbers and to say which
forces which.

---

## E6 %s `MyTree.contains`

### 23. `contains` is documented `O(depth)`. On a balanced and a degenerate tree of the same 4,096 values, how many `Branch` nodes does `contains(-1)` visit?

**Attempted: "2048 x 4096".** Both halves wrong, and the measurement is worth
more than the correction because the implementation does not do what its
Scaladoc says.

```scala
case Branch(v, l, r) => (v == x) || l.contains(x) || r.contains(x)
```

It never compares. It tests equality and descends into **both** subtrees: a
generic tree search wearing a binary search's signature. Instrumented with a
counting `Ordering` and a counting `equals`:

```text
                      depth   contains(-1)   Ordering consulted
-------------------   -----   ------------   ------------------
sorted/degenerate      4,096     4,096 nodes                   0
balanced                  13     4,096 nodes                   0
```

Identical, `O(n)` in both, and the `(using Ordering[A])` parameter is requested
and never called %s not once. The BST invariant is paid for by every `insert`
and then read by nobody.

Three consequences, and the first is the expensive one:

* the balanced-versus-degenerate distinction that the whole of Exercise 9 exists
  to measure is **invisible** to `contains`;
* an unused `given` is not reported by `-Werror` the way an unused parameter is,
  so the tool that catches `Sharing.prependCells` cannot catch this;
* `Exercise6MyTreeSpec` asserts that `contains` returns the right booleans, and
  it does. No assertion in the suite mentions a cost.

The `2048` in the attempt is a real number in this system, for a different
question: for a value that is **present** the `||` short-circuits on the hit, so
`contains(2048)` costs 2,049 on the degenerate tree. For an absent value there
is nothing to short-circuit and the whole tree is walked.

### What a corrected `contains` costs, and why the probe decides it

```text
probe                    sorted/degenerate   balanced
----------------------   -----------------   --------
-1   (below the min)                     1         12
4096 (above the max)                 4,096         13
2048 (present)                       2,049         12
```

**On the degenerate tree `contains(-1)` visits one node.** `fromSorted` builds a
spine to the right, so a value below the minimum is less than the root, the
root's left child is `Leaf`, and the search stops. Pattern 11 again, in a second
function: the probe that looks like the worst case is the best case. The
predicted `4,096` is the right number from the wrong row %s it is the cost of
probing *above* the maximum.

And the balanced column is 12, not 13. `depth` is the **maximum** over
root-to-leaf paths, not the length of every path; the leftmost path bottoms out
one level early. `depth` bounds `contains` from above and does not predict it.

What the table actually demonstrates is sharper than "degenerate is slower": the
degenerate column ranges from 1 to 4,096 depending on the argument, while the
balanced column stays between 12 and 13 **for every argument**. The defect of a
degenerate tree is not that it is always worse. It is that its cost is a
function of the query, and its worst case is the size of the structure.

### The repair, and what it measures

Made in Exercise 6, mirroring `insert` exactly %s same guards, same trichotomy:

```scala
def contains(x: A)(using Ordering[A]): Boolean = t match
  case Leaf => false
  case Branch(v, l, _) if x < v => l.contains(x)
  case Branch(v, _, r) if x > v => r.contains(x)
  case _ => true
```

Note what it does *not* use: `==`. Equality now comes from the same `Ordering`
that placed the value, so a type whose `equals` disagrees with its ordering
cannot make `contains` disagree with `insert`.

Verified against the predicted costs, counting `Ordering.compare` calls:

```text
probe                    sorted/degenerate            balanced
                         compares    nodes       compares    nodes
----------------------   --------   ------       --------   ------
-1   (below the min)            1        1             12       12
4096 (above the max)        8,192    4,096             26       13
2048 (present)              4,098    2,049             14       12
```

The nodes column is derived, not measured, and the derivation is the caution:
a node costs **one** compare when the search goes left (`x < v` is true) and
**two** when it goes right or stops (`x < v` false, then `x > v`). Dividing
compares by a fixed factor is therefore only valid where every turn goes the
same way %s true of the first two rows and false of the third. The balanced path
to 2,048 is 10 left turns, 1 right turn and the final node:
`10 + 2 + 2 = 14`, over 12 nodes. A first pass that divided by two reported 7
and was wrong for exactly that reason.

One assertion is still owed. The suite passes on both the old implementation and
the new one, because every test asks what `contains` returns and none asks what
it costs. Until a test separates them, the repair is protected by nothing.

---

## E2 %s `MyList` and variance

### 24. Delete the `+` from `MyList[+A]`. Which line does the compiler reject first, what does it say, and what would an invariant `MyList` need instead?

Contracted from the start: the enum's own Scaladoc says *"delete the `+`,
compile, and record which line the compiler rejects first %s that error is the
whole content of Part VII.26, and it is asked again in §G."*

**Answered unaided, and correctly in all three parts.** The compiler rejects
`Nil`; `Nil` is `MyList[Nothing]` by construction and invariance denies it every
other element type; so the empty list must become per-type, declared
`case Nil[A]() extends MyList[A]`.

Run, on `enum MyList[A]`:

```text
27 |  case Nil
   |  ^^^^^^^^
   |  cannot determine type argument for enum parent class MyList,
   |  type parameter type A is invariant
```

One error, and the diagnosis is the answer restated by the compiler. The
`extends MyList[A]` is not optional either %s writing `case Nil[A]()` alone is
rejected with *"explicit extends clause needed because both enum case and enum
class have type parameters"*.

### What the repair costs

It type-checks, and it breaks five use sites: a bare `Nil` now names the case
class's companion object rather than a value, so every `case Nil =>` pattern and
every `= Nil` default becomes `Nil()`. That is inconvenience, not cost.

The cost is that the empty list stops being free.

```text
Cov.Nil eq Cov.Nil                = true
Inv.Nil[Int]() eq Inv.Nil[Int]()  = false

100,000 empty lists
  covariant, parameterless case              0 bytes    0.00 per instance
  invariant, Nil[A]()                1,600,072 bytes   16.00 per instance
```

A parameterless enum case compiles to a **single value**; a parameterised one
compiles to a case class, and `Nil[A]()` allocates on every call. Covariance is
not type-level convenience here %s it is what makes the empty list cost nothing,
on the most frequently constructed value in the whole structure: the seed of
every fold, the default accumulator of every `loop`, the terminator of every
list.

The 16 bytes agree with Module 1's layout model exactly:
`Footprint.shallowSize(0, 0, 0, 0, 0)` is `align(12)` = 16, and `javap` confirms
the class has no fields at all.

### An instrument note: why the first measurement said 24

The first run measured 24 bytes per instance, against a model predicting 16. The
enum had been declared *inside* the test class, and `javap` names the difference:

```text
class TempLayoutSpec$InnerInv$Nil
  private final TempLayoutSpec$InnerInv$ $outer;      // the companion
class TempLayoutSpec$InnerInv
  private final TempLayoutSpec $outer;                // the enclosing instance

top-level   12 header + 0 fields      = 12  ->  align 16     measured 16
nested      12 header + 2 refs x 4    = 20  ->  align 24     measured 24
```

Two enclosing references, one per level of nesting, and both are invisible in
the source. A nested enum costs 50%% more per instance than the same declaration
at the top level %s worth knowing independently of this challenge, and a reminder
that a measurement of a *declaration* is sensitive to where the declaration
sits.
