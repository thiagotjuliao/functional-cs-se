# Block 1 — Error Patterns

The recall set asks whether the mechanism is understood. The challenge log asks
whether it can be derived. Neither asks the question this file asks:

> **Where does composition go wrong, once the mechanism is already known?**

Every entry below was a real defect written while working this block. None of them is
ignorance of a mechanism. `LongBytes = 16` was written by someone who knows a
`Long` is 64 bits; the error is in the conversion, not in the knowledge.

That is why this file is organised by **pattern** rather than by exercise. Nine
individual mistakes are a diary and nobody rereads a diary. Six recurring shapes
are a review checklist.

Each entry carries four things: what the pattern is, the occurrences that
instantiated it, the rule that prevents it, and — the field that makes this
usable — **why the compiler and the test suite do not catch it**. All six
compile cleanly under `-Wall -Werror`.

| # | Pattern | Occurrences | Caught by the build? |
| :-- | :--- | :-: | :--- |
| 1 | Bit-to-byte conversion written from memory | 3 | two of three |
| 2 | Two quantities that coincide under the current configuration | 1 | no — a test passed for the wrong reason |
| 3 | Off-by-one in a limit | 1 | no — the boundary has no call site |
| 4 | A constant is only as tested as the arithmetic that exposes it | 2 confirmed, 6 latent | no — one latent since pinned, five documented |
| 5 | A generator built inside the by-name parameter it should drive | 1 | no — a test passed on a degenerate input |
| 6 | A contract no implementation of that signature can satisfy | 2 | no — contracts are prose, and the suite samples the interior |

Patterns 1–6 were found in **Module 1**. Module 2's section is at the foot of
the file, opened empty on purpose: the file is created with the module, not at
the end of it, so that a defect fixed in conversation has somewhere to go the day
it appears.

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

Both occurrences are now repaired in the contract rather than the body, and both
boundaries are pinned by a test — including, for `align`, an assertion on the
behaviour *outside* the domain, so that a future change to the signature is
confronted with what it would be replacing.

---

## How to use this file

Read it before committing, not after a defect. Six questions, one per pattern:

1. Is there a width in this diff that I converted in my head?
2. Does any call pass several same-typed arguments positionally?
3. Does any comparison sit on a boundary that no test exercises?
4. Did I add a constant, and can I name the assertion that would fail if it were
   wrong?
5. Does any by-name argument construct state that should have been constructed
   once?
6. Does any Scaladoc say "every input", and does it hold at `NaN`, at
   `MaxValue`, at `MinValue`?

---

## Module 2 — Manual Persistent Data Structures

*Empty at the time of writing, and that is the point: this section exists before
the first defect does.*

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
