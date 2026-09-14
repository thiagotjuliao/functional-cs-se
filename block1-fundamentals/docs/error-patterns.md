# Block 1 — Error Patterns

The recall set asks whether the mechanism is understood. The challenge log asks
whether it can be derived. Neither asks the question this file asks:

> **Where does composition go wrong, once the mechanism is already known?**

Every entry below was a real defect written while working this block. None of
them is ignorance of a mechanism. `LongBytes = 16` was written by someone who
knows a `Long` is 64 bits; the error is in the conversion, not in the knowledge.

That is why this file is organised by **pattern** rather than by exercise.
Twenty-two individual mistakes are a diary and nobody rereads a diary. Eleven
recurring shapes are a review checklist.

Each entry carries four things: what the pattern is, the occurrences that
instantiated it, the rule that prevents it, and — the field that makes this
usable — **why the compiler and the test suite do not catch it**. Every pattern
but the first compiles cleanly under `-Wall -Werror` in all its occurrences.

| # | Pattern | Occurrences | Caught by the build? |
| :-- | :--- | :-: | :--- |
| 1 | Bit-to-byte conversion written from memory | 3 | two of three |
| 2 | Two quantities that coincide under the current configuration | 2 | no — a test passed for the wrong reason |
| 3 | Off-by-one in a limit | 1 | no — the boundary has no call site |
| 4 | A constant is only as tested as the arithmetic that exposes it | 2 confirmed, 6 latent | no — one latent since pinned, five documented |
| 5 | A generator built inside the by-name parameter it should drive | 1 | no — a test passed on a degenerate input |
| 6 | A contract no implementation of that signature can satisfy | 3 | no — contracts are prose, and one of them is a checklist clause |
| 7 | A unit declared in the name and nowhere the machine reads | 1 | no — both sides of the confusion are `Long` |
| 8 | An exact integer answer routed through `Double` | 2 | no — the suite stopped two powers of two short |
| 9 | A structural guarantee carried by traversal order instead of by construction | 3 | the shape yes, the price no |
| 10 | A quantity compared against a model of a neighbouring quantity | 4 | no — the tolerances were wide enough to swallow the gap |
| 11 | A fixture that cannot exhibit the property under test | 1 | no — and the failure it finally produced blamed the wrong file |
| 12 | A measurement recorded where nothing can re-run it | 3 | no — two of the three were wrong when measured, and the suite was green |

Patterns 1–6 were found in **Module 1**, 7 to 12 in **Module 2**. The file was
created at the close of Module 1, so those six were reconstructed afterwards;
from Module 2 on it is maintained continuously, and each entry is written on the
day its defect appears.

---

## 1. Bit-to-byte conversion written from memory

A width is quoted in bits, stored in bytes, and the division between them is
performed mentally rather than written down.

```text
written                          means                     intended            verdict
------------------------------   -----------------------   -----------------   -------
val LongBytes: Int = 16          a Long spans 16 bytes     8  (64 bits / 8)    WRONG
val DoubleBytes: Int = 16        a Double spans 16 bytes   8  (64 bits / 8)    WRONG
// booleans have 2 bits          a Boolean carries 2 bits  2 STATES = 1 bit    WRONG
```

All three double a width. The first two doubled 8 into 16; the third promoted
"two states" into "two bits". The pattern is the same operation performed from
memory, in both directions.

The two wrong constants propagated into every object carrying a 64-bit field:

```text
shallowSize call        expected   with LongBytes = DoubleBytes = 16
---------------------   --------   ---------------------------------
(0, 0, 0, 2, 0)  Vec2         32   12 + 16 + 16       = 44  ->  48
(0, 0, 1, 0, 0)  Long         24   12 + 16            = 28  ->  32
(1, 1, 1, 1, 1)               40   12+4+4+16+16+1     = 53  ->  56
```

**The rule.** Write the division on the same line as the constant. `8 // 64 bits`
is checkable by eye; `16` is not. A width in bytes that equals the combined width
of two fields is worth a second look.

**Why the build caught two of three.** `Exercise7FootprintSpec` asserts
`shallowSize` on an object with two `Double` fields and on one with a `Long`
field, so both wrong constants had an assertion standing over them. That is the
spec author's choice, not a property of the constants — see pattern 4. The
comment was never executable, and nothing was ever going to catch it.

---

## 2. Two quantities that coincide under the current configuration

Two different facts about the world are written as the same number, and the code
that reads them cannot tell which one was meant.

```text
written                        declares              intended                value now
----------------------------   -------------------   ---------------------   ---------
shallowSize(1, 0, 0, 0, 0)     one reference field   one int field           16
shallowSize(0, 1, 0, 0, 0)     one int field         one int field           16
```

The call was building the boxed `Integer` half of `listOfIntSize`. A
`java.lang.Integer` holds a primitive `int` inline; it holds no reference. Both
spellings evaluate to 16 **only because compressed oops make a reference and an
`int` both 4 bytes**. Turn them off — a heap above ~32 GB, or
`-XX:-UseCompressedOops` — and the two facts separate:

```text
                          ReferenceBytes = 4   ReferenceBytes = 8
                          (compressed oops)    (no compression)
-----------------------   ------------------   ------------------
cons cell                                 24                   32
box, as written                           16                   24
box, as intended                          16                   16
per List[Int] element        40 both ways          56  vs   48
error                                    0 %               16.7 %
```

**The rule.** Use named arguments wherever a call takes several parameters of
the same type: `shallowSize(references = 0, ints = 1, longs = 0, ...)`. Scala 3
supports them at no cost, and they turn a silent slot error into a line the
author fixes before committing.

**Why the build does not catch it.** All five parameters are `Int`, so the type
checker has nothing to compare. The assertion checks the *result*, and the result
is correct — by coincidence. This defect passed a green suite and would have
shipped; it was found by reading the call, not by running it.

**Occurrence 2, Module 2.** Exercise 8 exists to confirm Exercise 1's model of
*this module's* structure. Three of its four measurements built the list with
`scala.collection.immutable.List` instead:

```text
written                              measures            model describes
----------------------------------   -----------------   ----------------
List.from(0 until n).prepended(x)    scala.::            MyList.Cons
MyTree.fromRange(0, n).insert(x)     MyTree              MyTree            right
```

The suite was green, and it was green because the two cells are the same size:

```text
                          header   fields         raw   aligned
-----------------------   ------   ------------   ---   -------
scala.::                      12    2 refs = 8     20        24
MyList.Cons                   12    2 refs = 8     20        24
```

Same 24, same measurement, different structure — which is this pattern exactly,
one layer up: the coincidence is no longer between two *fields* but between two
*types*. `reverse` and `prepend` agreed to the byte. `append` did not, and that
is what exposed it: `scala.List.appended` reaches `n` cells through a mutable
`ListBuffer`, while a `@tailrec` `MyList.concat` allocates `2n + 1` (pattern 10).
A structure swapped for another that happens to lay out identically is caught
only where their *algorithms* differ, and nowhere where only their shapes do.

---

## 3. Off-by-one in a limit

```text
written                   rejects             intended            verdict
-----------------------   -----------------   -----------------   -------
if iterations <= 1        0 and 1             only 0              WRONG
```

`Bench.medianNanos`' own Scaladoc says *"`iterations` must be at least 1"*. One
sample has a median — itself. The guard turned a valid request into the `-1L`
sentinel, contradicting the contract line directly above it: *"the returned
value must be strictly positive for any non-trivial `body`"*.

**The rule.** Write the boundary rows before writing the comparison:

```text
   iterations = 0   ->  reject
   iterations = 1   ->  accept, the median is the single sample
   iterations = 2   ->  accept
```

Then read the operator off the table rather than choosing it from intuition.

**Why the build does not catch it.** `Exercise9BenchSpec` calls `medianNanos`
with `1_000` and `2_000` only. The boundary the guard is about has no call site
anywhere in the suite, so no assertion constrains it in either direction.

---

## 4. A constant is only as tested as the arithmetic that exposes it

The structural entry, and the one that explains why patterns 1 and 2 survived as
long as they did. `Footprint` names eight constants. None of them is asserted
directly; each is observed only through `shallowSize`, `arrayOfIntSize` and
`listOfIntSize` — and **`align` discards information below 8 bytes on the way
out**.

Holding the other seven at their correct values, here is every wrong value each
constant could take while `Exercise7FootprintSpec` stays green, computed by
replaying the suite's numeric assertions:

```text
constant           correct   as first written          after the repair
----------------   -------   -----------------------   -----------------
HeaderBytes             12   9, 10, 11, 12             9, 10, 11, 12
ArrayHeaderBytes        16   13, 14, 15, 16            13, 14, 15, 16
ReferenceBytes           4   3, 4, 5, 6                3, 4, 5, 6
AlignmentBytes           8   8              <- pinned  8
IntegerBytes             4   4              <- pinned  4
LongBytes                8   5, 6, 7, 8, 9, 10, 11     5, 6, 7, 8, 9, 10, 11
DoubleBytes              8   7, 8, 9, 10               7, 8, 9, 10
BooleanBytes             1   0, 1, 2, 3, 4             1              <- pinned
```

Six of the eight were underdetermined. `BooleanBytes` was the sharpest case: it
could be **0** — a `Boolean` field costing nothing at all — with every assertion
still passing, because `align8` rounds `12 + 0` and `12 + 4` into the same 16.

The two originally pinned constants show what pinning takes. `AlignmentBytes` is
fixed because the `align` test walks `0 to 500` and checks `aligned % 8 == 0` at
every one — a property, not a sample. `IntegerBytes` is fixed because
`arrayOfIntSize(1_000_000) == 4_000_016` multiplies it by a million, which is
exactly what defeats the rounding.

**The rule.** For every named constant, name the assertion that would fail if it
were wrong. If no assertion names it, the constant is *documented*, not
*verified*, and saying so in a comment costs nothing. Two ways to pin one:
multiply it by something large enough to survive rounding, or assert a property
over a range instead of a value at a point.

**What was repaired.** `BooleanBytes` is now pinned by one added assertion,
`shallowSize(0, 0, 0, 0, 8) == 24` — eight booleans instead of one, so a
one-byte error becomes an eight-byte one and stops fitting inside what `align`
rounds away. The remaining five carry the admission in their Scaladoc instead,
each naming the assertion that observes it and the range it could still take.
`HeaderBytes` names something stronger: it is **unpinnable in principle**,
because it enters every expression with coefficient 1 and `align8(H - 1 + S)`
can never differ from `align8(H + S)`. The model sees its residue class modulo
8, never its value.

**Why the build does not catch it.** `align` is a rounding function: it is
deliberately non-injective, and every wrong value landing in the same 8-byte
bucket as the right one is erased before the assertion sees it. This is not a
weakness in the spec — it is the arithmetic the module exists to teach, applied
to the module's own test suite.

---

## 5. A generator built inside the by-name parameter it should drive

A combinator takes its element **by name** and evaluates it once per position.
Construct the generator inside that argument and every position gets a *new*
generator — identically seeded, and therefore identically answered.

```text
written                                                distinct values
----------------------------------------------------   ---------------
Array.fill(100_000)(Random(Seed).between(1_000, 1e5))                1
Array.fill(100_000)(rng.between(1_000, 1e5))                    62,957
  where  val rng = Random(Seed)  is bound outside
```

The occurrence is in `Exercise4BoxingSpec`, in the line that builds the input for
the boxing measurement — test code that shipped with the exercise set rather
than an implementation defect. Every one of the 100,000 elements is `15429`, and
the setup additionally builds and discards 100,000 `Random` instances.

The same file gets it right ten lines earlier, in the correctness test: `rng` is
bound outside the loop and only `rng.between` appears inside it. Both spellings
coexist, which is what makes the wrong one hard to see.

**The rule.** A generator is state. Bind it to a `val` outside, and let only the
*draw* appear inside the by-name argument. When reading a `fill`, `tabulate` or
`iterate`, ask what the argument evaluates to on the **second** call — not the
first.

**Why the build does not catch it.** Both forms are well typed, and the wrong one
produces an array of exactly the requested length and element type. Nothing is
null, nothing throws, and no warning fires under `-Wall -Werror`. The assertion
that consumes the array measures allocation, and it still measures the right
number — `15429` lies outside the `Integer.valueOf` cache of -128..127, so
`arr.toList` really does box all 100,000 elements separately:

```text
   15429 outside the cache               ->  true
   distinct box identities in toList     ->  100,000
```

The test therefore passes for the right reason, on an input that is degenerate.
Had the constant fallen inside -128..127, the same test would have reported near
zero and been read as a triumph of escape analysis.

---

## 6. A contract no implementation of that signature can satisfy

A Scaladoc promises a property "for every input". The implementation is correct
and the promise is still false, because the input type contains values the
promise never considered. The defect is in the contract, and no amount of work on
the body will fix it.

```text
promised                             signature         where it fails
----------------------------------   ---------------   ------------------------
"non-negative for every input"       Vec2 => Double    any NaN component:
                                                       NaN >= 0.0 is false, and
                                                       so is NaN < 0.0
"total for all non-negative inputs"  Int => Int        the top seven Ints: the
                                                       answer is 2^31, and no
                                                       Int holds it
"zero warnings suppressed by         Int => Long       a correct prependCells
 annotation" (checklist B1-M2 §C)    (prependCells)    cannot read n, and
                                                       -Werror rejects an
                                                       unread parameter
```

Both were found by reading the contract against the extremes of its input type,
not by any test. Measured:

```text
   align(2147483640)     =  2147483640      Int.MaxValue - 7, already aligned
   align(2147483641)     = -2147483648      first input outside the domain
   align(Int.MaxValue)   = -2147483648
```

Seven of the `2^31` non-negative inputs are wrong, and the property test above
them walks `0 to 500`.

**The rule.** Before writing "for every input", enumerate the extremes of the
input *type* and check the promise against each by hand:

```text
   floating point   NaN, +Infinity, -Infinity, -0.0, subnormals
   integers         MinValue, MaxValue, and the neighbourhood of each
```

If the promise fails at any of them, **narrow the documented domain** — that is a
repair, not a retreat. A contract that admits its boundary is stronger than one
that pretends not to have one, because the next reader can see where the edge is.

**Why the build does not catch it.** Contracts live in prose, and the compiler
does not read Scaladoc. The suites sample the *interior* of the domain and never
approach the edges: `Exercise1Vec2Spec` draws components from `[-100, 100]`, and
`Exercise7FootprintSpec` walks `align` over `0 to 500`. Both are entirely
reasonable test ranges, and neither can see a failure that lives only at
`10^154` or at `2^31 - 1`.

The first two occurrences are now repaired in the contract rather than the body,
and both boundaries are pinned by a test — including, for `align`, an assertion
on the behaviour *outside* the domain, so that a future change to the signature
is confronted with what it would be replacing.

**The third occurrence is repaired the same way, and it is worth separating
because the contract is not in a Scaladoc.** It is a clause of the deliverables
checklist, which is a contract like any other and can be unsatisfiable like any
other. `Sharing.prependCells(n: Int): Long` exists to be read beside
`appendCells(n: Int): Long`; the parallel *is* the lesson, and the whole point is
that only one of the two mentions `n`. A correct prepend costs one cell whatever
the list's length, so the body cannot read its parameter — and under `-Werror`
an unread parameter is not a warning but an error. Either the signature loses
the parameter and the lesson with it, or the clause admits an annotation.

The repair is the clause, not the code: §C now records two suppressions and what
each is for, rather than claiming a zero it cannot have. Note also what the
annotation buys beyond silence — `Sharing`'s own Scaladoc says it: *"Treat the
compiler's complaint as confirmation: an implementation that reads `n` is the one
that is wrong."* The suppression marks the correct implementation rather than
excusing a defective one.

---

## How to use this file

Read it before committing, not after a defect. Eleven questions, one per
pattern:

1. Is there a width in this diff that I converted in my head?
2. Does any call pass several same-typed arguments positionally?
3. Does any comparison sit on a boundary that no test exercises?
4. Did I add a constant, and can I name the assertion that would fail if it were
   wrong?
5. Does any by-name argument construct state that should have been constructed
   once?
6. Does any Scaladoc say "every input", and does it hold at `NaN`, at
   `MaxValue`, at `MinValue`?
7. Does any name in this diff declare a unit, and does the body honour it?
8. Am I answering an integer question through `Double`?
9. Does a recursion here compute a shape and then emit a flat sequence, leaving
   something downstream to rediscover it?
10. For every comparison of a prediction against a measurement: do both sides
    count the same set of things, said out loud in one sentence?
11. Can the fixture I am measuring actually exhibit the property I am asserting,
    or have I chosen the input where it does not appear?

---

## Module 2 — Manual Persistent Data Structures

Numbering continues from 6. When a Module 2 defect instantiates one of patterns
1–6, it is added as an **occurrence to that entry** rather than opening a new
one — the repetition across modules is the finding, and a pattern that recurs in
a different subject is worth more than one that recurred in the same file.

Four of the six already have obvious surfaces in this module, and they are worth
watching for rather than waiting for:

| Pattern | Where it can reappear in Module 2 |
| :--- | :--- |
| 3 — Off-by-one in a limit | `balancedDepth`: is `balancedDepth(16)` 4 or 5? |
| 4 — A constant only as tested as the arithmetic exposing it | `CellBytes` and `NodeBytes` are both 24, so a call that confuses them passes |
| 2 — Two quantities that coincide | exactly the above: a cell and a node are indistinguishable by size on this JVM |
| 6 — A contract no implementation can satisfy | `head` on the empty list, and `treeMap` promising to preserve an ordering it cannot |

Patterns 7 and 8 both came out of Exercise 1 — arithmetic with no data structure
in it, which is worth noting on its own: neither defect needed recursion,
sharing or a measurement to appear.

Pattern 9 came out of Exercise 6, and is the first in this file whose occurrences
are three drafts of a single function rather than three separate sites.

Patterns 10 and 11 came out of Exercises 8 and 9, and both are defects of
*measurement* rather than of code: the implementations they describe were
correct, and the instrument aimed at them was not. That is a category the first
nine entries do not cover, and it is the category an empirical module produces
most of.

---

## 7. A unit declared in the name and nowhere the machine reads

Two functions sit four lines apart in `Sharing.scala`. One counts cells, one
converts cells to bytes. Both return `Long`.

```text
written                              means                    intended           verdict
----------------------------------   ----------------------   ----------------   -------
def prependCells(n) = CellBytes      a prepend costs 24 ...   1  (one cell)       WRONG
                                     ... of something
def cellBytes(cells) = cells * 24    cells -> bytes           cells -> bytes      right
```

The two compose, and the composition is where the unit error becomes visible:

```text
call site                                  with CellBytes        with 1L
----------------------------------------   -------------------   -------
cellBytes(prependCells(N))                 24 * 24 = 576         24
```

576 bytes for a single cons cell, against a cell that is 24 bytes. The error is
not in either function's arithmetic — both multiply correctly. It is that one of
them multiplied at all.

**The rule.** When two functions differ only by unit, name the unit in the return
type, not in the identifier — and until that is possible, read every composition
out loud: `cellBytes(prependCells(n))` should sound like *bytes of cells of a
prepend*, not *bytes of bytes*.

**Why the build does not catch it.** `Long` is a width, not a unit. The compiler
has no way to know that one of these quantities is dimensionless and the other
is not, and `-Wall -Werror` passes without a word. `Exercise1SharingSpec.scala:22`
does assert `prependCells(n) == 1L`, so this one was caught — but by an
assertion written specifically for it. Change the constant to anything and the
type system stays silent. Scala's `opaque type` is the construction that closes
this, and Block 2 builds it.

---

## 8. An exact integer answer routed through `Double`

`balancedDepth(n)` returns a small `Int` — at most 31 for any `Int` input. It was
computed as `Math.ceil(Math.log(n + 1) / Math.log(2))`. Two independent failures
follow from that one line, and neither throws.

```text
occurrence                  input                  written gives   correct   mechanism
-------------------------   --------------------   -------------   -------   ---------------------
ceil over a 1-ulp error     n = 2^29 - 1                      30        29   quotient = 29.000000000000004
Int overflow before the     n = Int.MaxValue                   0        31   n + 1 wraps to Int.MinValue
promotion to double
```

The first, in full:

```text
n = 2^k - 1        log(n+1)/log(2)          ceil    correct
---------------    ---------------------    ----    -------
2^20 - 1           20.00000000000000000       20         20
2^21 - 1           21.00000000000000000       21         21
2^29 - 1           29.00000000000000400       30         29     <- diverges
2^30 - 1           30.00000000000000000       30         30
```

`n + 1` is `2^29` exactly, so the quotient is mathematically the integer 29.
`Math.log` is specified to within 1 ulp, the quotient landed four units above 29
in the last place, and `ceil` turned `4e-15` into a whole level of tree.

The second:

```text
n = Int.MaxValue = 2147483647

  n + 1                 = -2147483648      <- Int arithmetic; the promotion to
                                              double happens after the overflow
  Math.log(-2147483648) = NaN
  Math.ceil(NaN)        = NaN
  (int) NaN             = 0                <- JLS 5.1.3: narrowing NaN does not
                                              throw, it yields zero
```

The largest tree an `Int` can describe reports the depth of the empty one, and
`treeInsertNodes` of it reports 1.

**The rule.** An integer question gets an integer answer. Before writing
`Math.log`, `Math.pow` or `Math.sqrt` in a function whose return type is `Int`,
ask what the bit-level operation is: here it is the bit length of `n`, which is
`32 - Integer.numberOfLeadingZeros(n)` — one machine instruction, no `+ 1` to
overflow and no rounding to get wrong. And when a `double` parameter accepts an
`Int` expression, check where the widening happens: `Math.log(n + 1)` promotes
the *result* of `n + 1`, not its operands.

**Why the build does not catch it.** `Math.log` accepts an `Int` by widening, so
there is no type error; `NaN.toInt` is defined rather than exceptional, so there
is no runtime error. `-Wall -Werror` is silent on both. The suite was silent for
a narrower reason worth recording: it sampled `2^k - 1` for `k` up to 20 —
exactly the right family of inputs, stopped nine powers of two short of the
first divergence. The walk now runs to `k = 30` and asserts `Int.MaxValue`
directly, so both occurrences have a test standing over them.

---

## 9. A structural guarantee carried by traversal order instead of by construction

The property required is about **shape** — `fromRange(0, 15)` must have depth 4.
It is obtained by choosing the order in which values are handed to an
order-sensitive operation, rather than by assembling the shape directly. The
result is then correct exactly while that order is preserved, and the order is
invisible at the place where the guarantee is written down.

Three versions of `fromRange` were written. All three produce `size == 15`; all
three contain the same fifteen values; the depths are measured:

```text
version                                   order fed to insert          depth   verdict
---------------------------------------   --------------------------   -----   -------
midpoint fixed as the root, then           0,1,2, ... ,14                   8   WRONG
foldLeft(insert) over 0 until 15

midPoints, concatenated in-order:          0,1,2, ... ,14                  15   WRONG
mids(lo,mid-1) #::: (mid #:: mids(...))

midPoints, concatenated pre-order:         7,3,1,0,2,5,4,6,11,9,8,          4   right
mid #:: (mids(lo,mid-1) #::: mids(...))    10,13,12,14

Branch assembled on the recursion itself   no order exists to get wrong     4   right by
                                                                               construction
```

The two wrong versions feed **ascending** input to `insert`, which is precisely
the degeneracy Exercise 9 exists to demonstrate: each value is greater than every
value already placed, so each becomes the right child of the previous one and the
tree is a chain of `n` nodes. The first reports 8 rather than 15 only because a
root was pre-placed, splitting one chain of 15 into two chains of 7:
`max(1 + 7, 1 + 7)`.

The third is correct, and correct for a nameable reason: the emission is a
**pre-order** — every node is emitted before any value in either of its subtrees.
When `insert` reaches a value, the node that must be its parent is therefore
already in place, and the value lands exactly where `midPoints` intended it.
Inserting the pre-order of a binary search tree reconstructs that tree exactly.
The distance between the correct version and the 15-deep one is a single swap of
the operands around `#:::`.

The parentheses in the table above are the source's, but only after the fact:
the line was first written without them. Both `#::` and `#:::` end in a colon and
are therefore right-associative, so `mid #:: l #::: r` groups as
`mid #:: (l #::: r)` — not the grouping the eye reads left to right. Here that
was harmless, because the two groupings denote the same sequence, but harmless by
coincidence rather than by design. The parse is now declared. Challenge 16 of
[`challenge-log.md`](challenge-log.md) carries it.

**The rule.** When the invariant is structural, let the constructor carry it.
Checkable by eye: if a function computes the shape — it picks a midpoint, it
recurses on the halves — and then emits a **flat sequence**, the shape it just
computed was thrown away, and something downstream is rediscovering by
comparison what was already known. Look for a `fold` or an `insert` standing
below a recursion that already had the answer.

**Why the compiler and the test suite do not catch it.** Here the usual finding
inverts, and both halves are worth recording.

The *shape* is pinned. `Exercise6MyTreeSpec.scala:53` asserts `depth` against
`Sharing.balancedDepth(n)` at `n` in {3, 7, 15, 31, 1023}, so all three wrong
spellings fail it. The first occurrence was nevertheless found by reading the
Scaladoc rather than by running the suite — a pinned assertion only protects code
that has been run against it.

What nothing pins is the **price**. Both surviving versions build the same tree,
and one of them pays for it:

```text
fromRange(0, 1_000_000)            Branch allocations   median of 5 runs
--------------------------------   ------------------   ----------------
Branch assembled directly                   1,000,000            4.4 ms
midPoints + foldLeft(insert)               18,951,445          493.1 ms
```

Nineteen times the allocations and a hundred and twelve times the wall clock, for
a tree that is identical node for node — `insert` rebuilds every node on the path
it walks, so the build costs the tree's internal path length instead of its size.
`Exercise7TreeFoldSpec.scala:68` constructs exactly this million-element tree,
which means the suite pays the half-second on every run and reports nothing. A
test suite measures failure, not price.

---

## 10. A quantity compared against a model of a neighbouring quantity

Both sides of the comparison are `Long`, both are in bytes, and they count
different sets of things. The model describes what a structure *keeps*; the
instrument reports what a thread *spent*. They coincide only when an operation
produces no garbage and introduces no element — which is one of the four
measurements in Exercise 8, and it is the one that agreed to the byte.

```text
compared                            the model counts          the measurement counts
---------------------------------   -----------------------   ------------------------
cellBytes(appendCells(n))           cells the result keeps    bytes allocated, garbage
                                                              included
cellBytes(prependCells(n))          cells                     bytes, the boxed element
                                                              included
nodeBytes(treeInsertNodes(n))       nodes                     bytes, the boxed element
                                                              included
fromSorted.depth / fromBalanced     a path length             nodes allocated, which is
  .depth                                                      the path plus a new leaf
```

Measured, at `n = 100,000` and a tree of `2^20 - 1`, on JDK 21.0.9 HotSpot,
forked, after warm-up:

```text
operation      model       measured    the difference is
------------   ---------   ---------   ----------------------------------------
prepend               24          40   one java.lang.Integer
tree insert          504         520   one java.lang.Integer
reverse        2,400,000   2,400,000   nothing — reverse introduces no element
append         2,400,000   4,800,040   a discarded spine, a cell, and the box
```

`reverse` is the control that identifies the culprit. It is the only one of the
four that adds no value to the structure, and it is the only one that lands on
the model untouched. Anything that must be subtracted from `reverse` is not a
boxed element, and the subtraction is wrong.

The fourth row is the same error in arithmetic rather than in measurement, and
the right model was four lines away in the same file. `Sharing.treeInsertNodes`
reads `balancedDepth(n) + 1L` and says why in its Scaladoc — *"the count
therefore exceeds the depth by exactly one"* — while `degenerationFactor`
divided one depth by another:

```text
                    numerator   denominator    factor
-----------------   ---------   -----------   -------
depths                   4096            13   315.077
nodes, depth + 1         4097            14   292.643
```

The `+1` is worth 0.02% on the numerator and 7.1% on the denominator. **An
additive constant disappears where the quantity is large and decides the answer
where it is small** — and in a comparison between a degenerate structure and a
balanced one, the balanced side is always the small one.

**The rule.** Before comparing a prediction with a measurement, say what each
side counts in one sentence, out loud, with a noun: *"cells the result keeps"*
against *"bytes this thread allocated"*. If the two nouns differ, the comparison
is not yet a comparison — either model the difference or remove it from the
window, and say which.

**Why the build does not catch it.** Both sides are `Long`; the noun lives in the
name, which the machine does not read (pattern 7, one layer up). Worse, the
tolerances absorbed three of the four: prepend asserted *at most two cells* and
measured 1.67 of one, the tree asserted *within three nodes* and measured 0.67 of
one over, and append was compared against `scala.List`, whose builder-based
`appended` sits 0.005% from the model (pattern 2). Only swapping in `MyList`
produced a gap — 100% — that no tolerance could hide. Three defects sat inside a
green suite for as long as the instrument was pointed at the wrong structure.

---

## 11. A fixture that cannot exhibit the property under test

The assertion is right, the implementation is right, and the input chosen to
join them is the one input for which the property does not hold.

`Exercise9BalanceSpec` measures what depth does to the cost of an insert, by
inserting into a degenerate tree and a balanced one and taking the ratio. It
probed both with `insert(-1)`.

`fromSorted` inserts `0, 1, ..., n-1` ascending, so every value becomes the right
child of the last and the tree is a spine to the right. A value *below* the
minimum is therefore the cheapest insert that tree admits: it is less than the
root, the root's left child is empty, and the walk stops after one node.

```text
probe    sorted (bytes / nodes)   balanced (bytes / nodes)    factor   verdict
------   ----------------------   -------------------------   ------   --------
-1                    48 /    2                 312 /    13     0.15   inverted
4096              98,344 / 4097.7                352 / 14.7    292.6   right
```

The degenerate tree measured **six times cheaper** than the balanced one, in a
test whose subject is that it is three hundred times more expensive.

The same choice hid a second defect. `-1` lies inside the `Integer` cache
(`-128..127`), so `Integer.valueOf(-1)` allocates nothing and the boxed element
of pattern 10 never appeared on that path — which is why both `-1` rows above
are exact multiples of 24 and both `4096` rows are not.

**The rule.** For every property asserted, name two inputs before writing the
fixture: the one that exhibits it maximally, and the one that hides it. Then
assert the *direction* before the magnitude — `sortedCost > balancedCost` is one
line, it cannot be satisfied by an inverted experiment, and it fails on the sign
long before anyone reads the ratio.

**Why the build does not catch it.** A fixture compiles. The assertion that
should have caught it, `measured > predicted / 3.0`, reads like a floor but was
never reached: `degenerationFactor` was wrong upstream (pattern 10, fourth row),
so the suite failed earlier and this defect stayed latent behind another one.
Had the formula been fixed first, the failure message would have read *"the
insert is not copying the path it walks"* — a diagnosis pointing squarely at
`MyTree.insert`, which was correct throughout.
---

## 12. A measurement recorded where nothing can re-run it

A number is written into a Scaladoc or a guide as a measured fact, and no test
reads it. It is not wrong when written. It becomes wrong silently, because
nothing in the build is able to disagree with it — and a claim that cannot be
refuted is not evidence, whatever it was when it was taken.

This is the decay version of pattern 10. There, a quantity was compared against
a model of a neighbouring quantity and the tolerance swallowed the gap. Here
there is no comparison at all, so there is nothing for a tolerance to be too
wide for.

`MyList.map`'s Scaladoc carries a three-row table of measurements, of which the
first row is anchored and the other two are not. The difference is invisible on
the page, which is the whole problem:

| | Where | What is written | What anchors it |
| :-- | :--- | :--- | :--- |
| — | `MyList.map` | `reverse 2,400,000 bytes — 1.00 n cells` | `Exercise8SharingProofSpec` asserts it against `reverseCells` within 1% |
| 1 | `MyList.map` | `filter 4,808,392 bytes — 2.00 n cells` | nothing |
| 2 | `MyList.map` | `map 6,522,656 bytes — 2.72 n cells` | nothing, until `measureMap` — and it was wrong by 124,704 bytes |
| 3 | `Building` | `at n = 16,000 the gap is about 2,400×, and one of them allocates three gigabytes` | nothing |

The first row is the contrast, not an occurrence: it sits in the same table, in
the same sentence, and it is the only one the build can contradict.

Occurrences 1 and 2 were produced in `playground.scala`, which is gitignored and
now empty.

**Occurrence 2 has since been settled, and it settles the argument.** The
residue was the tell: two spines plus one `Integer` per element above the cache
accounted for all but 124,704 bytes of the recorded figure, and that remainder
corresponded to nothing the model predicts. `SharingProof.measureMap` was
written, and the true value is the decomposition with no residue at all:

```text
two spines          2 x 100,000 x 24        4,800,000
one Integer each    (100,000 - 128) x 16    1,597,952
                                            ---------
model                                       6,397,952
measured                                    6,397,952     exact
recorded in the Scaladoc                    6,522,656     +124,704
```

The number had been wrong the whole time. Nothing reported it, because nothing
read it — which is the pattern, stated as a measurement rather than as a
worry.

**Occurrence 3 is what the pattern costs when nobody goes looking.** Both of its numbers are wrong now, and
wrong by exactly the same factor:

```text
                        written      recorded in the checklist   ratio
--------------------   ----------   -------------------------   -----
gap at n = 16,000        2,400 x                     4,816 x     2.00
byAppend at n = 16,000       3 GB                      6.14 GB   2.00
```

Exactly half, both of them, which identifies the cause rather than leaving it a
coincidence. They were written against a model in which appending to a list of
`k` cells allocates `k` cells. It allocates `2k + 1`: `appended` delegates to
`concat`, and `concat` opens with `loop(xs.reverse)`. That is
`appendAllocatedCells` against `appendCells` — pattern 10 again — frozen into
prose where nothing re-checks it. The measurement that corrected it, in
`Exercise8SharingProofSpec`, did not and could not correct the sentence.

All three were corrected in one pass, and the sweep that found them is the
evidence for the rule below — the same two figures had propagated into the
theory guide and the recall set, where nothing read them either:

```text
where                            was                    is
------------------------------   --------------------   --------------------
MyList.map Scaladoc              6,522,656              6,397,952, asserted
MyList.map Scaladoc              filter 4,808,392       2.00 n cells, derived
Building Scaladoc                2,400x / three GB      4,800x / six GB
module2_structures.md §4          :+ = 2,400,104         4,800,024
module2_structures.md §11         difference = 104       retained vs allocated
module2_structures.md §12         map(f) = 2,411,000     6,397,952
module2_structures.md §7          append col. halved     the measured table
quiz b1-m2 §II.7                  3 GB, 3.964/1.881      6 GB, 3.999/2.026
quiz b1-m2 §III.12                "identity shares"     the box, as measured
Exercise5BuildingSpec comment    3.964, 1.881           3.999, 2.026
```

Two of those are worth naming separately. The guide's §7 table was measured
against an `append` that allocates `n` cells rather than `2n + 1`, so its whole
column was half — and it closed with *"Exercise 5 makes you reproduce this
table from your own structure"*, which no correct implementation under §D could
do. And the recall set's §III.12 question taught the opposite of the
measurement: that `map(identity)` shares its contents. A wrong answer key is
the worst form this pattern takes, because it is the one artifact a reader
consults *in order to be corrected*.

**The rule.** A number in an artifact needs a named source that can be run
again. Three forms are acceptable, and the test is whether you can point at one
of them by eye:

  - an assertion that reads it, so the suite fails when it drifts;
  - a committed harness that reproduces it, named at the number;
  - the arithmetic that derives it, so it can be recomputed from the layout
    rather than re-measured.

A number whose source was a scratch file is none of the three. Either promote
the scratch file to a test or delete the number — and prefer a *ratio* in prose
to an absolute, because the ratio survives a change of machine and is what the
sentence was trying to say anyway.

**Why the build does not catch it.** Scaladoc is a comment. `-Wall -Werror`
reads the code beside it and has no opinion about the sentence, and the suite
asserts what it is given: `Exercise5BuildingSpec` closes on
`lastGap > firstGap * 4.0`, a *relative* widening that is exactly the right
assertion for the complexity class and is completely indifferent to whether the
gap is 2,400× or 4,816×. The suite is green, the test is correct, the
complexity class it proves is correct, and the number three lines above the
function is off by a factor of two.
