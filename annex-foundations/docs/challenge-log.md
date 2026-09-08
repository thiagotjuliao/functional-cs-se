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
| E5–E9 | — | pending |

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
