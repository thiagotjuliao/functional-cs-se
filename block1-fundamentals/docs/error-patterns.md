# Block 1 — Error Patterns

The recall set asks whether the mechanism is understood. The challenge log asks
whether it can be derived. Neither asks the question this file asks:

> **Where does composition go wrong, once the mechanism is already known?**

Every entry below was a real defect written while working this block. None of
them is ignorance of a mechanism. `LongBytes = 16` was written by someone who
knows a `Long` is 64 bits; the error is in the conversion, not in the knowledge.

That is why this file is organised by **pattern** rather than by exercise.
Forty-six individual mistakes are a diary and nobody rereads a diary. Twenty
recurring shapes are a review checklist.

Each entry carries four things: what the pattern is, the occurrences that
instantiated it, the rule that prevents it, and — the field that makes this
usable — **why the compiler and the test suite do not catch it**. Every pattern
but the first compiles cleanly under `-Wall -Werror` in all its occurrences.

| # | Pattern | Occurrences | Caught by the build? |
| :-- | :--- | :-: | :--- |
| 1 | Bit-to-byte conversion written from memory | 3 | two of three |
| 2 | Two quantities that coincide under the current configuration | 2 | no — a test passed for the wrong reason |
| 3 | Off-by-one in a limit | 2 | no — the boundary has no call site, and the second occurrence is prose |
| 4 | A constant is only as tested as the arithmetic that exposes it | 2 confirmed, 6 latent | no — one latent since pinned, five documented |
| 5 | A generator built inside the by-name parameter it should drive | 1 | no — a test passed on a degenerate input |
| 6 | A contract no implementation of that signature can satisfy | 3 | no — contracts are prose, and one of them is a checklist clause |
| 7 | A unit declared in the name and nowhere the machine reads | 1 | no — both sides of the confusion are `Long` |
| 8 | An exact integer answer routed through `Double` | 2 | no — the suite stopped two powers of two short |
| 9 | A structural guarantee carried by traversal order instead of by construction | 3 | the shape yes, the price no |
| 10 | A quantity compared against a model of a neighbouring quantity | 5 | no — the tolerances were wide enough, and the fifth is a verb in a comment |
| 11 | A fixture that cannot exhibit the property under test | 2 | no — and the failure it finally produced blamed the wrong file |
| 12 | A measurement recorded where nothing can re-run it | 3 | no — two of the three were wrong when measured, and the suite was green |
| 13 | A measurement taken while its subject is still changing | 4 | no — it passes intermittently, and the fourth reaches a checklist field instead of an assertion |
| 14 | An equality standing in for an inequality | 1 | no — it agrees with the original across the whole tested domain |
| 15 | A wildcard standing in for the constructors it currently covers | 3 | no — the defect is the removal of the check that would report it |
| 16 | A value test that spends a resource it never mentions | 1 | no — it passes 18 runs in 19, and the failure blames the implementation |
| 17 | A pattern-matching lambda where the function takes more than one parameter | 1 | no — the re-tupling is free under strict parameters and fatal under a by-name one |
| 18 | An invariant measured at the wrong node | 3 | no — not rotating preserves the in-order walk, so the content assertions stay green |
| 19 | A step delegated to a primitive that does not maintain the invariant | 2 | no — the types are identical and the contents stay sorted |
| 20 | An allocation budget met by escape analysis rather than by construction | 2 | no — it passes in isolation and fails after four other tests, with no source change |

Patterns 1–6 were found in **Module 1**, 7 to 12 in **Module 2**, and 13 to 20
in **Module 3**. The file was
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

| # | Where | What was written | What was meant |
| :-- | :--- | :--- | :--- |
| 1 | `Bench.medianNanos` | `if iterations <= 1` | `if iterations < 1` |
| 2 | `Loops.factorial` Scaladoc | *"It wraps silently when `n >= 20`"* | wraps from 21; `20!` is exact |

The two occurrences sit on opposite sides of the compiler. The first is a
comparison the machine executes; the second is a sentence the machine never
reads. The arithmetic mistake is identical, which is the point of filing them
together.

### Occurrence 1 — the guard

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

### Occurrence 2 — the documented limit

`Loops.factorial`'s Scaladoc opened with *"It wraps silently when `n >= 20`"*.
The boundary is one higher:

```text
                                          value   <= Long.MaxValue ?
-------------------------   -------------------   ------------------
Long.MaxValue               9,223,372,036,854,775,807
20!                         2,432,902,008,176,640,000   yes, exact
21!                        51,090,942,171,709,440,000   no, 5.54x over
```

`20!` is the largest factorial a `Long` holds, so the documented domain
excluded the one input that most needed to be in it.

What makes this occurrence worth filing rather than merely fixing is that **the
correct number was already in the file, twice.** Four lines below the new
sentence, the Scaladoc it was added to still read *"Above `n = 20` a `Long`
silently wraps"*; and `Exercise4LoopsSpec` asserts

```scala
assertEquals(Loops.factorial(20), 2_432_902_008_176_640_000L, "the largest that fits a Long")
```

Three statements of one boundary, in two files, disagreeing — and the suite
green throughout.

**The rule for occurrence 2.** A documented limit is stated once. When a domain
is written in a Scaladoc, delete the prose it supersedes rather than adding a
sentence beside it; two statements of a boundary are a contradiction waiting for
one of them to be edited. And a domain is pinned by an assertion on **both**
sides of the limit — the last input that works and the first that does not —
which is the discipline `Footprint.align` and `Arithmetic.digits` already
follow in this repository.

**Why the build does not catch it.** Nothing reads a Scaladoc. The suite
asserts the true boundary at `factorial(20)` and stops there, so the first
wrapping input has no call site — occurrence 1's failure mode exactly, arrived
at from the other direction.

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
[`challenge-log.md`](challenge-log/b1-m2.md) carries it.

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
MyList.concat's Scaladoc:           cells the result keeps    the verb says "allocates",
  "allocates one cell per                                     and allocation is 2n
  element of xs"
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

**Second occurrence, same probe, a different function.** Asked to predict what a
corrected `MyTree.contains` costs on the two trees, the answer reached for
`contains(-1)` again — and `-1` is below the minimum of a spine that grows to
the right, so it is that tree's *cheapest* query:

```text
probe                    sorted/degenerate   balanced   verdict
----------------------   -----------------   --------   --------
-1   (below the min)                     1         12   inverted
4096 (above the max)                 4,096         13   right
```

The shape of the mistake is identical to the first: on a structure that is
degenerate in one direction, a probe aimed at the other end measures the best
case and reads like the worst. A right spine has exactly one cheap query and
`n` expensive ones, and the cheap one is the memorable value.

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
---

## 13. A measurement taken while its subject is still changing

The instrument is correct, the arithmetic is correct, and the number is stale
before it is used. Nothing is wrong with the measurement except *when* it was
taken.

This is not pattern 12's problem. There, a number could not be re-run; here it
re-runs perfectly and gives a different answer, because the thing being measured
moved between the measurement and the assertion.

The subject was the largest recursion depth a method survives. Ten searches for
that one boundary, in order, on one JVM in one process:

```text
run  1    32,768    and the very next call to deep(32,768) fails
run  2    24,575    the interpreted frame
run  3+   61,653    the C2-compiled frame
```

A **C2-compiled frame is 2.51× smaller than an interpreted one**, so the
boundary climbs by that factor as the JIT does its work. Run 1 is the dangerous
row: it does not sit at either value. It straddles the transition, and the
number it returns was true for part of the search and false for the rest —
which is why `deep(32,768)` fails immediately afterwards.

Warm, the answer is stable but not fixed: 61,653, 61,655, 61,661 on consecutive
calls, a spread of 8 frames.

| # | Where | What was written | What was meant |
| :-- | :--- | :--- | :--- |
| 1 | `Exercise1StackProbeSpec` | `!survives(deep(found + 1))`, on the first, cold search | a depth safely above the boundary, measured warm |
| 2 | `StackProbe.maxDepth` Scaladoc | *"Binary search"* | a binary search over an `f` that has already been warmed |
| 3 | `StackProbe.maxDepth` body | `loop(n + 1)` — a linear climb | the binary search the Scaladoc above it specifies |
| 4 | `Module3Harness.maxSurviving` | a binary search entered cold | a search over an `f` already warmed, as `probeBytes` does |

Occurrence 1 is a margin of **one frame** — 0.0016% — on a quantity that drifts
by 8 between consecutive warm calls and by a factor of 2.51 across compilation.
It is the guide's own §23 in miniature: that section forbids asserting an
absolute depth, and this asserted something stricter without noticing.

Occurrence 2 is the more useful one, because **the rule already existed in this
repository and was not carried across.** Module 2's `SharingProof.bytesOf` says
it outright — *"Warm the body before measuring, exactly as `Exercise4BoxingSpec`
does. A cold measurement measures the interpreter."* The discipline was written
down for the heap, and the stack instrument was specified without it, where it
decides a factor of 2.51 rather than a few per cent.

**The rule.** Before asserting on a measured quantity, ask what it is a function
of, and list everything on that list that is not the input:

```text
quantity                  also a function of
-----------------------   --------------------------------------------
allocation in bytes       nothing after warm-up          -> stable
elapsed time              tier, cache state, other load  -> warm, repeat
recursion depth           tier, stack size, call site    -> warm, margin
```

Then: **warm until the answer repeats, and assert with a margin wider than the
drift you just observed.** Both halves are needed. Warming without a margin
still asserts 8 frames of noise away; a margin without warming still asserts
across a factor of 2.51.

**Why the build does not catch it.** It cannot, and worse, it will tell you the
opposite. A cold assertion passes whenever the timing happens to line up, so the
defect is intermittent rather than absent — and the failure, when it comes,
blames the implementation under test. This one failed with *"61,679 was reported
as surviving and does not"*, which reads as an accusation against a `maxDepth`
that was correct throughout. That is pattern 11's signature reappearing: a
failing test naming the wrong file.

**Occurrence 3 is the sharpest of the three, and it needs the pattern's title
read more strictly.** In occurrences 1 and 2 the measurement was taken *while*
the subject happened to be changing. Here **the measurement is the cause of the
change**: reaching the boundary by climbing one at a time takes some fifteen
thousand invocations of the very method whose frame size is being measured, and
fifteen thousand invocations is what C2 waits for.

Three searches, one JVM, one fresh 1 MiB thread, `f = sumNaive`, `limit = 40,000`:

```text
                                            answer
--------------------------------------   ----------
binary  (cold, the first thing that runs)     14,999
linear  (the committed maxDepth)              40,000
binary  (warm, after the linear scan)         39,999
```

Row 2 is the `limit` itself: the linear search found no boundary, walked to the
end of the interval and returned the caller's own argument as a measurement. Row
3 is the control — the *same* binary search, after the linear scan has run,
reports 39,999 rather than 14,999. The algorithm did not change; only the tier
did. The factor is at least 2.67x, a floor rather than a value because rows 2 and
3 both saturated, against the 2.51x this entry already records.

**The rule this adds** to the two already stated: count how many times the
instrument touches the subject, and prefer the instrument that touches it least
— not for speed, but because **every touch is a warm-up**. Binary search was
specified here for that reason and not for its complexity class. Repaired with
exponential search establishing the upper witness before the binary phase
narrows: 34 probes against ~15,000, and `Exercise1StackProbeSpec` fell from tens
of seconds to 0.296 s. Challenge 32 in [`challenge-log.md`](challenge-log/b1-m3.md)
carries the full measurement.

**Occurrence 4 is the same defect one level up, in the shared harness**, and it
is the one that reached a checklist field. `Module3Harness` carries two
instruments side by side:

```scala
protected def maxSurviving(limit: Int)(f: Int => Any): Int = ...   // no warm-up

/** Bytes allocated while evaluating `body`, after warm-up. */
protected def probeBytes[A](body: => A): Long =
  (0 until 20).foreach(_ => body)      // twenty iterations before measuring
  AllocationProbe.measure(body)._2
```

Warm-up discipline for allocation, none for depth — the same asymmetry
occurrence 2 records between `bytesOf` and `maxDepth`, reproduced inside one
file ten lines apart. The consequence is that the *same expression*, in the
same forked JVM, reports two different ceilings depending on which spec ran
first:

```text
                                      E6 + E7 together    each spec alone
  MyList.foldRight, measured in E6              16,895             16,895
  MyList.foldRight, measured in E7              30,862             16,895
  foldRightLazy, measured in E7                  7,751              3,879
```

E6's own binary search invokes `cells(n).foldRight(...)` thousands of times and
leaves the method compiled; E7 then measures the compiled frame. Run E7 alone
and it agrees with E6 exactly. Neither number is wrong and neither is a property
of `foldRight`: each is a property of `foldRight` *plus an unnamed JIT state*.

**What makes this occurrence worse than 1 to 3** is where the number was going.
Occurrences 1 to 3 fed assertions, which fail loudly when they are wrong. This
one feeds `report`, and `report` feeds the §E field `MyList.foldRight: ______`,
where the number is written down once, without its conditions, and reread later
as a constant.

**And it did not merely add noise — it reversed two findings.** The audit round
of E6 and E7 answered four challenges from this instrument's cold output, and
when the warm-up was added, two of the four conclusions inverted:

```text
                                     from the cold instrument    warm
  foldRightLazy against foldRight        6.2x worse              0.80x better
  existsLazy against existsStrict        overflows earlier       overflows later
```

Both had been written up with derivations, tables and a captured stack listing
behind them. The derivations were sound; the numbers they were reasoning about
described a method caught mid-compilation. A measurement taken while its subject
is still changing does not produce an imprecise finding — it can produce the
opposite finding, fully argued. Challenges 41, 43 and 44 carry the full account,
including what the cold readings had claimed.

**Repaired** by warming inside `maxSurviving`: 200 rounds at depth 128, 25,600
invocations, past C2's threshold. The two specs that disagreed now report 24,695
and 24,694.


---

## 14. An equality standing in for an inequality

A loop's termination test is `<`, and the recursion that replaces it tests `==`.
Inside the intended domain the two are the same function. Outside it, one stops
and the other does not — and because the replacement is tail-recursive, *not
stopping* raises nothing.

| # | Where | What was written | What was meant |
| :-- | :--- | :--- | :--- |
| 1 | `Loops.fibonacci` | `if i == n then a` | `if i >= n then a`, from `while i < n` |

The original and the translation, side by side:

```text
while i < n do ...          ->    if i == n then a else loop(i + 1, ...)
       ^                                ^
       an ordering                      an identity
```

For every `n >= 0` they agree, because `i` starts at 0 and rises by one: it
reaches `n` before passing it, so the first `i` failing `i < n` is the first
`i` satisfying `i == n`. That coincidence is the whole defect. It holds exactly
where the tests look.

For `n < 0` the two separate completely. Measured, by calling the compiled
method from a plain `java` process rather than from the suite:

```text
                            result                     elapsed
-------------------   ---------------------------   -----------
reference(-3)                                   0        0 ms
Loops.fibonacci(-3)          -1880398516846847095    4,136 ms
```

`i` never equals `-3` while rising from 0, so the recursion runs to
`Int.MaxValue`, **wraps to `Int.MinValue`**, and climbs back to `-3`. That is
`2^32 - 3 = 4,294,967,293` iterations at 0.96 ns each — consistent with the
4,136 ms observed, and with a `goto` rather than a call.

Which is the sharpest part of the pattern: **the tail call removed the symptom
along with the stack.** A non-tail recursion with an unreachable base case dies
in milliseconds with a `StackOverflowError` naming the method. This one has one
frame and no allocation, so there is nothing to exhaust. It returns — late, and
with a number that is a genuine Fibonacci value of a meaningless index.

**The rule.** When transcribing a loop, the termination test is translated
**literally**, and `==` is never the translation of `<`. Write the negation of
the loop's own condition as the recursion's base case and stop there:

```text
while p do body        ->    if !p then <result> else loop(<step>)

while i < n            ->    if i >= n then a         correct
                             if i == n then a         agrees only while i <= n
```

An equality is safe as a base case only when the counter is *proved* to land on
it. Rising by one from a known start is such a proof for `n >= 0` and no proof
at all for the rest of the type — and the rest of the type is where the input
came from.

**Why the build does not catch it.** `Exercise4LoopsSpec` compares the
implementation against the imperative original it was translated from, which is
the right test — and it runs it over `(0 to 90)`, plus a recurrence check over
`(2 to 90)`. Every fixture is non-negative, so the fixture cannot exhibit the
divergence: pattern 11 again, in the one spec written specifically to catch a
mistranslation. The neighbouring `reverseDigits` test does exercise negatives
(`-42`, `-1024`); the `fibonacci` one does not, and the asymmetry is invisible
until the two are read side by side.

**Fixed**, and the regression pinned: `Exercise4LoopsSpec` now checks `-1`,
`-3`, `-1000` and `Int.MinValue` against the reference. Note what that test
would have cost *before* the fix, though, because it is the reason a defect
like this is cheaper to prevent than to catch. Against the defective version
every one of those calls climbs the whole `Int` range before terminating:

```text
n             iterations to reach it
-----------   ----------------------
-1                     4,294,967,295
-3                     4,294,967,293
-1000                  4,294,966,296
Int.MinValue           2,147,483,648      (reached on the wrap itself)
                       --------------
x2 calls per n        30,064,769,064  ->  ~29 s at 0.96 ns
```

A spec file that completes in **18 ms** today would have taken half a minute to
report a one-character mistake. Against the corrected version those same eight
assertions are free. The guard that is cheap to keep was expensive to install —
the usual shape of a fixture nobody wrote.

---

## 15. A wildcard standing in for the constructors it currently covers

A `match` over a closed ADT ends in `case _`. Today the wildcard covers exactly
the constructors the author had in mind, so the branch is correct and the suite
is green. What it also does — and this is the whole of the defect — is **switch
off the exhaustivity checker for that match**, silently and for good. The check
that would report the next constructor is the one the wildcard removed.

| # | Where | What was written | What was meant |
| :-- | :--- | :--- | :--- |
| 1 | `EarlyExit.forall` | `case _ => false` | `case _: Cons[A] => false` |
| 2 | `EarlyExit.exists` | `case _ => true` | `case _: Cons[A] => true` |
| 3 | `EarlyExit.takeWhile`, in `loop` | `case _ => acc` | `case Nil => acc` and `case _: Cons[A] => acc`, separately |

Any spelling that names the constructor restores the check; the typed pattern
was chosen over `case Cons(_, _)` because it is an `instanceof` where the
constructor pattern also calls `unapply`, and the type argument is deducible
from the scrutinee, so `-unchecked` has no objection.

Occurrence 3 is the one that merges two *different* terminations — the list ran
out, and an element failed the predicate — and it is also the one where the
merge costs something beyond safety; see the closing note.

`EarlyExit.indexOf`, in the same object and written in the same session, names
both `Cons` alternatives and is unaffected. Three sites switch the check off and
one keeps it, with nothing in the file explaining the difference: the defect is
not a decision taken wrongly, it is a decision not taken.

**What it costs.** Compiled side by side on a `Toy` enum that is `MyList` with
the third case Block 3 would want, and run on three elements that all satisfy
`p`, one of them reached through the new constructor:

```text
Wild.forall (flat  , _ > 0) = true     correct
Wild.forall (grown , _ > 0) = false    WRONG - no element fails p
Total.forall(grown , _ > 0) = MatchError: Concat(Cons(1,Cons(2,Nil)),Cons(3,Nil))
```

Not a crash — a plausible wrong value. The named version names the class and the
line that needs a new branch; the wildcard version reports `false` for a list on
which the predicate holds everywhere. This is precisely the mechanism `MyList`'s
own Scaladoc relies on when it says a third case *"would break every incomplete
match in the codebase"*: the breakage is the feature, and `case _` opts out of
it.

**The rule.** Over a `sealed` or `enum` scrutinee, `case _` is not written —
name the constructor, **including when it is the only one left**. Checkable by
eye, and by grep: every `case _ =>` in a file that matches on a closed ADT is
either a wildcard over an open type or this pattern. The saving it offers is
nine characters; what it spends is the compiler's only means of finding the
match again later.

**Why the build does not catch it.** Because the defect *is* the removal of the
detector. The two spellings, compiled together under the project's own `Compile`
flags:

```text
-- [E029] Pattern Match Exhaustivity Warning: Toy.scala:22:4
22 |    xs match
   |    ^^
   |    match may not be exhaustive.
   |
   |    It would fail on pattern case: Toy.Concat(_, _)
No warnings can be incurred under -Werror
1 warning found
1 error found
```

One error, on the **named-constructor** version. The wildcard version, fifteen
lines above it, produced nothing at all. `-Wall -Werror` is fully armed here and
has nothing to fire at: the match genuinely is exhaustive, by construction,
because a wildcard matches everything.

The residue is visible in the bytecode, where it reads as an improvement.
`loop$6`, the lifted loop of `indexOf`, ends with `new MatchError; athrow`;
`forall`, `exists` and `loop$7` contain no such instruction:

```text
function      MatchError in the compiled body
-----------   -------------------------------
indexOf         yes  (loop$6, offset 88)
forall          no
exists          no
takeWhile       no  (loop$7)
```

That `athrow` is unreachable code today. It is also the compiler's own admission
that it could not prove the match total — which is exactly the state you want it
to stay in. **A match that can throw `MatchError` is one the compiler is still
reasoning about; a match that cannot is one it has stopped reasoning about.**

And the suite cannot separate them either, now or ever. `MyList` has two cases,
so `case _` and `case Cons(_, _)` are the same function on every input that
exists. No test over the current ADT can tell them apart; only a change to a
different file, in a later block, can — and by then the three call sites are no
longer being looked at.

**Why this is not an occurrence of pattern 2.** The shape matches — two
spellings agreeing under the current configuration, here the arity of the ADT
rather than compressed oops. The fourth field is what separates them. In
pattern 2 the check never existed: all five parameters are `Int` and the type
checker has nothing to compare. Here the check exists, works, and is switched
off by the defect itself.

**The cost is not only safety.** In occurrence 3 the wildcard also discards a
bit the machine had already computed. `loop$7`, disassembled:

```text
  5: instanceof  MyList$Cons
  8: ifeq        84          <- not a Cons: the list ran out
 ...
 57: ifeq        84          <- the guard rejected: an element was dropped
 ...
 84: aload_3 ; areturn       <- return acc
```

Two conditional branches converging on one label. Reaching 84 from offset 8
means nothing was dropped, and the correct answer there is `xs` itself — a
persistent structure, so the sharing is unobservable. Reaching it from 57 means
the prefix must be rebuilt. Merging the two labels costs one of the two copies
the rebuild makes: measured with `AllocationProbe` on a 100,000-element list the
predicate keeps whole, 4,800,000 bytes against 2,400,000, or 48 bytes per
element against 24. Splitting them costs one extra `areturn` and no extra test.

Not zero. The accumulator is built during the walk, and that it was unnecessary
is learned only on arrival, by which time its cells exist; only the `reverse` is
saved. This entry claimed zero when it was first written, and challenge 30 in
[`challenge-log.md`](challenge-log/b1-m3.md) carries the corrected accounting.

Which still makes the repair worth stating twice: **naming the constructors
fixes the exhaustivity hole and half the allocation at the same keystroke.**

---

## 16. A value test that spends a resource it never mentions

The assertion is about arithmetic. Nothing in it names the stack, the heap or
the clock. But *evaluating the fixture* consumes one of them, and the amount
available varies between runs — so the test fails on a quantity it does not
mention, and the stack trace accuses the implementation.

| # | Where | What was written | What was meant |
| :-- | :--- | :--- | :--- |
| 1 | `Exercise2TailShapesSpec`, *"all three agree wherever all three survive"* | `sumNaive(10_000)` on whatever thread MUnit supplies | the same assertion on a thread of a stated size |
| 2 | `Exercise6SafeFoldSpec`, *"foldRightComposed ... relocates the cost"* | `probeBytes(...)` at `n = 50_000`, against a ceiling of ~30,800 | a probe at a size that owes the ceiling nothing |
| 3 | `Exercise7LazyFoldSpec`, *"foldRightLazy is a right fold"* | a value check at `n = 5_000`, against a cold ceiling near 2,000 | `n = 512` |
| 4 | `Exercise7LazyFoldSpec`, *"existsLazy stops at the first hit"* | `counted.get == 1_000_000` on the **no-match** path | the same count on a list short enough to traverse |

Occurrences 2 to 4 are the same shape as 1 and were all written by the exercise
author, not by the implementer — which is worth saying plainly, because three
red tests in a row read as three defects in `Folds.scala` and were none.

**Occurrence 2 also refutes the obvious repair.** Sizing the probe from the
ceiling the suite has just measured looks like exactly the discipline this
pattern asks for, and it still fails: run `Exercise6SafeFoldSpec` whole and the
million-element test above shifts the JIT enough that a probe at *half* the
number measured three lines earlier overflows.

```text
largest n that foldRightComposed survives, same suite, four runs
  30,816      30,856      30,804      30,812
probe at composedCeiling / 2  =  15,428      ->  StackOverflowError
```

The rule's phrase *the bound must come from the cold measurement* is therefore
not a preference for one measurement over another. Where the subject is the
stack, **no measurement the suite can take is cold enough to license a fixture
near the bound**, so the fixture has to leave the bound's neighbourhood
entirely. The licence to use a small `n` comes from somewhere else: in
occurrence 2, from an assertion that allocation is linear in `n`, which makes
2,048 and 50,000 measure the same per-element quantity.

**Occurrence 4 is the extreme case and deserves its own line**, because it is
not a fixture that is merely too large — it is one that *cannot* be satisfied.
`existsLazy` short-circuits on a hit and only on a hit; with nothing to stop at,
`||` forces its right operand at every step and the recursion descends the whole
list. A correct implementation, measured:

```text
existsLazy(cells(1_000_000), _ ==  3)   survives, p applied 4 times
existsLazy(cells(1_000_000), _ == -1)   StackOverflowError at ~1,385 elements
existsLazy(cells(1_000),     _ == -1)   survives, p applied 1,000 times
```

The assertion asked the early exit to rescue the one case that contains no early
exit. No implementation of that signature passes it, which makes it an
occurrence of pattern 6 as much as of this one.

The test's own name states the precondition — *wherever all three survive* — and
the fixture violates it intermittently. Caught once in 19 runs:

```text
==> X Exercise2TailShapesSpec.all three agree wherever all three survive
      java.lang.StackOverflowError: null
        at cs.se.block1.module3.TailShapes$.sumNaive(TailShapes.scala:39)
        at cs.se.block1.module3.TailShapes$.sumNaive(TailShapes.scala:39)
        ...
```

**10,000 is not a small number for this function.** Measured cold, on the
default thread, as the first thing a fresh JVM does with it — eight separate
processes:

```text
41,521   16,383   41,511   41,487   41,455   16,383   41,459   41,509
```

Bimodal, because the search either does or does not cross C2's step while it
runs. Against 16,383 the fixture has a margin of 1.64x.

**And the real margin is smaller than any of those numbers, because none of them
can be taken at the moment that matters.** The value test runs *first* in the
suite, when nothing has touched `sumNaive` and its frames are interpreted and at
their largest. The ceiling test that would report the number runs afterwards —
and by then the value test has already warmed the method. Measuring the quantity
raises it, so **the suite cannot report the number that decides its own first
assertion**. The 16,383 above is an upper bound on a boundary nobody can
observe in place.

**The rule.** A test that asserts a *value* must not put its fixture near a
*resource* boundary. Two repairs, and the second is preferred wherever the
resource is part of the module's subject matter:

```text
keep the fixture far below any plausible bound   -> the bound must come from the
                                                    COLD measurement, never the warm
state the resource instead of hoping for it      -> StackProbe.onStack(8192) { ... }
```

"Far below" is not a feeling. It is a ratio against a measured cold bound, and
the same discipline pattern 13 asks for one level up: the margin is set against
the drift that was observed, not against the drift that was expected.

**Why the build does not catch it.** Three reasons, and the third is the one
that wastes an afternoon:

  - It passes. Eighteen runs out of nineteen, and the nineteenth looks like
    infrastructure flakiness rather than a defect in the fixture.
  - `-Wall -Werror` has no opinion about the magnitude of an integer literal.
    Nothing distinguishes `10_000` from `1_000` to a type checker.
  - When it does fail, **the failure names the implementation**. The trace is
    forty frames of `TailShapes.sumNaive`, which is pattern 11's signature —
    a failing test blaming the wrong file. And it is worse here than usual,
    because `sumNaive` is *specified* to overflow: the failure reads as the
    function doing exactly its job, so the reflex is to doubt the expected value
    rather than the environment the fixture was evaluated in.

**Not an occurrence of pattern 13.** There, an assertion is made *about* a
quantity that drifts. Here nothing is measured and nothing in the assertion
drifts — `sumNaive(10_000) == 50_005_000` is true at every tier. What drifts is
the *cost of evaluating it*, which is invisible in the assertion's text. The two
share a discipline and not a shape.

---

## 17. A pattern-matching lambda where the function takes more than one parameter

`{ case (a, b) => e }` is not a lambda with a destructuring binder. It is a
*pattern-matching anonymous function*, and when the expected type is `FunctionN`
with N greater than 1 the expansion manufactures the tuple that the pattern then
takes apart:

```text
written                        compiled as
{ case (a, b) => e }           (x1, x2) => (x1, x2) match { case (a, b) => e }
```

The parameters were already separate. The tuple exists only to be destructured
on the next line.

| # | Where | What was written | What was meant |
| :-- | :--- | :--- | :--- |
| 1 | `LazyFold.existsLazy` | `foldRightLazy(xs, false): case (a, acc) => p(a) \|\| acc` | `(a, acc) => p(a) \|\| acc` |

**With strict parameters the manufactured tuple costs nothing.** Measured on two
folds, `case` against the plain lambda, everything else identical:

```text
                              n          bytes      per element
foldRightComposed  case    2,048         98,344        48.02
foldRightComposed  plain   2,048         98,344        48.02
foldLeft           case  100,000      2,399,640            —
foldLeft           plain 100,000      2,399,640            —
```

Zero difference, byte for byte. Escape analysis proves the tuple never escapes
and scalar replacement deletes it. Anyone who avoids `case` here for performance
is avoiding nothing.

**With a by-name parameter it is not a cost — it is a different program.**
Building `(x1, x2)` evaluates `x2`, and where `f: (A, => B) => B`, `x2` is the
suspension. The laziness dies in the pattern, before the body is entered and
therefore before `||` can short-circuit. The same body, folded over
`cells(10)` with an `f` that returns `-1` on the first element:

```text
{ case (a, b) => ... }      p applied 10 times
  (a, b)    => ...          p applied  1 time
```

Both return `-1`. Ten times the work for the same answer, and at a million
elements the difference is a `StackOverflowError` against four frames.

**The rule.** Count the parameters of the *function type*, never the shape of
the value being bound:

```text
(A, B)   => C      two parameters              (a, b) => ...     case re-tuples
((A, B)) => C      one parameter, a tuple      case (k, v) =>    genuine destructuring
(A, => B) => B     a by-name parameter         (a, b) => ...     not a preference
```

`Map.foreach`, `List[(A, B)].map` and `Option[(A, B)].flatMap` are the second
row: the function really does receive one `Tuple2`, and `case` is the right and
only concise spelling. `foldLeft`, `foldRight` and every other `Function2` are
the first.

**Why the build does not catch it.** Four reasons, and the fourth is why the
habit survives long enough to reach the one place it matters:

  - Both spellings are well typed and neither produces a warning under
    `-Wall -Werror`. The expansion is in the language specification, not a
    compiler wart.
  - There is no allocation to profile, per the table above, so no measurement
    taken for any other purpose will surface it.
  - **`existsLazy` still returns the right answer.** `true` for a present
    element, `false` for an absent one, at every size the test can reach. Only
    the *count* of applications and the *ceiling* differ, and a suite that
    asserted the boolean alone — the natural suite to write for a predicate —
    would be green. This one counts, and counted 1,000,000 where it wanted 4.
  - The habit is reinforced for free everywhere else. Of the twelve
    `case (`-shaped binders in this repository's main sources, one is textbook
    correct (`VarIntCodec.decode`, a `flatMap` over `Option[(Int, Int)]` with a
    guard and two clauses), two are defensible (`Bits` and `Sets` fold with a
    tuple *accumulator*, which a plain lambda cannot destructure in its
    parameter list), eight are `Function2`s over strict parameters where the
    spelling is redundant and free — and one is the defect. Exactly one function
    type in the repository has a by-name parameter, and that is where the
    eleven harmless repetitions were spent.

**The repair.** The nine redundant `Function2` spellings were converted to plain
lambdas — not because they cost anything, but so that a surviving `case (` in
this repository's main sources means one of the two legitimate shapes and the
eye-check above stays cheap. The three that remain are named in the paragraph
above. The suite is unchanged by the conversion: 101 passing before and after, on
the same eleven `NotImplementedError`s from the two exercises still unwritten.

---

## 18. An invariant measured at the wrong node

A guard reads the right quantity from the wrong place. `balanceFactor` is total
and returns an `Int` for any tree, so every misplacement type-checks and most of
them still compile to a plausible-looking condition.

Three occurrences, all in `Avl.rebalance`, all within one sitting:

| # | What was written | What was meant |
| :-- | :--- | :--- |
| 1 | `balanceFactor(l) == 2` as the left-left trigger | `balanceFactor(t) == 2 && balanceFactor(l) >= 0` |
| 2 | `balanceFactor(t) == 2 && balanceFactor(r) == -1` for left-right | `... && balanceFactor(l) <= 0` |
| 3 | `balanceFactor(t) >= 0` as the trigger | `balanceFactor(t) == 2` |

Occurrence 1 is unreachable by construction. `rebalance` declares that both
subtrees already satisfy AVL, so `balanceFactor(l)` is confined to
`{-1, 0, +1}` and `== 2` can never hold. All four guards tested the child
against plus or minus 2; the function was the identity:

```text
case   bf(t)   bf(l)   bf(r)   guard that fires   depth in   depth out
LL       +2      +1       0        none               4          4
LR       +2      -1       0        none               4          4
RR       -2       0      -1        none               4          4
RL       -2       0      +1        none               4          4
```

Occurrence 2 took the *side* of the child from the sign of the child's own
balance factor. With `bf(t) == 2` the problem is entirely on the left, so `r` is
a node that does not participate in the rotation. Occurrence 3 relaxed the
*magnitude* at the root, where `>= 0` admits `0` and `+1` — healthy trees —
after the relaxation had been correctly reasoned out for the child, where `0` is
genuinely ambiguous under delete.

**The rule, checkable by eye, one guard at a time:**

```text
  bf(t)       chooses WHETHER to rotate      == 2 / == -2 only, never a range
  bf(child)   chooses SINGLE or DOUBLE       a direction, so >= 0 / <= 0
  the side    comes from bf(t), always       LR reads l, RL reads r
```

The case name has two letters. The first comes from the root and picks the
child; the second comes from that child and picks single against double. Every
occurrence above used one letter to do the other's work.

**Why the build does not catch it.** Four reasons, and the last is the expensive
one:

  - Guards are arbitrary boolean expressions. An unreachable guard is not an
    unreachable *case*, so the exhaustivity checker has nothing to say and
    `-Wall -Werror` is silent.
  - `case other => other` makes the match total, so a tree that matches no
    guard is returned unrotated rather than raising `MatchError`. The failure
    mode is silence.
  - **The order law survives every one of these.** Not rotating preserves the
    in-order walk perfectly, so `preservesOrder` and any content assertion pass.
    Occurrence 1 passed 2 of the spec's 5 tests; the two it passed were the ones
    that check what the tree *holds*.
  - Occurrence 3 passed **all four** rotation fixtures and failed only on the
    tree that needed no rotation — the fixture an author is least likely to
    write, because it looks like it is testing nothing.

---

## 19. A step delegated to a primitive that does not maintain the invariant

A function exists to establish an invariant its building block does not have.
It then calls that building block for the recursive step, and the invariant is
established only at the single node the function touched itself.

| # | Where | What was written | What was meant |
| :-- | :--- | :--- | :--- |
| 1 | `Avl.insertBalanced` | `rebalance(t.insert(x))` | a descent that rebalances at every level |
| 2 | `Avl.insertBalanced` | `case ... => rebalance(Branch(v, l.insert(x), r))` | `... insertBalanced(l, x) ...` |

Occurrence 2 is the instructive one. It was written *as a correction of
occurrence 1*, has the shape of a recursive function, and is the same function:
`MyTree.insert` is defined as `Branch(v, l.insert(x), r)`, so the rewrite
expanded the call by hand and changed nothing. The suite agreed to every digit:

```text
                                        occurrence 1   occurrence 2
  ascending input, Avl depth                    2,049          2,049
  descending input, Avl depth                   2,049          2,049
  one insert into the AVL tree                 49,264         49,264
  one insert into the degenerate tree          98,344         98,344
  ratio                                         2.0 x          2.0 x
```

`2,049` is `n/2 + 1`: each insert adds a level and the single root rotation
removes one, so the tree grows a level every second insert. Half a spine is
still a spine.

There is a second consequence that outranks the depth. `rebalance` documents a
precondition — both subtrees already satisfy AVL — and `MyTree.insert` does not
establish it. The call was outside its contract, which is why the result is not
merely tall but invalid: the first ascending insert leaving a non-AVL tree is
the **sixth**.

**The rule.** When a function's purpose is to maintain an invariant that its
primitive does not, the recursive call must be to *itself*. Grep the body for
the function's own name; if it is absent, the invariant holds at one node.
Correspondingly, read the primitive's precondition and ask which call
establishes it — "rebalance on the way up" is not a style preference, it is what
makes each call legal.

**Why the build does not catch it.** The two spellings have identical types, and
`MyTree[A] => MyTree[A]` says nothing about balance. The BST invariant, the
in-order walk and `size` are all correct throughout — only depth and the
per-node invariant differ, and both need an assertion someone chose to write.
The spec here has one (`isAvl` at every node), which is the only reason the
defect surfaced at all; a suite asserting sorted contents alone is green.

---

## 20. An allocation budget met by escape analysis rather than by construction

The measured allocation matches the model, and the agreement is the JIT's doing.
Nothing in the source guarantees it, so it lapses when the profile changes — on
another machine, in another test order, in a longer-running process.

| # | Where | What was written | What was meant |
| :-- | :--- | :--- | :--- |
| 1 | `Avl.insertBalanced` | `x < v` under `infixOrderingOps` | `ord.lt(x, v)` |
| 2 | `MyTree.insert`, `MyTree.contains` | `x < v`, `x > v` | `ord.lt`, `ord.gt` |

`x < v` on a generic `A` is not an operator. It expands to
`new ord.OrderingOps(x) < v`, and `OrderingOps` is an inner class of `Ordering`
carrying an `$outer` reference: 24 bytes, one per comparison, one comparison per
level. Escape analysis usually proves it does not leave the method and scalar
replacement deletes it — usually.

Measured both ways, with and without `-XX:-DoEscapeAnalysis`:

```text
                                      EA on      EA off     difference
  degenerate (4,096 deep), x < v     98,344     294,952     4096 x 2 x 24
  degenerate (4,096 deep), ord.lt    98,344      98,344     0
  AVL (13 deep), x < v                  352         976       13 x 2 x 24
  AVL (13 deep), ord.lt                 352         352     0
```

The deciding variable is the **size of the method containing the comparison**,
not the depth of the structure. `insert` is small, inlines, and wins. Adding
`rebalance` — which drags in `balanceFactor`, `depth` and four rotations —
pushes `insertBalanced` past the inlining budget, and it sat exactly on the
threshold:

```text
  insertBalanced, measured alone            400 bytes    (model ceiling 408)
  insertBalanced, after four other tests    712 bytes    = 400 + 13 x 24
```

Same source, same JVM, same run. The 312 bytes appeared because four earlier
tests changed what the JIT had profiled.

**The rule.** An allocation budget is met only if it survives
`-XX:-DoEscapeAnalysis`. Run the probe once with the flag: a number that moves
is a number the optimiser is holding up. On a hot path, call the type class
method directly rather than through a syntax wrapper — `ord.lt(x, v)` allocates
nothing under any flag, and the implicit conversion import can then leave the
file.

**Why the build does not catch it.** It compiles cleanly, the model is correct,
and the measurement agrees with it — in isolation. The test that fails is the
same test that passed, with no source change between the two runs, which makes
the first instinct "flaky test" rather than "unstated dependency". Occurrence 2
is worse: it never failed at all. Module 2's measurements read 24 bytes per node
and matched `Footprint` exactly, for four exercises, while depending on an
optimisation that was never mentioned in the model.
