# A1 — Conceptual Challenge Log

The Step 4 audit of the module routine asks precise technical questions about
design choices, and the answers are where the understanding actually lives. A
green test suite proves the code works; it proves nothing about whether the
author can say *why*. This file is the record of those answers.

It is the evidence behind **§G, Oral Defence** in [`checklist.md`](checklist.md).
That box closes when every exercise has an entry here.

Every number below was executed and verified before being written — bytecode
read with `javap -c -p`, timings taken from the `medianNanos` harness in
`Exercise4PopCountSpec`, arithmetic checked against an independent oracle.

| Exercise | Challenges | Status |
| :--- | :--- | :--- |
| E1 `Bits` | 3 | recorded |
| E2 `TwosComplement` | 3 | recorded |
| E3 `PowersOfTwo` | — | closed on the suite alone, no Step 4 round |
| E4 `PopCount` | 3 | recorded |
| E5 `BitAdder` | 3 | recorded |
| E6 `BitSet64` | 7 | recorded |
| E7 `Packing` | 3 | recorded |
| E8 `BitmapIndex` | 3 | recorded |
| E9 `VarIntCodec` | 3 | recorded |
| Carried over from the removed Self-Check | 2 | recorded |

---

## E1 — `Bits`

### 1. What does `testBit(1, 32)` return, and which instruction is responsible?

**`true`** — not `false`, which is what the arithmetic reading predicts.

Shifting a 32-bit value right by 32 positions ought to push the single set bit
out of the word and leave `0`. It does not, because JVMS §6.5 defines the shift
opcodes to use only the **low five bits** of the distance operand:

```text
   32          =  100000
   low 5 bits  =  00000  =  0
   executed    :  (1 >> 0) & 1 == 1   ->  true
```

The instruction responsible is `ishr`, and the interesting part is that there is
nothing to see:

```text
   testBit(int, int):
      0: iload_1
      1: iload_2
      2: ishr          <-- the masking happens here, invisibly
      3: iconst_1
      4: iand
      5: iconst_1
      6: if_icmpne 13
      9: iconst_1
     10: goto 14
     13: iconst_0
     14: ireturn
```

No `iand` with `31` appears anywhere. A reader disassembling the method and
looking for the mask concludes there is none and that the shift is pure
arithmetic. The masking lives in the *specification of the opcode*, not in the
instruction stream, which is exactly why the trap survives inspection.

The consequence is silent aliasing rather than failure: `testBit(x, 32)` is
`testBit(x, 0)`, `testBit(x, 33)` is `testBit(x, 1)`. No exception, no zero, no
signal that anything went wrong. The precondition `[0, 31]` must live in the
Scaladoc because no layer below will check it.

Five bits because a 32-bit operand admits distances `0..31`, and five bits
address exactly 32 values — the width of the operand fixes the width of the
counter. `Long` shifts (`lshl`, `lshr`, `lushr`) mask to **six** bits, so
`1L << 64 == 1L` and the aliasing there has period 64.

The rule was not chosen for elegance. x86-64 shift instructions mask the count
to five bits for 32-bit operands, in silicon. The JVM adopted the behaviour of
the underlying machine, so the masking costs **zero instructions** — there is
nothing to emit. A specification requiring `x >> 32 == 0` would burden every
shift in the system with a test nobody wanted to pay for.

### 2. Which of the two laws in `clearBit`'s Scaladoc holds?

**Both** — the Scaladoc asks "which" in the singular, and that is the trap.

They collapse by mirrored routes. With `m = 1 << i`:

```text
   Law A:  clearBit(setBit(x,i), i)
           = (x | m) & ~m
           = (x & ~m) | (m & ~m)      -- & distributes over |
           = (x & ~m) | 0             -- m & ~m == 0     (annihilator of &)
           = x & ~m                   -- 0 is the identity of |
           = clearBit(x, i)                                        OK

   Law B:  setBit(clearBit(x,i), i)
           = (x & ~m) | m
           = (x | m) & (~m | m)       -- | distributes over &
           = (x | m) & -1             -- m | ~m == -1    (annihilator of |)
           = x | m                    -- -1 is the identity of &
           = setBit(x, i)                                          OK
```

Law A uses `m & ~m == 0`; Law B uses the **dual**, `m | ~m == -1`. Substituting
one for the other does not work: `(x & ~m) | m` contains no `&` between `m` and
`~m` for the annihilator to act on.

The step worth noticing is the second distributivity. `&` over `|` has a direct
arithmetic analogue — it is `a*(b+c) = ab + ac`. **`|` over `&` has none**: in
arithmetic, `a + (b*c) != (a+b)*(a+c)`. That is a lattice property, not a ring
property, and it is precisely where intuition imported from arithmetic fails
silently. Boolean algebra is self-dual; arithmetic is not.

The Scaladoc's prose names a **third** statement, distinct from both listed
laws: `clearBit` as a *left inverse* of `setBit`, i.e.
`clearBit(setBit(x,i), i) == x`. By Law A that reduces to
`clearBit(x,i) == x`, true only when bit `i` was already clear.

The structural reason is that `setBit(., i)` is **not injective** — it maps `x`
with bit `i` clear and `x` with bit `i` set to the same result. A non-injective
function has no left inverse on any domain; the information was destroyed.

So the two laws say nothing about inversion. They say **the last write wins**:
both functions are constant on that bit and the identity on the other 31, and
composing two constants leaves the second. The algebra of `INSERT OR REPLACE`,
not the algebra of a group.

This separates `toggleBit` from the other two for good: it *is* injective — a
bijection, and its own inverse — precisely because it depends on the previous
bit rather than overwriting it.

### 3. `testBit` must not branch, yet the bytecode has `if_icmpne`. Where does the branch die?

It survives scalac, the bytecode, and C1. It dies in **C2**, by if-conversion
into a `setcc` (`sete`/`setne`), which writes `0` or `1` into a byte from the
flags register without altering control flow.

The branch is not the author's fault. The JVM has no instruction that
materialises a `boolean` from a comparison — there is no `setcc` in the
bytecode — so *every* `Int => Boolean` conversion costs a branch at that level.
Rewriting the test as `!= 0` changes nothing.

Here a stronger outcome is likely. C2 knows that `AndI(x, 1)` has type
`int:0..1` in its lattice, and a Java `boolean` **is** the int `0` or `1`; there
is no separate representation. So the whole expression is

```text
   v in {0,1};   result = (v == 1) ? 1 : 0   ==   v
```

— the comparison is the identity and need not generate any instruction at all.
`setcc` is the correct general answer for `Int => Boolean`; this particular case
has a stronger ending because the operand arrives already normalised.

Two consequences worth holding on to. Before C2, the branch is **real**: the
interpreter executes it as written, and C1 emits the jump too. A method called
32 times per render behaves differently in its first few thousand invocations
than in steady state.

And had the branch survived, inside `toBinaryString`'s 32 iterations over the
bits of an arbitrary word it would be the **worst case for a predictor**: bits
uncorrelated, roughly 50% each way, ~15-20 cycles of pipeline flush per miss, on
a function whose useful work is three instructions.

---

## E2 — `TwosComplement`

### 4. Rewrite `sameSign` in one operation and one comparison.

```scala
(x ^ y) >= 0
```

Bit 31 of `x ^ y` is `sign(x) ^ sign(y)`, which is `0` exactly when the signs
agree. A comparison against zero on a signed integer reads precisely that bit —
in bytecode, `ixor` followed by `ifge`, with no shift and no mask.

The other 31 bits of `x ^ y` are meaningless: a bitwise combination of the two
magnitudes. They cost nothing, because nothing reads them.

The lesson inverts the usual instinct: **do not clean up bits nobody will look
at.** The first implementation applied `signMask` to both operands, spreading
each sign across all 32 positions, combined the two walls, and then asked only
whether the result was `-1` or `0` — discarding 31 of the 32 bits it had just
computed. `signMask` earns its cost in `absBranchless`, where every bit
participates in the `^` and the subtraction. Here one bit is consulted, so
spreading is work thrown away. The question to ask before reaching for a mask is
always: *how many bits of the result will anyone read?*

Zero needs no special case. Its sign bit is `0`, so `sameSign(0, 5)` is `true`
and `sameSign(0, -5)` is `false` — the Scaladoc's "zero counts as non-negative"
falls out of the representation.

### 5. Do `floorDiv2` and a literal `x / 2` compile to the same instructions?

**No.** Recorded in full under §E, *Bytecode reading*, in
[`checklist.md`](checklist.md). In summary: `iconst_1; ishr` against
`iconst_2; idiv`. They are different functions, not two spellings of one — `/`
truncates toward zero and `>>` rounds toward negative infinity, so they disagree
on every negative odd input and agree everywhere else.

### 6. What would `signMask` return with `>>>`, and where does `(x ^ m) - m` break?

`x >>> 31` yields `0` or **`1`**, never `-1`. Verified:

```text
     x     m (with >>>)   (x ^ m) - m     correct
    -5           1             -7             5
    -1           1             -3             1
  -128           1           -128          (128)
     5           0              5             5
```

`abs` returning a **negative** for every negative input. Note the last row:
positives stay correct, because there `m == 0` and the expression degenerates to
the identity. The defect hides entirely in the negative half of the domain and
survives any suite built from positive inputs.

The cause is scale, the same distinction that separates `x ^ 1` from `x ^ -1`:
`1` sets one bit, `-1` sets all of them. Both roles of `m` in the expression
require the wall. `x ^ m` must produce `~x`, and `x ^ 1` only flips bit 0.
`- m` must add one, and `- 1` subtracts one. `>>>` cannot produce `-1`, so
neither role can be filled.

---

## E3 — `PowersOfTwo`

No Step 4 round was run; the exercise was closed on its test suite alone. The
gap is recorded rather than hidden. Candidate questions, should it be revisited:
why the bit-smearing cascade needs no early exit, why the initial `x - 1` is
part of the algorithm rather than hygiene, and why the branchless binary search
must distinguish the probe from the state.

---

## E4 — `PopCount`

### 7. Why does `kernighan` terminate for `Int.MinValue`?

`Int.MinValue - 1` **overflows**, to `Int.MaxValue`. Subtracting one produced a
larger number under the signed reading. Shown in the 8-bit analogue:

```text
   n          1000 0000  =  128   (signed: -128)
   n - 1      0111 1111  =  127   (signed:  127)   <- LARGER than n
   n & (n-1)  0000 0000  =    0
```

Any termination argument of the form *"the value decreases each step, and a
value that decreases reaches zero"* is refuted by that middle line.

The correct argument never mentions magnitude. The governing rule is

> `x & (x - 1)` clears the rightmost set bit

which is a statement about **bit patterns**, and the borrow mechanics behind it
mention no sign anywhere. Subtraction in `Z/2^32` is total: it always exists and
simply wraps. What "overflows" is only the signed interpretation layered on top;
the bits do what they always do.

The measure that decreases monotonically is the **population count**. It starts
at most 32, falls by exactly one per step, and is bounded below by zero. That
proves termination for every `Int`, with no special case. `Int.MinValue` has one
set bit, so the loop clears it once and returns `1`.

This is the same lesson as `absBranchless`: `Int.MinValue` is exceptional only
when reasoned about as a magnitude. As a bit pattern it is an ordinary value.

### 8. Why does the final multiply perform four additions at once?

Two elementary facts combine. Multiplying by a power of two is a shift, and
multiplication distributes over addition. Decomposing the constant:

```text
   0x01010101  =  2^24 + 2^16 + 2^8 + 2^0

   z * 0x01010101  =  (z << 24) + (z << 16) + (z << 8) + z
```

One multiply *is* four shifted copies of `z`, summed.

After the third SWAR step, `z` holds four independent per-byte counts, each in
`0..8`. Writing them `[d][c][b][a]` from high byte to low, and lining up the
four terms:

```text
   z << 0    [ d ][ c ][ b ][ a ]
   z << 8    [ c ][ b ][ a ][   ]
   z << 16   [ b ][ a ][   ][   ]
   z << 24   [ a ][   ][   ][   ]
             -----
             byte 3
```

Byte 3 of the four terms is `d`, `c`, `b`, `a`. Byte 3 of the sum is therefore
`a + b + c + d` — the total population count. `>>> 24` extracts it.

Nothing overflows because the column sums are bounded:

```text
   byte 0:  a              <=  8
   byte 1:  a + b          <= 16
   byte 2:  a + b + c      <= 24
   byte 3:  a + b + c + d  <= 32
```

The largest is `32` against a byte's capacity of `255`, so **no carry ever
crosses a lane boundary**. That slack is what makes the trick valid — it works
for population counts and would not work for summing arbitrary byte fields.
Bits above byte 3 are discarded by the width of `Int`, which is irrelevant since
only byte 3 is read.

The name for this is a **horizontal sum**: the first three steps add vertically
within widening fields, and the multiply collapses the four lanes sideways.
Real SIMD instruction sets offer it as a primitive; here it is fabricated from
the integer multiplier, which already knows how to do it.

Verified: the four constants are exactly `0x55555555`, `0x33333333`,
`0x0f0f0f0f` and `0x01010101`, and the implementation agrees with a reference
population count on roughly 370,000 inputs including `0`, `-1`, `Int.MinValue`
and `Int.MaxValue`.

### 9. Predict the ranking before measuring.

Predicted: `swar` fastest, `kernighan` varying with density, `naive` slowest.
**Two of three.** Measured medians, ns per 1000 calls:

```text
   intrinsic            2600
   swar                 2700
   swar/sparse          3000
   kernighan/sparse     3600
   naive                6500
   kernighan            8400
   kernighan/dense     13100
```

`swar` is fastest of the three, and `kernighan` swings by a factor of `3.6x`
between sparse and dense while `swar` moves within noise. But **`naive` beats
`kernighan`** on random input — 6500 against 8400 — despite performing 32
iterations against an average of 16 for a random 32-bit word. Half the work,
30% slower.

Two causes, both already met elsewhere in this annex:

**Serial dependency chain.** Each `kernighan` iteration needs the `n` produced
by the previous one: `n & (n - 1)` cannot begin until its predecessor finishes.
`naive`'s 32 tests are **independent** — each inspects a different bit of the
same original `x` — so they pipeline, and the loop has a constant trip count
that C2 can unroll.

**Unpredictable exit branch.** `kernighan` runs a number of iterations that
depends on the data, from 0 to 32, varying per call. The predictor has no
pattern to learn and misses the exit on essentially every call.

Fewer operations lose to more parallelism. "Fewer operations" and "faster" are
different quantities once one of them branches on the data.

**The intrinsic barely won**, at 2600 against `swar`'s 2700, when `POPCNT` is
one cycle and `swar` is roughly twelve operations. That is an artefact of the
experiment, and the test's own comment warns of it in advance: all seven
candidates are invoked through a single `Int => Int` reference, making one
**megamorphic call site**. C2 cannot inline any of them and emits virtual
dispatch, whose cost swamps the difference between one cycle and twelve. Only
large differences — such as `kernighan`'s iteration count — survive the noise.

The controlled experiment confirms the intrinsic was real. With
`-XX:-UsePopCountInstruction`:

```text
                    normal     intrinsic off
   intrinsic          2600           3200
   swar               2700           3100
   naive              6500           6600
   kernighan          8400           8100
```

`Integer.bitCount` becomes **indistinguishable from `swar`** — because the Java
body of `Integer.bitCount` in the JDK *is* this same SWAR algorithm, with the
same four constants. With the intrinsic on, C2 replaces the whole call with one
`POPCNT`; with it off, C2 compiles the actual Java source, and the two converge.

Caveat: the delta is `0.6 ns/call` against a run-to-run spread of roughly
`0.4 ns` on the untouched candidates. Directionally clear, at the edge of this
harness's resolution. JMH exists to separate exactly this.

---

## E5 — `BitAdder`

### 10. Why does the carry word strictly shrink, and why is 32 an upper bound?

"An `Int` is 32 bits wide" is the *bound*, not the argument. Width alone proves
nothing: the first draft of `multiply` used `y >> 1` to consume the multiplier
and looped forever on negative inputs — also a loop over a 32-bit word. A
termination proof needs a quantity that moves toward the bound on every
iteration, and the width only says where the bound is.

The quantity is the **count of trailing zeros** of the carry word — equivalently,
the position of its lowest set bit. `Integer.numberOfTrailingZeros` is the JDK
name for it.

Look at where the strictness comes from, because the two operations in
`b_ = (a & b) << 1` contribute differently:

```text
   a & b     can only clear bits    ->  the count cannot decrease   (non-strict)
   << 1      injects a zero at the bottom
                                    ->  the count rises by at least 1  (strict)
```

`&` alone would give only weak monotonicity, and weak monotonicity does not
prove termination — a loop can sit at one position forever. The `<< 1` is what
guarantees movement on *every* iteration, whatever `&` did.

Traced in 8 bits, computing `0111 1111 + 1`, the width's worst case:

```text
   it   a           carry b      position of the carry's lowest set bit
    0   0111 1111   0000 0001              0
    1   0111 1110   0000 0010              1
    2   0111 1100   0000 0100              2
    3   0111 1000   0000 1000              3
    4   0111 0000   0001 0000              4
    5   0110 0000   0010 0000              5
    6   0100 0000   0100 0000              6
    7   0000 0000   1000 0000              7
    8   1000 0000   0000 0000        -- carry empty
```

The carry is a front marching left, one position per iteration, never
retreating. On the eighth turn it leaves through the edge and the word is zero.
Eight positions, eight iterations; at 32 bits, 32.

The bound is reached exactly when the loop exits, and that is not a coincidence
to be reconciled: a 32-bit word with 32 trailing zeros **is** zero, which is the
exit condition. The measure hitting its ceiling and the loop terminating are the
same event.

Measured: instrumenting the recurrence over 200,000 random pairs plus the
boundary cases (`-1 + -1`, `Int.MinValue + Int.MinValue`, `0 + 0`) gives a
maximum of **exactly 32**. The worst case exists and is reachable — `add(-1, -1)`
propagates a carry across all 32 positions because every one of them is set.

Compare with `kernighan`, which has the same shape of argument with a different
measure:

| | measure | direction | limit |
| :--- | :--- | :--- | :--- |
| `add` | trailing zeros of the carry | rises | 32 = word is zero |
| `kernighan` | population count | falls | 0 = word is zero |

In both cases the measure is a property of the **bit pattern**, never the
magnitude — which is precisely what survives the overflow at `Int.MinValue`.

### 11. Make `multiply`'s accumulation branchless.

```scala
val acc_ = add(acc, x & negate(y & 1))
```

`y & 1` is `1` or `0`; `negate` maps those to `-1` or `0`; `x & mask` is then `x`
or `0`; and the addition runs unconditionally. When the bit is clear the code
adds zero, which is harmless and cheap — `add(acc, 0)` exits on its first test
without entering the loop.

The method now contains **one branch in its entire body**, the `y == 0` exit
test, and that one is perfectly predictable: it is taken once per call.

The mask had to be built from `negate` rather than a unary minus, since `-` is
forbidden in this object. The constraint has a side effect worth noticing: it
makes the dependency explicit in the source. `multiply` rests on `negate`, which
rests on `add`, which rests on `^`, `&` and `<<`. The whole tower is visible.

This is the third source the same technique has been fed from in this annex:

| exercise | where the predicate comes from | how the mask is built |
| :--- | :--- | :--- |
| E2 `absBranchless` | the sign bit | `x >> 31` |
| E3 `log2Floor` | "is anything left above?" | `signMask(-t)` |
| E5 `multiply` | bit 0 of the multiplier | `negate(y & 1)` |

Three origins, one pattern: **produce `0` or `-1`, then let `&` execute the
decision.**

### 12. Why does `multiply(a, -1)` cost 32 iterations and `multiply(a, 1)` cost 1?

Measured trip counts of the outer loop:

```text
   b =           1   ->   1
   b =          13   ->   4
   b =        1024   ->  11
   b =  2147483647   ->  31
   b =          -1   ->  32
   b =          -5   ->  32
   b = -2147483648   ->  32
```

The count is **not** the population count — that is what `kernighan` measures.
`1024` has a single set bit and costs 11 iterations. The loop shrinks `y` by
`>>> 1` until it reaches zero, so what governs the cost is the position of the
**highest** set bit: the bit length of `y`, not its weight.

And the reading is **unsigned**, because `>>>` is a logical shift. It does not
know a sign bit exists; it treats the word as a pure magnitude:

```text
   b = -1   read signed:    magnitude 1,   highest bit at position 0    ->  1?
   b = -1   read unsigned:  2^32 - 1,      highest bit at position 31   ->  32
```

The measured answer is 32. The unsigned reading governs.

The consequence is the point of the challenge:

> **"Multiplying by a small number is cheap" is false.**

`-1` is the smallest possible multiplier by magnitude and the **worst case** by
cost. `-5`, `Int.MinValue`, any negative: always 32 iterations, because every
negative has bit 31 set and therefore maximal length as an unsigned magnitude.
Magnitude does not predict cost; unsigned bit length does. The same theme as
everywhere else in this annex — the magnitude intuition is the wrong one, the
bit-pattern intuition is the right one.

A footnote on the `O(log b)` that the literature attaches to shift-and-add. At a
fixed width the logarithm is decorative: `log2(b) <= 32` always, so `O(log b)`
**is** `O(1)` — asymptotics over a fixed-size type distinguish nothing. The
notation only carries content in arbitrary-precision arithmetic, where `b` grows
without bound.

What remains here is the **constant**, and it varies by a factor of 32 between
best and worst case. That is why E4's measurement taught more than an operation
count would have: at a fixed width, the constant is the whole story.
---

## E6 — `BitSet64`

### 13. Why is `(P(U), symDiff, Empty)` a group when `(P(U), union, Empty)` is not?

Both are closed, associative, commutative and carry `Empty` as identity. The
axiom that separates them is **inverse**: for a given `s`, is there a `t` with
`s union t == Empty`?

There is not, except for `s = Empty`. Union only adds; nothing unioned onto
`{1,2}` will remove the `1`. An operation that cannot undo forms a commutative
idempotent monoid and stops there.

Symmetric difference has inverses, in the strongest possible form — **every
element is its own**:

```text
s symDiff s == Empty        for every s
```

Verified over 1000 random 64-bit words: the inverse law and associativity both
hold 1000/1000.

The structural name for this is worth carrying into Block 2. With `symDiff` as
addition, the powerset is a vector space over the two-element field:

```text
P({0..63})   ~=   (Z/2Z)^64        one coordinate per bit
symDiff       =   vector addition
Empty         =   the zero vector
```

A field of **characteristic 2**: `x + x = 0` for all `x`. "Every element is its
own inverse" is not a curiosity of the operation — it is the characteristic of
the field. And it is why `union` could never qualify: `union` is not addition in
any field, it is the join of a lattice, and lattices have no inverses.

At the level of a single bit the whole thing collapses to one line of the truth
table: `1 ^ 1 = 0`.

### 14. Why does `(s union t) - (s intersect t)` compute the symmetric difference, given that `-` is arithmetic subtraction?

Because **`s intersect t` is always a subset of `s union t`**, and that
inclusion forbids every borrow.

Walk binary subtraction position by position, with `A` the minuend and `B` the
subtrahend:

```text
position    A     B     result     borrow needed?
   B=1      1     1       0             no
   B=0      1     0       1             no
   B=0      0     0       0             no
   B=1      0     1      ---      impossible: B is a subset of A
```

The fourth row is the only one that would borrow, and the inclusion rules it
out. With no borrow, no position influences any other — and a subtraction that
propagates nothing between positions **is** an XOR. Hence

```text
(s union t) - (s intersect t)  ==  (s union t) symDiff (s intersect t)  ==  s symDiff t
```

The formula `(A union B) minus (A intersect B)` is the correct definition of
symmetric difference. What happened here is that `-` stood in for set
difference and the inclusion made the substitution sound.

It is sound *only* under that inclusion. Remove it and the expression
disintegrates — verified, showing the low 8 bits (the real damage runs to bit
63):

```text
s           t         s - t (arithmetic)     s minus t (correct)   t subset of s?
---------   -------   --------------------   -------------------   --------------
{0}         {1}       {0..63}  (Full)        {0}                   no
{0,1,2}     {2,3}     {0,1,3,4,5,6,7,...}    {0,1}                 no
{1,3}       {0,2}     {0,2}                  {1,3}                 no
{0,1,2,3}   {0,1}     {2,3}                  {2,3}                 yes
```

`{0} - {1}` is `1 - 2 = -1`, which as a set is the entire universe. A set
difference returning `Full`.

The engineering point outranks the arithmetic one: inside an `opaque type` over
`Long`, the arithmetic operators remain in scope and remain legal. Nothing in
the type stops `-` from appearing where set difference was meant.

### 15. `diff` was computing the symmetric difference. Why did twelve passing tests not say so?

They could not run. `Exercise6BitSet64Spec:141` is exactly the assertion that
catches it:

```scala
assertEquals((a diff b).raw, (a intersect b.complement).raw, "diff is not a & ~b")
```

but it dies on `NotImplementedError` from the `???` in `complement` before
reaching the comparison. The test that would have caught the bug was written,
present, and mute.

The bug was found by hand instead, on `s = {0,1,2}`, `t = {2,3}`:

```text
s union t      = {0,1,2,3}   ->  0b1111  =  15
s intersect t  = {2}         ->  0b0100  =   4
                                            --
                                 15 - 4  =  11  ->  0b1011  ->  {0,1,3}

symmetric difference  {0,1,3}    <- what it returned
relative complement   {0,1}      <- what `diff` promises
```

The correct implementation is `s & ~t`, written in the file as
`s intersect t.complement` so that the law stays legible.

**A test that cannot execute is not a safety net.** Ordering matters: a `???`
anywhere in a chain of laws silences every law downstream of it.

### 16. `-s` is an involution. Why is it not the complement?

Because involution alone does not characterise the complement, and the naive
check does not notice. Measured over 1000 random 64-bit words with
`complement(s) = -s`:

```text
law                                    passed
------------------------------------   ---------
involution   f(f(x)) == x              1000/1000
covering     s union c   == Full        498/1000
disjoint     s intersect c == Empty       0/1000
```

The involution law is satisfied perfectly. Worse, the covering law passes on
roughly half of all inputs — including the small sets one picks by hand:

```text
s          -s         -(-s)      | involution   s|c == Full   s&c == Empty
--------   --------   --------   | ----------   -----------   ------------
00000000   00000000   00000000   | yes          NO            yes
00000001   11111111   00000001   | yes          yes           NO
00000110   11111010   00000110   | yes          NO            NO
00000111   11111001   00000111   | yes          yes           NO
00001010   11110110   00001010   | yes          NO            NO
```

`{0}` and `{0,1,2}` both satisfy `s | complement == Full`. Two hand-picked
cases, two passes, and a wrong implementation ships.

The disjunction law reads **0/1000**, and that is not bad luck. `s & -s` is the
expression from E3 that **isolates the lowest set bit**, so it is zero if and
only if `s` was already zero. `s intersect complement(s) == Empty` is
arithmetically impossible with `-s` for any non-empty `s`.

The lesson generalises past this exercise: the four assertions in the
`complement` test are not four checks *of* the complement, they are its
**definition**, written in pieces. Exactly one function satisfies all of them.
The value of a law is not that it is stricter per case — it is that **the author
stops choosing the cases**.

### 17. Both `of` and `toList` are folds over the same opaque type. Why is only one of them primitive?

The bootstrap methods of `Sets$package$BitSet64$`, read with `javap -v`:

```text
of$$anonfun$1              (long, int) -> long             primitive throughout
toList$$anonfun$adapted$1  (Object, Object) -> Tuple2      note "adapted"
```

The `adapted` suffix is the compiler recording that it had to insert box/unbox
adapters.

The cause is the **type of the accumulator**, not of the element. `of` folds
into a `BitSet64`, which after erasure is a `long`, and a `long` is what the
lambda carries. `toList` folds into `(BitSet64, List[Int])`, and `Tuple2` stores
its components as **references** — `_1` is typed `Object` in the bytecode. A
primitive `long` does not fit in a reference, so every iteration pays for the
round trip.

`javap -c -p` on the fold's lambda, annotated:

```text
 5: boxToInteger          box the Range index
 8: Tuple2$.apply         allocate the incoming tuple
32: unboxToLong           unbox the BitSet64
48: Long.numberOfTrailingZeros    } the actual
63: excl                          } work
66: boxToLong             re-box the BitSet64
73: boxToInteger          box the index
76: List.$colon$plus      the append (see challenge 18)
79: Tuple2$.apply         allocate the outgoing tuple
```

Five allocations per set bit. The tuple is not expensive for being a tuple; it
is expensive for being **generic**.

For contrast, the operations that avoided a tuple erase exactly as the exercise
promised:

```text
public long union(long, long)      public long complement(long)
public long diff(long, long)       public int  size(long)
public long symDiff(long, long)    public long excl(long, int)
```

`long` throughout. No box, no header — Part VII delivered.

### 18. `toList` passes all twelve tests and violates its own Scaladoc. Where?

The Scaladoc requires *"O(size), not O(64)"*. The implementation is quadratic in
`size`, because it accumulates with `:+`.

In Scala's `List`, the costs are the opposite of the array intuition:

```text
::  (prepend)   O(1)
:+  (append)    O(n)
```

Cons cells are immutable, which forces it:

```text
prepend 9 onto [1,2,3]:   9 -> [1 -> 2 -> 3]     one new cell,
                               ^^^^^^^^^^^^^     tail is SHARED

append 9 onto [1,2,3]:    1' -> 2' -> 3' -> 9    three new cells; the last
                                                 cell must point at 9 and
                                                 cannot be mutated
```

So `n` appends cost `0 + 1 + ... + (n-1)` copies rather than `n` steps.
Computed:

```text
n (set bits)   :+ (append)   :: + reverse   ratio
------------   -----------   ------------   ------
 4                  10             8         1.2x
 8                  36            16         2.2x
16                 136            32         4.2x
32                 528            64         8.2x
64                2080           128        16.2x
```

For `Full`, 2080 cons cells where 128 suffice.

The repair is one word: `i :: acc` in place of `acc :+ i`. That breaks the
Scaladoc's **"in ascending order"** invariant, since prepending emits
descending, and a single `.reverse` at the end restores it. `reverse` is one
linear pass, so the total stays linear in `size`.

The twelve tests pass throughout, because they verify **correctness**, not
complexity and not allocation. This is the argument of this file in one
exercise: a green suite proves the code works and proves nothing about whether
the declared contract was honoured.

**Repaired 2026-09-09**, in `1978404`. `toList` now accumulates with `::` and
reverses once, and the suite stays at 12/12. The quadratic listing above is kept
as written: it is the state the audit found, and the reasoning that produced the
repair is the point of the entry.

What the repair did **not** address is the allocation. `:+` was the complexity
defect; the `Tuple2` accumulator of challenge 17 is the allocation defect, and it
is still there — the boxing per iteration is unchanged. §E of
[`checklist.md`](checklist.md) remains open on that count.

### 19. What does the Kernighan formulation remove, and what does it not?

It does **not** remove the clearing of the bit. Without it,
`numberOfTrailingZeros` returns the same index forever and the loop does not
advance.

What it removes is the **counter**: the call to `size`, the allocation of the
`Range`, and the boxing of the Range index. The stop condition becomes
`if t == Empty then acc else ...`.

Termination comes for free, and that is the part that dispenses with the
counter: every step clears exactly one set bit and no step sets one, so the word
reaches zero in exactly `popcount(s)` steps.

There is a second saving. Clearing the lowest set bit does not require knowing
its index:

```text
t          ntz(t)   t & ~(1<<i)   t & (t-1)   equal?
--------   ------   -----------   ---------   ------
10110100     2       10110000      10110000     yes
00000001     0       00000000      00000000     yes
10000000     7       00000000      00000000     yes
11111111     0       11111110      11111110     yes
```

`excl(i)` rebuilds the mask `1L << i` from the index — a variable shift.
`t & (t - 1)` clears the same bit in two instructions and never computes the
index. `numberOfTrailingZeros` is still needed to know *which element to emit*,
but no longer to remove it.
---

## E7 — `Packing`

### 20. Three masks in `Packing` compute nothing. For each, which later instruction already guaranteed the effect?

The bytecode, with the inert work marked:

```text
unpackHi   getstatic mask; ldc2_w -1L; lxor; land; bipush 32; lshr; l2i
                           ^^^^^^^^^^^^^^^^^^^^^^ the `& ~mask`

unpackLo   getstatic mask; land; l2i
                           ^^^^ the `& mask`

alpha      ldc -16777216; iand; bipush 24; iushr
                          ^^^^ the `& (-1 << 24)`
```

Three redundancies, and **three different reasons** — which is the point of the
challenge, because the expressions look alike:

```text
unpackHi   `& ~mask` is redundant because `lshr 32` DISCARDS what it zeroes
unpackLo   `& mask`  is redundant because `l2i` PRESERVES what it preserves
alpha      `& (-1<<24)` is redundant for both reasons at once
```

The middle one is where the reasoning goes wrong most easily. `l2i` is **not** a
shift, and in particular it is not `lshr 32` — the two keep opposite halves:

```text
packed              00000000000000000000000000000111 | 00000000000000000000000000001001
                              hi = 7                 |            lo = 9

(int) packed          = 9      <- l2i keeps the LOW half
(int)(packed >>> 32)  = 7      <- lshr 32 keeps the HIGH half
```

Were `l2i` a 32-bit shift, `unpackLo` would return the `hi` field. It truncates:
it keeps the low 32 bits and discards the high ones, which is exactly what the
mask was there to arrange.

All three simplifications verified over 500,000 random values plus the boundary
cases (`0`, `-1`, `MinValue`, `MaxValue`, and each half in isolation):

```text
(packed & ~mask) >> 32   vs   packed >> 32      0 mismatches
(packed & mask).toInt    vs   packed.toInt      0 mismatches
(p & (-1<<24)) >>> 24    vs   p >>> 24          0 mismatches
```

`alpha` is the instructive one. It was written *before* the technique the other
three accessors ended up using, so it kept a mask that the final shape no longer
needs — four instructions where `red` has three.

### 21. `-1 << 24` compiled to `ldc`; `mask` compiled to `getstatic`. Both are expressions over literals. What made the difference?

Not the expression — the **declaration**. A `val` in an object is a field with a
getter, read at every call. A constant folded into the expression is a compile-
time value emitted immediately.

Scala 3 offers a keyword for exactly this, and it accepts the expression rather
than demanding a literal. Compiled in this project and read with `javap`:

```text
inline val  = (1L << 32) - 1     ->   ldc2_w 4294967295L
inline val  = 0xffffffffL        ->   ldc2_w 4294967295L
final val   = (1L << 32) - 1     ->   ldc2_w 4294967295L
private val = (1L << 32) - 1     ->   getstatic Field maskPlainVal:J
```

Three forms become an immediate; only the plain `val` becomes a field. Note that
`final val` reaches the same place, which is worth knowing where `inline` is not
available — and that the compiler folds `(1L << 32) - 1` before requiring a
constant, so the readable form costs nothing against the hex literal.

The difference is small: one `getstatic` against one `ldc2_w`, and the JIT will
hoist the field read out of any loop it can see. It is recorded because it is
observable, and because the same file contains one of each — `-1 << 24` was
folded, `mask` was not, and nothing in the source hints at the asymmetry.

### 22. `packRgba` allocates two objects per valid pixel. Under what conditions does the JVM eliminate them?

The allocations, from the bytecode:

```text
None:   getstatic scala/None$.MODULE$        singleton, no allocation
Some:   boxToInteger(I) -> Integer           allocation 1
        Some$.apply(Object) -> Some          allocation 2
```

This is not a defect: the Scaladoc requires `Option`, and ground rule 2 forbids
`throw`. It is a cost worth being able to describe.

**The optimisation is scalar replacement.** C2's escape analysis assigns each
allocation one of three verdicts:

```text
NoEscape       never visible outside the method     -> can be dissolved
ArgEscape      passed to a method, but not stored
GlobalEscape   stored in a field, returned, or published
```

A `NoEscape` object need not exist. C2 dissolves it into its fields and keeps
each in a register: no `new`, no header, no GC pressure.

**The precondition is the subtle part.** Inside `packRgba` the `Some` is
*returned*, which is `GlobalEscape` by definition. On its own it can never be
eliminated. Elimination requires C2 to **inline `packRgba` into its caller**
first, because the escape analysis runs over the already-inlined graph; only in
the caller's body can the `Some` be proved not to escape. **Allocation removal is
always downstream of an inlining decision.**

**What survives.** `boxToInteger` calls `Integer.valueOf`, which returns cached
objects for `-128..127`:

```text
Integer.valueOf(127) == Integer.valueOf(127)     true    <- cached
Integer.valueOf(128) == Integer.valueOf(128)     false   <- allocated
```

A cached `Integer` is a pre-existing global: never allocated, and never
eliminable either. It barely helps here, because a packed pixel is an arbitrary
32-bit word:

```text
pixels landing in the Integer cache:  18 of 1,492,992   (0.001%)
```

**The condition under which it all fails** is the one E4's challenge 9 already
demonstrated. A call site is monomorphic with one receiver type, bimorphic with
two, and **megamorphic** from three: the inline cache gives up and C2 emits
virtual dispatch. No inlining, therefore no proof of `NoEscape`, therefore two
allocations per iteration:

```text
megamorphic call site  ->  no inlining  ->  Some never proved NoEscape
                       ->  no scalar replacement  ->  two allocations per pixel
```

The causal order is what makes this hard to spot in review. The allocation is not
decided by the code that allocates — nothing in `packRgba` changes, and nothing
in it looks wrong. It is decided by the **shape of the call site that consumes
it**.
---

## E8 — `BitmapIndex`

### 23. `IArray(child)` allocates three objects for one element. Which three, and what removes them?

The first `inserted` built the dense array by splicing three pieces together.
Its bytecode, in order:

```text
slice                  -> array 1   (the left slice)
anewarray Object       -> array 2   )
genericWrapArray       -> ArraySeq  )  this is IArray(child)
IArray.apply           -> array 3   )
$plus$plus             -> array 4
slice                  -> array 5   (the right slice)
$plus$plus             -> array 6   <- the only survivor
SparseNode.apply       -> the node
```

Six arrays allocated, five discarded at once.

The expensive part is the innocent-looking one. `IArray(child)` is a varargs
call, `apply(xs: A*)(using ClassTag)`, and a varargs argument must arrive as a
`Seq`. The compiler knows one way to build that: put the element in an array,
wrap the array in an `ArraySeq`, and let `apply` copy it back out into an array.

```text
child  ->  [child]  ->  ArraySeq([child])  ->  [child]
           array          wrapper              array
```

A full `array -> Seq -> array` round trip for an element already in hand.

The `ClassTag` in that signature is **not** one of the three. It is an implicit
parameter synthesised at the call site from the method's own `[A: ClassTag]`
bound — passed in, not allocated here.

`IArray.tabulate(n)(f)` produces the destination in one allocation, and the
index function is where the real content of the exercise sits. Reading the
destination backwards — for each position of the result, where does the element
come from? — with `children = [a,b,c]`, `index = 1`, `child = x`, giving
`[a,x,b,c]`:

```text
destination i    value    comes from
-------------    -----    -------------
      0            a      children(0)
      1            x      child
      2            b      children(1)
      3            c      children(2)
```

So `i < index` reads `children(i)`, `i == index` is the new child, and
`i > index` reads `children(i - 1)`. That `- 1` is the whole asymmetry of
insertion: from the splice point onward, every element sits one position further
along in the destination than it did in the source. The two `slice` calls were
doing that shift implicitly; `tabulate` makes it explicit and costs one array.

Measured on the compiled result:

```text
before   slice · [anewarray + genericWrapArray + apply] · ++ · slice · ++
         = 6 arrays + 1 ArraySeq
after    invokedynamic (closure) · tabulate
         = 1 array + 1 closure
```

Not zero: the lambda captures `index`, `children` and `child`, so it is not a
singleton and costs one object. But one small object replaces five discarded
arrays. `removed` still splices, and the mirrored index function would apply
there too.

### 24. `removed` clears the bit with `^`. What breaks if the guard is relaxed?

Take `bitmap = 10001001` (slots {0,3,7}) with `children = [a,b,c]`, and call
`removed(node, 5)` on the **empty** slot 5.

The damage happens before the choice of clearing operator matters:

```text
physicalIndex(bitmap, 5) = 2   ->  the code drops children(2) = "c"
                                    ...which belongs to slot 7, not to slot 5.
```

The physical index of a **vacant** slot points at the element of the next
occupied slot. Removing "from" an empty slot removes its neighbour.

The two clearing expressions then differ only in the bookkeeping:

```text
clearing        new bitmap   arity   length   invariant arity == length
-------------   ----------   -----   ------   -------------------------
^  (1<<slot)    10101001       4       2      BROKEN by 2
& ~(1<<slot)    10001001       3       2      BROKEN by 1
```

`^` **sets** the bit of a vacant slot: the bitmap starts claiming slot 5 is
occupied in the same move that the array loses an element. `& ~` is idempotent
and leaves the bitmap untouched, so it is off by one instead of two.

The two failures are of different **kinds**, which is the part worth carrying:

```text
bitmap   fails DETECTABLY and reversibly
         (arity != length announces it; the bit can be put back)

array    fails SILENTLY and irreversibly
         ("c" is gone; the result is still a valid array of length 2, and no
          invariant check can say which element went missing)
```

So choosing `& ~` over `^` is **hygiene, not safety**. It reduces the damage
from two to one and prevents nothing. Idempotence is a property of the
expression; what guarantees the operation is `Option.when(hasSlot(...))`. The
implementation is correct — the correctness lives in the guard, and the chosen
expression does not carry it alone.

### 25. Why does one `physicalIndex` serve both lookup and insertion?

Because the mask excludes the bit being asked about. `bit - 1` covers strictly
the positions **below** `slot`, so the count never inspects whether `slot`
itself is occupied. It answers exactly one question:

> how many elements come before this slot?

And one sentence covers both cases:

```text
slot occupied   k elements before  ->  the element sits at k
slot vacant     k elements before  ->  an inserted element would go to k
```

These are not two readings of an ambiguous number. It is one reading, which is
why lookup and insertion share the computation — and why the count is
meaningful for a slot that holds nothing.

Note what the count does **not** say: `k = 2` does not mean slots 0 and 1 are
occupied. It means two slots below are occupied, whichever they are. For
`bitmap = 10001001` and `slot = 5` the count is 2, and the occupied slots below
are 0 and 3.

**The structural precondition** is what makes any of this work: the dense array
must be **ordered by slot number**. The correspondence between `{occupied
slots}` and `{0 .. arity-1}` has to be an order-preserving bijection. Were the
elements held in insertion order, counting bits below would measure the bitmap
while the array was organised by an unrelated criterion, and the index would
mean nothing.

That coupling is exactly what `inserted` preserves by splicing at
`physicalIndex` rather than appending. Appending would be cheaper and would
destroy the invariant on the first out-of-order insertion.
---

## E9 — `VarIntCodec`

### 26. `decode` accepts encodings that `encode` never produces. What does that cost?

Five distinct byte strings decode to `-1`, and the encoder emits only the first:

```text
input                                          decode
--------------------------------------------  ---------
00000001                                       Some(-1)   <- canonical
10000001 00000000                              Some(-1)
10000001 10000000 00000000                     Some(-1)
10000001 10000000 10000000 10000000 00000000   Some(-1)
00000000                                       Some(0)    <- canonical
10000000 00000000                              Some(0)

encode(-1) = 00000001        encode(0) = 00000000
```

This does not violate the Scaladoc, which asks `decode` to reject truncated,
malformed and trailing input and says nothing about canonicity. Standard LEB128
behaves the same way. It is a property to know one has, not a defect.

**What it costs.** The round trip holds in one direction only:

```text
decode(encode(n)) == Some(n)     verified over 200,017 values
encode(decode(bs)) == bs         FALSE for every non-canonical bs
```

A protocol signs, hashes and deduplicates **bytes**, not values. While each value
has exactly one representation, "same value" and "same bytes" are
interchangeable and either can proxy for the other. Multiple representations
break that, and the simplest consequence is replay:

```text
original frame     00000001             hash H1  -> accepted, executed
same command       10000001 00000000    hash H2  -> passes deduplication,
                                                    executed AGAIN
```

No signature is forged and no key is guessed. An attacker rewrites the varint
into an equivalent form and the layer that guaranteed at-most-once delivery
fails to recognise the repeat, because it compares bytes while the meaning lives
one level up.

The same shape appears wherever an identity is derived from bytes: transaction
malleability in Bitcoin, merkle roots diverging between nodes that received
different forms of the same block, idempotency keys, content-addressed caches.

**The missing check** is the encoder's own rule read from the other side:

```text
an encoding is canonical  <=>  the final byte has a non-zero payload,
                               or it is the only byte
```

A final byte with a zero payload contributes no bits, so the previous byte could
already have terminated the varint. In the loop this is a condition on the
terminating branch: with `k > 0` and a zero payload, return `None`.

### 27. `decodeAt` allocates four objects per call. Which are they, and when can the JVM remove them?

```text
public scala.Option<scala.Tuple2<java.lang.Object, java.lang.Object>> decodeAt(byte[], int)
                                              ^^^^^^^^^^^^^^^^^^^^^
```

`Option[(Int, Int)]` stacks the two patterns this annex has already recorded:

```text
Some       ─┐
Integer    ─┴─ challenge 22 (E7):  Option[Int] allocates the Some and boxes the Int
Tuple2     ─┐
Integer    ─┴─ challenge 17 (E6):  Tuple2 stores references, so primitives are boxed
```

For contrast, the two methods that return primitives escaped entirely:
`public byte[] encode(int)` and `public int encodedSize(int)`.

**The condition for removal** is challenge 22's: scalar replacement needs a
`NoEscape` proof, and the `Some` is *returned* from `decodeAt`, so it escapes by
definition. It can only be dissolved if C2 **inlines `decodeAt` into its caller**
and proves there that nothing retains it.

**Two things threaten that in the spec's fold:**

```scala
values.foldLeft(...) { (acc, _) =>
  acc.flatMap { (seen, offset) =>
    VarIntCodec.decodeAt(buffer, offset).map((v, next) => (v :: seen, next))
  }
}
```

The chain is `foldLeft` → lambda → `flatMap` → lambda → `decodeAt` → `loop` →
`map` → lambda. Every link spends one level of C2's inlining budget
(`MaxInlineLevel`, 9 by default) and must also fit the size limits. If the chain
runs too deep, `decodeAt` is never inlined and none of the four allocations can
go.

Second, `foldLeft` on `List` dispatches virtually. Should that call site observe
three or more collection implementations over the life of the process it becomes
**megamorphic**, and C2 stops inlining there — the same cause that flattened the
measurements in challenge 9.

### 28. The accumulator uses `+` where the idiom is `|`. When do they coincide?

Exactly, and with one condition:

```text
a + b  ==  a | b      if and only if      a & b == 0
```

With no shared set bits no column produces a carry, and addition degenerates
position by position into OR. Verified over 500,000 random pairs with no
counterexample in either direction:

```text
a       b       a & b   a + b   a | b   equal?
-----   -----   -----   -----   -----   ------
1010    0101    0       15      15      yes
1100    0011    0       15      15      yes
1010    0110    0010    16      14      no
1111    0001    0001    16      15      no
```

**Why the loop guarantees it.** Each group occupies exactly seven positions and
the shifts are multiples of seven:

```text
k    shift   positions occupied by (payload << shift)
0    0       0..6
1    7       7..13
2    14      14..20
3    21      21..27
4    28      28..31
```

No position is claimed twice. The accumulator holds only bits below `shift`; the
new term holds only bits from `shift` up. The intersection is always empty.

**The symmetry with challenge 14** is what carries beyond this exercise:

```text
E6   (s∪t) - (s∩t)  ==  (s∪t) ^ (s∩t)     because s∩t ⊆ s∪t    -> never a BORROW
E9   acc + (p<<k)   ==  acc | (p<<k)      because disjoint     -> never a CARRY
```

A subtraction that became an XOR and an addition that became an OR, for the same
underlying reason: **when nothing propagates between positions, the arithmetic
operation degenerates into the bitwise one.** Carry and borrow are the only
coupling between columns; remove the coupling and Boolean algebra is what
remains.

The practical difference is that in E6 the coincidence was an accident that
rescued a wrong expression, while here it is a structural property of the
format. `|` is still the better spelling — not for correctness, but because it
states "place these bits here" instead of asking the reader to verify that no
carry occurs.
---

## Carried Over — questions inherited from the removed Self-Check

The guide's Self-Check was dropped in favour of this round (contract rule 10).
Four of its six questions were already covered here or in the recall set; these
two were not, and are asked as challenges rather than lost.

### 29. A colleague writes `def mod(x: Int, n: Int): Int = x & (n - 1)` and every test passes. What did the tests fail to violate?

**Two preconditions, not one**, and the second survives the first being met.

**`n` must be a power of two.** For `n = 2ᵏ`, `n - 1` is a mask of `k` ones and
keeping the low `k` bits *is* the remainder (§IV.19). For anything else the
identity is simply false:

```text
mod(9, 6) = 9 & 5 = 0b1001 & 0b0101 = 0b0001 = 1        9 % 6 = 3
```

**`x` must be non-negative** — and this one holds even when `n` is a power of
two, which is what makes it the harder half. With `n = 8`:

```text
x       x & (n-1)   x % n    floorMod(x,n)   agree?
-----   ---------   ------   -------------   ------
 0          0          0           0         yes
 5          5          5           5         yes
 9          1          1           1         yes
-1          7         -1           7         NO
-5          3         -5           3         NO
-9          7         -1           7         NO
-16         0          0           0         yes
```

Over 500,000 random `x` with `n` a power of two, **47.7% disagree** with `%`.
Against `Math.floorMod` the same 500,000 disagree **zero** times.

That second measurement is the real finding: the function is not broken, it
implements a **different operation**. Masking clears the high bits, sign
included, so the result always lands in `[0, n)` — the *floored* modulus. Java's
`%` is the *truncated* remainder, whose sign follows the dividend.

**And this pair has been met before.** `%` is defined as `x - (x / n) * n`, so it
inherits its rounding convention from `/`:

```text
division   >>       rounds toward -infinity        /   truncates toward zero   (§III.15)
modulus    & (n-1)  is floorMod                    %   is rem                  (here)
```

The same floor-versus-truncate split that separated `floorDiv2` from `x / 2` in
E2, reappearing in the modulus, and diverging over exactly the same half of the
domain — which is why a suite of friendly inputs never notices either.

The repair is therefore a choice between two contracts, not a fix to the code:

```text
"precondition: n is a power of two AND x >= 0"   keeps the equivalence with %
"this function is floorMod, not %"               changes the contract, not the body
```

In a hash table — where the technique comes from — the second is usually right: a
bucket index must never be negative, and `& (n-1)` guarantees that while `%` does
not.

E3's own `modPowerOfTwo` had already settled both halves. Its Scaladoc says *"For
non-negative `x` the result must equal `x % n`"* — asserting the equivalence only
where it holds — and the precondition on `n` is encoded in the return type rather
than trusted to the caller:

```scala
def modPowerOfTwo(x: Int, n: Int): Option[Int] =
  if !isPowerOfTwo(n) then None
  else Some(x & (n - 1))
```

The colleague is writing that function with both guards removed.

### 30. Why is a HAMT's branching factor 32 rather than 8 or 128?

Three quantities are in tension, and one of the three candidates is eliminated by
impossibility rather than by balance.

**Depth is logarithmic, not a quotient.** A trie divides the key space by `b` at
*every* level, so depth is `log_b(n)`:

```text
b     bits/level   log_b(10⁶)   levels
---   ----------   ----------   ------
  8       3           6.64         7
 32       5           3.99         4
128       7           2.85         3
```

**The bitmap is the hard constraint.** A node with `b` slots needs `b` bits of
occupancy:

```text
b     bits needed   fits an Int (32)?   fits a Long (64)?
---   -----------   -----------------   -----------------
  8        8              yes                 yes
 32       32              yes                 yes
128      128              NO                  NO
```

128 slots fit in no JVM word at all. The whole economy of §IV.20 rests on

```scala
val physical = Integer.bitCount(bitmap & (bit - 1))   // one POPCNT
```

and with four words that becomes a sum of partial counts plus a branch to decide
which words enter whole and which enters masked. `physicalIndex` stops being an
expression and becomes a loop. **128 is not outscored — it is ruled out.**

**Why 32 beats 8, even though 8 copies less.** The persistent-insert cost appears
to favour the smaller factor:

```text
b     levels   copy/level   refs copied, worst case
---   ------   ----------   -----------------------
  8      7          8                 56
 32      4         32                128
128      3        128                384
```

But the two quantities are not priced alike per unit:

```text
descending a level   chase a pointer into a node probably not in cache
                     -> a cache miss, ~100+ cycles

copying a reference  contiguous read and write, vectorisable, prefetchable
                     -> a fraction of a cycle, amortised
```

Seven levels against four is **three extra cache misses per operation**, which
comfortably outweighs 72 additional contiguous references. A full dense array of
32 references occupies 128 bytes under compressed oops — exactly two cache lines,
fetched together. And a factor of 8 wastes 24 of the 32 bits of the word it still
has to pay for.

**The hash budget closes the argument.** A 32-bit hash is a finite supply, spent
`log2(b)` bits per level:

```text
b=8     3 bits/level  ->  32/3 = 10.7 levels available
b=32    5 bits/level  ->  32/5 =  6.4 available against 4 needed   comfortable
b=128   7 bits/level  ->  32/7 =  4.6 available against 3 needed   tight
```

32 is the only value where all three close at once: the bitmap is exactly one
word, the physical index is one instruction, the hash has margin over the depth
actually required, and a full node's dense array is two cache lines.
