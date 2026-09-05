# Annex A1 — Bitwise Arithmetic & Binary Representation

> **Annex Track · Prerequisite for B1-M2, B3-M9 and B4-M12 · Complexity ⭐️⭐️**
> Milestone tag on completion: `a1-bitwise-arithmetic`

---

## How To Read This Guide

This guide assumes **no prior experience with bits**. It builds from counting in
base 2 up to the indexing arithmetic inside Scala's immutable `Map`.

It is organised as a staircase. Each part depends on the one before it, and each
one unlocks specific exercises:

| Part | Content | Unlocks |
| :--- | :--- | :--- |
| **I** | Numbers as bits: base 2, hexadecimal, negative numbers | E1, E2 |
| **II** | The six operators, one at a time, with worked examples | E1, E2, E3 |
| **III** | Four traps that the operators hide | E2, E3, E7, E9 |
| **IV** | Where this is used in real systems | motivation for all |
| **V** | The idiom catalogue, with derivations | E3, E4, E5 |
| **VI** | Population count, and the HAMT node | E4, E6, E8 |
| **VII** | Scala 3: making bitmasks type-safe | E6 |

**Every numeric result printed in this guide has been executed and verified.**
Where a result looks wrong to you, run it — the surprise is the lesson.

A note on notation: examples use **8-bit** words wherever the width does not
matter, because eight digits fit on a line and thirty-two do not. The JVM's
`Int` is 32 bits, and every rule stated for 8 bits holds for 32 unchanged.

---

# Part I — Numbers As Bits

## 1. Why Base 2

Decimal has ten symbols because we have ten fingers. A digital circuit has two
stable states — a wire carrying voltage, or not — so it has two symbols: `0` and
`1`. That is the entire reason. Everything else follows mechanically.

A single such symbol is a **bit** (binary digit). Eight of them make a **byte**.

## 2. Reading A Binary Number

Positional notation works identically in every base. In decimal, `237` means:

```text
  2 × 100  +  3 × 10  +  7 × 1
= 2 × 10²  +  3 × 10¹ +  7 × 10⁰
```

Each position is worth the base raised to the position's index, counting from
the right, starting at zero. In base 2 the only change is that the base is 2:

```text
position:    7    6    5    4    3    2    1    0
weight:    128   64   32   16    8    4    2    1
```

Memorise that bottom row. It is the whole of binary arithmetic.

**Worked example — read `00001101`:**

```text
   0    0    0    0    1    1    0    1
 128   64   32   16    8    4    2    1
                       ↓    ↓         ↓
                       8 +  4    +    1  =  13
```

So `00001101` is 13. To read any binary number: add the weights of the positions
holding a `1`.

**More examples, all verified:**

```text
  00000000 =   0        (nothing set)
  00000001 =   1
  00000010 =   2
  00000101 =   5        (4 + 1)
  00001101 =  13        (8 + 4 + 1)
  01000000 =  64
  01100100 = 100        (64 + 32 + 4)
  11001010 = 202        (128 + 64 + 8 + 2)
  11111111 = 255        (every weight, 8 bits)
```

That last line is worth pausing on: **the largest value in 8 bits is 255**, not
256. With `n` bits you can represent `2ⁿ` distinct patterns, and counting from
zero the largest is `2ⁿ - 1`.

## 3. Writing A Decimal Number In Binary

Go the other way by greedily subtracting the largest weight that fits.

**Worked example — write 100 in binary:**

```text
100  -  64  =  36     set bit 6      weight 128 does not fit
 36  -  32  =   4     set bit 5
  4  -   4  =   0     set bit 2      weights 16 and 8 do not fit
                                     weights 2 and 1 are not needed

bits set: 6, 5, 2   ->   01100100
```

Check it: 64 + 32 + 4 = 100. ✓

## 4. Hexadecimal — The Shorthand

Thirty-two binary digits are unreadable. Hexadecimal (base 16) exists to fix
that, and it works because **16 is 2⁴**: one hex digit is exactly four bits, with
no alignment arithmetic.

```text
   hex  binary  dec        hex  binary  dec
   ---  ------  ---        ---  ------  ---
    0    0000    0          8    1000    8
    1    0001    1          9    1001    9
    2    0010    2          a    1010   10
    3    0011    3          b    1011   11
    4    0100    4          c    1100   12
    5    0101    5          d    1101   13
    6    0110    6          e    1110   14
    7    0111    7          f    1111   15
```

Converting is pure substitution, four bits at a time:

```text
  binary  1100 1010
  hex        c    a      ->  0xca  =  202
```

In Scala, `0x` marks a hex literal: `0xca == 202`. Two hex digits are one byte,
eight hex digits are one `Int`. This is why masks in real code look like
`0xff` (one byte of ones), `0x1f` (five bits of ones), or `0xffffffff` (all
thirty-two).

You will meet `0xff` constantly. Learn to see it as `00000000 00000000 00000000
11111111` rather than as "two hundred fifty-five".

## 5. Width — What An `Int` Actually Is

A JVM `Int` is exactly **32 bits**. Not "a number" — a fixed-width vector of 32
bits, and every operation on it produces another 32-bit vector.

```text
Byte     8 bits         Short   16 bits
Int     32 bits         Long    64 bits
```

The fixed width has a consequence that surprises everyone once: arithmetic
**wraps around** instead of growing. Adding 1 to the largest `Int` gives the
smallest one, silently, with no error:

```scala
Int.MaxValue      //  2147483647
Int.MaxValue + 1  // -2147483648   ← wraps
```

This is not a bug and not undefined behaviour — it is the defined behaviour of
finite-width arithmetic, and Part I.6 explains exactly why the wrap lands where
it does.

## 6. Negative Numbers — Two's Complement

We have 32 positions. How do we say "minus"?

### 6.1 The Obvious Idea, And Why It Fails

The natural first attempt is **sign-magnitude**: reserve the leftmost bit as a
sign flag (`0` = positive, `1` = negative) and read the rest as the magnitude.

```text
  00000101  ->  +5
  10000101  ->  -5      (in sign-magnitude — NOT what the JVM does)
```

It reads nicely and it is wrong for hardware, for two reasons:

1. **Two zeros.** `00000000` and `10000000` would both mean zero. Every
   comparison would need to special-case them.
2. **Addition needs a second circuit.** `5 + (-5)` under this scheme is
   `00000101 + 10000101 = 10001010`, which is −10, not 0. The adder would have
   to inspect the sign bits and branch.

### 6.2 The Idea That Works

**Two's complement** keeps the leftmost bit as the sign indicator, but instead of
being a flag it carries a **negative weight**:

```text
position:    7     6    5    4    3    2    1    0
weight:   -128    64   32   16    8    4    2    1
             ↑
       negative!
```

Everything else is unchanged. Reading a number is still "add the weights of the
set positions" — the leftmost weight just happens to be negative.

**Worked example — read `11111011` as a signed 8-bit value:**

```text
   1    1    1    1    1    0    1    1
-128   64   32   16    8    4    2    1
   ↓    ↓    ↓    ↓    ↓         ↓    ↓
-128 + 64 + 32 + 16 +  8    +    2 +  1  =  -5
```

So `11111011` is −5. Verified:

```text
    5 = 00000101         127 = 01111111      ← largest positive
   -5 = 11111011        -128 = 10000000      ← most negative
    1 = 00000001           0 = 00000000
   -1 = 11111111
```

Look hard at `-1 = 11111111`. **Every bit set is negative one.** That single fact
explains more surprising output than anything else in this guide.

### 6.3 Why It Works: One Adder For Both Signs

Add `5 + (-5)` with the ordinary schoolbook algorithm, carrying as usual:

```text
    00000101      (  5)
  + 11111011      ( -5)
  ----------
   100000000
   ↑
   the ninth bit falls off the end — we only have eight
   what remains:  00000000  =  0     ✓
```

The carry out of the top simply vanishes, because there is no ninth position to
hold it. No sign inspection, no branch, no correction. **The same circuit adds
signed and unsigned numbers.** That is why every machine built since the 1970s
uses two's complement, and why `Int.MaxValue + 1` wraps to `Int.MinValue`
instead of raising an error: the overflow bit falls off the end exactly like
this one.

### 6.4 Negating A Number: Flip And Add One

To negate: invert every bit, then add 1.

**Worked example — negate 5:**

```text
   5           =  00000101
   flip         →  11111010
   add 1        →  11111011   =  -5    ✓
```

Try the reverse direction and it comes back:

```text
  -5           =  11111011
   flip         →  00000100
   add 1        →  00000101   =   5    ✓
```

Written as an identity, using `~` for "flip every bit" (Part II.10):

```text
       -x  ==  ~x + 1
```

**This is the master identity of the whole annex.** Nearly every clever bit trick
you will ever read descends from it, and Part V derives them from it one by one.

Here is the proof, and it needs no case analysis on the sign. For any `x`, the
sum `x + ~x` sets every position exactly once — wherever `x` has a `0`, `~x` has
a `1`, and vice versa. All ones is the representation of −1. So:

```text
   x + ~x  =  -1        hence   ~x  =  -1 - x        hence   -x  =  ~x + 1
```

### 6.5 The Asymmetry: One Number Has No Negative

Count the patterns. With 32 bits there are `2³²` of them. One is zero, leaving an
odd number to split between positives and negatives — so the split cannot be
even. Two's complement gives the extra one to the negatives:

```text
Int.MaxValue =  2147483647   =  01111111 11111111 11111111 11111111
Int.MinValue = -2147483648   =  10000000 00000000 00000000 00000000
```

There are 2³¹ negative values and only 2³¹ − 1 positive ones. Therefore
**`Int.MinValue` has no representable negation**:

```scala
-Int.MinValue          // -2147483648   ← itself!
Math.abs(Int.MinValue) // -2147483648   ← abs returns a negative number
```

Run the flip-and-add-one procedure on it and watch it fail to escape:

```text
  10000000...  flip →  01111111...  add 1 →  10000000...   back where we started
```

This is not a JVM defect; it is arithmetic. It matters for engineering because
`abs: Int => Int` is therefore **a partial function wearing a total type**. This
annex is the first place in the curriculum where "make illegal states
unrepresentable" collides with the machine, and Exercise 2 makes you confront it.

---

# Part II — The Six Operators

Bitwise operators work on **each position independently**. There is no carry, no
borrow, no interaction between neighbours. Position 3 of the result depends only
on position 3 of the inputs.

Two readings run in parallel throughout, and both are useful:

- **Numeric** — the word is a number; shifting multiplies and divides.
- **Set-theoretic** — the word is a *set*: position `i` set means "`i` is a
  member". Then `&` is intersection, `|` is union, `^` is symmetric difference,
  `~` is complement.

The set reading is the one that unlocks Part IV, and it is the one almost never
taught. Hold onto it.

Throughout Part II the two example operands are:

```text
  a  =  12  =  00001100
  b  =  10  =  00001010
```

## 7. AND — `&`

Result bit is `1` only where **both** inputs are `1`.

| `a` | `b` | `a & b` |
| :-: | :-: | :-----: |
| 0 | 0 | 0 |
| 0 | 1 | 0 |
| 1 | 0 | 0 |
| 1 | 1 | **1** |

```text
    00001100     (12)
  & 00001010     (10)
  ----------
    00001000     ( 8)        12 & 10 == 8
```

**Set reading:** intersection. `{2,3} ∩ {1,3} = {3}`, and bit 3 is worth 8. ✓

**What it is for.** `&` is how you *ask questions* and how you *keep only part*
of a word:

```scala
x & 1            // is x odd?  (keeps only bit 0)
x & 0xff         // keep only the lowest byte, discard the rest
flags & WRITE    // does this permission set include WRITE?
```

The second line is called **masking**, and it is the single most common use of
`&` in production code. A mask is a word whose `1`s mark the positions you want
to keep; `&`-ing against it zeroes everything else.

## 8. OR — `|`

Result bit is `1` where **either** input is `1`.

| `a` | `b` | `a \| b` |
| :-: | :-: | :------: |
| 0 | 0 | 0 |
| 0 | 1 | **1** |
| 1 | 0 | **1** |
| 1 | 1 | **1** |

```text
    00001100     (12)
  | 00001010     (10)
  ----------
    00001110     (14)        12 | 10 == 14
```

**Set reading:** union. `{2,3} ∪ {1,3} = {1,2,3}` = 2 + 4 + 8 = 14. ✓

**What it is for.** Combining and *setting* bits:

```scala
READ | WRITE               // build a permission set from parts
x | (1 << 5)               // turn bit 5 on, leave everything else alone
(hi << 32) | lo            // glue two fields into one word
```

## 9. XOR — `^`

Result bit is `1` where the inputs **differ**.

| `a` | `b` | `a ^ b` |
| :-: | :-: | :-----: |
| 0 | 0 | 0 |
| 0 | 1 | **1** |
| 1 | 0 | **1** |
| 1 | 1 | 0 |

```text
    00001100     (12)
  ^ 00001010     (10)
  ----------
    00000110     ( 6)        12 ^ 10 == 6
```

**Set reading:** symmetric difference — members of exactly one of the two sets.

XOR has a property the others lack: **it is its own inverse.**

```scala
(x ^ k) ^ k == x     // for every x and every k
x ^ x == 0           // anything xor'd with itself vanishes
x ^ 0 == x           // zero is the identity
```

**What it is for.** Toggling, difference, and cheap mixing:

```scala
x ^ (1 << 5)         // flip bit 5
a ^ b                // where do these two words disagree?
h ^ (h >>> 16)       // hash mixing (this exact line is in java.util.HashMap)
```

The "swap two variables without a temporary" trick is XOR's party piece. It is
also strictly worse than a temporary on any real machine — mention it only to
show you know why it works.

## 10. NOT — `~`

Unary. Flips **every** bit.

```text
  ~ 00001100     (12)
  ----------
    11110011                  as a signed 8-bit value: -13
```

In 32 bits, `~12 == -13`. And that is the master identity again: `~x == -x - 1`.

**Set reading:** complement — every member you did *not* have.

```scala
~0    // -1     no bits set → all bits set
~(-1) // 0
~5    // -6
x & ~mask    // clear every bit that the mask marks   ← the standard idiom
```

There is no dedicated NOT instruction in the JVM. `javap` shows `~x` compiling to
`iconst_m1; ixor` — XOR against all-ones. Which is exactly the set-theoretic
statement that complement is symmetric difference with the universe.

## 11. Left Shift — `<<`

Move every bit `n` positions to the left; inject zeros on the right; bits that
fall off the left are lost.

```text
   5        =  00000101   =   5
   5 << 1   =  00001010   =  10
   5 << 2   =  00010100   =  20
   5 << 3   =  00101000   =  40
```

**Each shift of one position doubles the value.** `x << n == x * 2ⁿ`, up to
overflow — which is exactly the same fact as "appending a zero in decimal
multiplies by ten".

**What it is for.** Building masks and positioning fields:

```scala
1 << 5              // a word with only bit 5 set — the mask for "member 5"
value << 16         // move a field into the third byte
hi.toLong << 32     // move a 32-bit field into the top half of a Long
```

## 12. Right Shift — `>>` and `>>>`

Right shift moves bits toward the least significant end. But something must fill
the vacated positions on the *left*, and there are two defensible answers — so
the JVM provides both operators.

**`>>` — arithmetic shift.** Fills with copies of the **sign bit**, preserving
sign.

**`>>>` — logical shift.** Fills with **zeros**, always.

For non-negative numbers they are identical:

```text
  20        =  00010100   =  20
  20 >> 1   =  00001010   =  10
  20 >> 2   =  00000101   =   5
  20 >> 3   =  00000010   =   2       ← halving, rounding down
```

For negative numbers they diverge completely. Here is −20 in full 32-bit form:

```text
  -20        =  11111111 11111111 11111111 11101100  =  -20
  -20 >> 2   =  11111111 11111111 11111111 11111011  =  -5
  -20 >>> 2  =  00111111 11111111 11111111 11111011  =  1073741819
```

`>>` gave −5, which is −20 divided by 4: the sign survived. `>>>` gave a huge
positive number: it treated the bits as an unsigned quantity and shifted zeros
in from the left.

**Which do you want?**

- Doing **arithmetic** on a signed number → `>>`.
- Treating the word as **raw bits** — a hash, a bitmap, a packed field, anything
  where the top bit is data rather than a sign → `>>>`.

Getting this wrong is the most common bug in bit-level code, and it is silent for
every non-negative input, which is why it survives testing. Exercises 7, 8 and 9
each contain a test that exists solely to catch it.

## 13. Reference Table

| Operator | Name | Bytecode | Numeric reading | Set reading |
| :--- | :--- | :--- | :--- | :--- |
| `a & b` | AND | `iand` | keep shared bits | intersection |
| `a \| b` | OR | `ior` | merge bits | union |
| `a ^ b` | XOR | `ixor` | bits that differ | symmetric difference |
| `~a` | NOT | `iconst_m1; ixor` | `-a - 1` | complement |
| `a << n` | shift left | `ishl` | `a × 2ⁿ` | shift membership up |
| `a >> n` | arithmetic shift right | `ishr` | `floor(a ÷ 2ⁿ)` | — |
| `a >>> n` | logical shift right | `iushr` | unsigned `a ÷ 2ⁿ` | shift membership down |

Note the third row: `~a` has **no dedicated opcode**. It is defined as XOR
against −1.

---

# Part III — Four Traps

Each of these is silent: the code compiles, runs, and produces a wrong answer
only for inputs your tests may never generate.

## 14. Trap One — The Shift Distance Is Masked

You would expect shifting a 32-bit word by 32 positions to leave nothing. It does
not:

```scala
1 << 0     // 1
1 << 1     // 2
1 << 31    // -2147483648    (the sign bit)
1 << 32    // 1              ← not 0!
1 << 33    // 2
1 << 64    // 1
1 << -1    // -2147483648
```

The rule (JVM Specification §6.5, `ishl`): only the **low five bits** of the
distance are used for `Int`, and the **low six bits** for `Long`. Five bits
express 0–31, which is every distance that could mean anything, so the hardware
skips the range check:

```text
   1 << 32   →  distance is  32 & 31 = 0   →  1 << 0   =  1
   1 << 33   →  distance is  33 & 31 = 1   →  1 << 1   =  2
   1 << -1   →  distance is  -1 & 31 = 31  →  1 << 31  =  Int.MinValue
```

This is the x86 `SHL` instruction's own behaviour, exposed verbatim so the JIT
can emit one instruction with no guard.

**Engineering consequence.** Any function of yours that takes a shift distance as
a parameter must validate its own domain. Neither the compiler nor the runtime
will do it for you, and the failure is a wrong answer rather than an exception.

## 15. Trap Two — `>> 1` Is Not `/ 2`

Both halve, but they round in **opposite directions** for negatives:

- `>>` rounds toward **negative infinity** (floor).
- `/` rounds toward **zero** (truncation).

```text
   x     x >> 1     x / 2
  ---    ------     -----
   8        4         4       agree
   7        3         3       agree
  -1       -1         0    ←  disagree
  -7       -4        -3    ←  disagree
  -8       -4        -4       agree
```

They agree on every non-negative input and on every even negative one. They
disagree on **every negative odd number** — which is why this survives a test
suite built from friendly inputs.

This is also why the compiler never silently rewrites `x / 2` as `x >> 1`. What
it actually emits for a division by two is a bias-corrected shift:

```scala
(x + (x >>> 31)) >> 1        // == x / 2, for every Int
```

Read it slowly, because it is the cleanest example of *branchless* code you will
meet: `x >>> 31` isolates the sign bit as a plain `0` or `1`. Adding that before
flooring converts floor into truncation. Same result as an `if`, with no branch
for the CPU to mispredict.

## 16. Trap Three — Narrow Types Widen With Sign Extension

There is no `iand` for `Byte`. Any operand narrower than `Int` is widened first —
and the widening **copies the sign bit** across the new positions.

```text
   Byte value    as Int     value & 0xff
   ----------    ------     ------------
       -1          -1           255
     -128        -128           128
      127         127           127
        5           5             5
```

A `Byte` holding the bit pattern `11111111` is −1. Widen it and you get 32 ones,
still −1. But when you are *parsing a byte stream*, that pattern means the
unsigned quantity 255, and the sign extension has just poisoned every arithmetic
operation downstream.

Hence the universal idiom of binary decoding:

```scala
val b: Byte = -1
b.toInt        // -1     ← almost never what you want when parsing
b & 0xff       // 255    ← the unsigned reading
```

In Module B4-M12 you will decode byte streams. **Every byte you read must pass
through `& 0xff`** before it participates in arithmetic. Exercise 9 has a test
named after this trap.

## 17. Trap Four — Operator Precedence

Scala derives an operator's precedence from its **first character**, in
increasing order:

```text
  (letters)  <  |  <  ^  <  &  <  = !  <  < >  <  : <  + -  <  * / %  <  (other)
```

So `&` binds *less* tightly than `==`. In C, `a & 1 == 0` silently parses as
`a & (1 == 0)` and is a famous production bug. In Scala 3 the same expression is
a **compile error**, because `Int & Boolean` has no such method — the type system
converts a historical runtime bug into a static failure.

Parenthesise anyway: `(a & 1) == 0`. The next reader should not have to know the
table.

---

# Part IV — Where This Is Actually Used

Everything so far is mechanism. This part is the answer to "why would I ever
write this".

## 18. Flags And Permission Sets

The oldest use. Instead of eight booleans, use eight bits of one word:

```scala
val READ  = 1        // 00000001
val WRITE = 2        // 00000010
val EXEC  = 4        // 00000100
```

```text
   val p = READ | WRITE          =  00000011  =  3

   p & EXEC  != 0   →  false     does p include EXEC?   no
   p & READ  != 0   →  true      does p include READ?   yes
   p | EXEC         →  7         add EXEC
   p & ~WRITE       →  1         remove WRITE
```

Unix file permissions, JVM access flags in a `.class` file, and `Set`-like
configuration in every systems API are all this. **Exercise 6 builds the
type-safe version of exactly this.**

## 19. Modulo By Masking — Why Hash Tables Have Power-Of-Two Sizes

For `n = 2ᵏ`, the value `n - 1` is a mask of `k` ones. `&`-ing keeps the low `k`
bits, which *is* the remainder modulo `2ᵏ`:

```text
   16 - 1 = 15 = 00001111

     37 & 15 =  5        37 % 16 =  5     ✓
     64 & 15 =  0        64 % 16 =  0     ✓
    100 & 15 =  4       100 % 16 =  4     ✓
   1000 & 15 =  8      1000 % 16 =  8     ✓
```

One instruction instead of a multi-cycle division. This is the *sole* reason
every high-performance hash table — `java.util.HashMap` included — has a
power-of-two capacity.

It also has a cost worth knowing: masking **discards the high bits entirely**. If
your hash function puts its entropy above bit `k`, every key collides. HashMap
defends itself by mixing before masking, with one line you can now read:

```scala
h ^ (h >>> 16)     // fold the high half down onto the low half
```

**Exercise 3** builds `modPowerOfTwo` and makes you encode the precondition.

## 20. The Payoff — How An Immutable `Map` Stays Fast

Scala's immutable `Vector`, `Map` and `Set` are **Hash Array Mapped Tries**
(HAMT): trees with a branching factor of 32. Two bit-level ideas make them work.

**First: index by slicing the hash.** A 32-way branch consumes exactly 5 bits of
hash per level, because `2⁵ = 32`:

```scala
val slot = (hash >>> (5 * level)) & 0x1f     // 0x1f is 11111 — five bits
```

Level 0 reads bits 0–4, level 1 reads bits 5–9, and so on. Note `>>>`, not `>>`:
a hash is raw bits, and a negative hash must not sign-extend (Part II.12).

**Second: compress the child array with a bitmap.** A naive node would hold a
32-slot array, mostly empty — ruinous. The real node holds a **32-bit bitmap** of
which slots are occupied, plus a **dense array** holding only those children.

To find where logical slot `s` lives in the dense array, count the occupied slots
*below* it:

```scala
val bit      = 1 << slot
val present  = (bitmap & bit) != 0
val physical = Integer.bitCount(bitmap & (bit - 1))
```

`bit - 1` is a mask of every position below `slot`; `bitCount` counts them. That
count is exactly how many children precede this one.

**Worked example.** A node with slots 0, 3, 7 and 8 occupied:

```text
   bitmap = 00000000 00000000 00000001 10001001      (4 children stored densely)

   slot  0:  present = true    physicalIndex = 0
   slot  3:  present = true    physicalIndex = 1
   slot  7:  present = true    physicalIndex = 2
   slot  5:  present = false   physicalIndex = 2   ← where it *would* be inserted
   slot 16:  present = false   physicalIndex = 4
```

Notice that the computation is meaningful whether or not the slot is occupied:
for a free slot it gives the insertion point. That dual reading is what lets
lookup and insertion share one computation.

> **This is the concrete answer to "why doesn't immutability cost 10×".** It
> costs one bitmap word and one `POPCNT` instruction per level.

**Exercise 8 builds this**, and Module B1-M2 builds the trie on top of it.

## 21. Packing Fields Into One Word

`AtomicLong.compareAndSet` updates **one** word atomically. Need to update two
32-bit counters atomically without allocating a wrapper object? Put both in one
`Long`:

```scala
val packed = (hi.toLong << 32) | (lo.toLong & 0xffffffffL)
```

The `& 0xffffffffL` is mandatory, not defensive. Watch what happens without it,
with `hi = 7` and `lo = -1`:

```text
   without the mask:   hi reads back as  -1     ← destroyed
   with    the mask:   hi reads back as   7     ✓
```

Widening `lo = -1` to `Long` sign-extends it to sixty-four ones, and the `|`
then floods the high half. Part III.16, in production.

No allocation means no pressure on the escape analysis you studied in Module 1,
and one CAS updates both fields with no torn read. **Exercise 7 builds it**;
Module B3-M9 uses it.

## 22. Wire Formats

Binary protocols are bit-level by definition — variable-length integers, tag
packing in Protocol Buffers, HTTP/2 frame headers, UTF-8 continuation bytes.
**Exercise 9 builds zig-zag + LEB128**, which is literally how Protobuf encodes
`sint32`, and Module B4-M12 builds codecs on it.

## 23. Arithmetic Itself

Addition is not a primitive sitting *above* bit operations — it *is* one,
iterated to a fixed point. For a single position:

- the sum ignoring carry is `a ^ b`;
- the carry produced is `a & b`, which applies one position to the **left**.

So, for whole words:

```text
   add(a, b)  =  if b == 0 then a else add(a ^ b, (a & b) << 1)
```

That recurrence is the **ripple-carry adder**. It terminates: each step moves the
surviving carries strictly leftward, so after at most 32 iterations the carry
word is zero. Multiplication follows as shift-and-add, exactly the schoolbook
algorithm in base 2.

**Exercise 5 builds all of it** from `^`, `&` and `<<` alone. Doing so dissolves
the false hierarchy between "arithmetic" and "bit twiddling": the ALU is not a
magic box beneath your functional code, it is a fixed point of a Boolean
recurrence.

---

# Part V — The Idiom Catalogue

These appear without explanation in every serious piece of systems code. Derive
them from the master identity rather than memorising them.

| Expression | Effect |
| :--- | :--- |
| `x & (x - 1)` | clears the **lowest set bit** |
| `x & -x` | **isolates** the lowest set bit |
| `x \| (x - 1)` | sets every bit **below** the lowest set bit |
| `x & (n - 1)` | `x mod n` — **only when `n` is a power of two** |
| `(x ^ y) < 0` | true iff `x` and `y` have **opposite signs** |
| `x >> 31` | `0` if `x >= 0`, `-1` (all ones) otherwise — a **mask** |
| `(x ^ m) - m` where `m = x >> 31` | branchless `abs` |

Verified, in 8-bit form:

```text
   x = 12  00001100      x & (x-1) =  8  00001000      x & -x =  4  00000100
   x = 40  00101000      x & (x-1) = 32  00100000      x & -x =  8  00001000
   x = 96  01100000      x & (x-1) = 64  01000000      x & -x = 32  00100000
   x =  1  00000001      x & (x-1) =  0  00000000      x & -x =  1  00000001
   x =  0  00000000      x & (x-1) =  0  00000000      x & -x =  0  00000000
```

## 24. Worked Derivation — Why `x & -x` Isolates The Lowest Set Bit

Let the lowest set bit of `x` be at position `k`. Then `x` ends in a `1` followed
by `k` zeros. Take `x = 40`:

```text
    x   =  00101000            lowest set bit at position 3
   ~x   =  11010111            flip
   -x   =  11011000            add 1: the carry runs through the trailing ones
                                and stops at position 3, setting it

  x & -x =  00001000  =  8     agrees with x only at position 3    ✓
```

In general: below position `k`, both `x` and `-x` are zero. At position `k`, both
are `1` — `x` by assumption, and `-x` because the carry stopped there. Above
position `k`, `-x` is the exact bitwise complement of `x`, so the AND is zero
everywhere. Hence only bit `k` survives.

**Now the question that separates understanding from pattern-matching:** does the
identity still hold for `x == Int.MinValue`, whose negation is itself? Work it
out before starting Exercise 3.

## 25. Why `x & (x - 1) == 0` Detects Powers Of Two

Subtracting 1 flips the lowest set bit to `0` and turns everything below it into
`1`s. If `x` had *only* that one bit, the result shares no bit with `x`, so the
AND is zero.

```text
   x = 16  00010000      x - 1 = 15  00001111      AND = 0   → power of two
   x = 12  00001100      x - 1 = 11  00001011      AND = 8   → not
```

**The trap** — and Exercise 3 tests it: `Int.MinValue` is `10000000...`, a single
set bit, so this test **accepts it**. But −2³¹ is not 2ᵏ for any `k ≥ 0`. The
correct predicate needs `x > 0` as well.

---

# Part VI — Counting Bits, And Why It Matters

**Population count** (`popcount`) — how many bits are set — is the operation that
makes HAMTs work (Part IV.20). Three algorithms, in increasing sophistication.

**Naive**, O(32): test all 32 positions. Simple, always the same cost.

**Kernighan**, O(number of set bits): repeatedly apply `x & (x - 1)`, counting
iterations. Each step removes exactly one set bit, so the loop runs as many times
as there are bits to count.

```text
   x = 00101000        count 0
     → 00100000        count 1     (cleared bit 3)
     → 00000000        count 2     (cleared bit 5)   → answer: 2
```

Note what is unusual here: the cost depends on the **value** of the input, not on
its size. That property is worth recognising — it is rare, and it is why this
version wins on sparse words and loses on dense ones.

**SWAR** (SIMD Within A Register), O(log 32), no branches at all — a
divide-and-conquer that sums adjacent 1-bit fields into 2-bit fields, then 2 into
4, 4 into 8, and finally collapses four byte lanes with one multiply:

```text
   x = x - ((x >>> 1) & 0x55555555)                    // pairs
   x = (x & 0x33333333) + ((x >>> 2) & 0x33333333)     // nibbles
   x = (x + (x >>> 4)) & 0x0f0f0f0f                    // bytes
   (x * 0x01010101) >>> 24                             // horizontal sum
```

The constants are masks selecting alternate fields of width 1, 2, 4 and 8 —
`0x55` is `01010101`, `0x33` is `00110011`, `0x0f` is `00001111`. Write them out
in binary and the algorithm becomes visible.

The last line is the elegant part: multiplying by `0x01010101` makes the top byte
of the product the sum of all four byte lanes — because multiplication *is*
shift-and-add (Part IV.23). One multiply performs four additions.

## 26. And Then Reality: Intrinsics

`java.lang.Integer.bitCount` is a HotSpot **intrinsic**. C2 deletes the call
entirely and emits the single x86 `POPCNT` instruction. Your best hand-written
SWAR cannot compete.

> **The lesson is not "don't write it".** Write all three, measure them, then use
> `Integer.bitCount` forever. Knowing what the machine gives you for free is what
> separates an engineer from a library consumer.

The intrinsics worth knowing:

```scala
java.lang.Integer.bitCount(x)                // POPCNT
java.lang.Integer.numberOfTrailingZeros(x)   // TZCNT — index of lowest set bit
java.lang.Integer.numberOfLeadingZeros(x)    // LZCNT — 31 - floor(log2 x)
java.lang.Integer.highestOneBit(x)           // largest power of two <= x
java.lang.Integer.reverse(x)                 // bit-reversal permutation
java.lang.Long.bitCount(x)                   // 64-bit POPCNT
```

`numberOfLeadingZeros` deserves attention: `31 - nlz(x)` is `floor(log2 x)` in
one instruction, which is how you size a hash table or compute a trie depth
without touching floating point.

**Exercise 4 builds all three and measures them against the intrinsic.**

---

# Part VII — Scala 3: Making Bitmasks Type-Safe

A raw `Int` flag set is *primitive obsession*: nothing stops you passing a
permission mask where a colour was expected. Scala 3's `opaque type` gives a
distinct compile-time type with **zero runtime representation** — after erasure
it is still a primitive `Int`, so no box, no header, no allocation.

```scala
opaque type Flags = Int

object Flags:
  val Empty: Flags = 0
  def apply(raw: Int): Flags = raw

  extension (f: Flags)
    infix def union(g: Flags): Flags = (f: Int) | (g: Int)
    def contains(g: Flags): Boolean  = ((f: Int) & (g: Int)) == (g: Int)
```

Two subtleties that Exercise 6 will make you feel:

1. **Inside the companion the alias is transparent** — there, `Flags` *is* `Int`.
   Outside it is opaque and only your extension methods exist. The ascriptions
   `(f: Int)` are therefore not decoration: without them, an extension method
   named `|` would resolve to *itself* and recurse forever. The failure is a
   `StackOverflowError` at runtime, **not** a type error — which is exactly why
   you want to meet it here rather than in Block 2.
2. **`inline def`** guarantees the abstraction is erased at the call site rather
   than merely being cheap, which matters inside a HAMT hot loop.

This is the pattern Module B2-M4 formalises. Meeting it here, on concrete
substrate, is the point of the annex.

---

# Instrument Cheat Sheet

```bash
# Read the bytecode of a compiled exercise: confirm ~x is iconst_m1 + ixor
javap -c -p -cp annex-foundations/target/scala-3.9.0/classes cs.se.annex.a1.Bits

# Show the JIT's inlining decisions
sbt annex/test -J-XX:+UnlockDiagnosticVMOptions -J-XX:+PrintInlining

# Disable the popcount intrinsic and re-measure: the controlled experiment
sbt "set annex/Test/javaOptions += \"-XX:-UsePopCountInstruction\"" annex/test
```

In the REPL, `java.lang.Integer.toBinaryString` is your microscope — but it does
**not** zero-pad, which is how off-by-one errors survive inspection. Exercise 1
asks you to write the padded version first, for exactly that reason.

---

# Self-Check — You Have Not Finished This Annex Until You Can Answer

Write the answers **in prose, without running code**, into `docs/checklist.md`.

1. Read `11010110` as an unsigned 8-bit number, then as a signed one. Show the
   arithmetic both times.

2. Prove that `x & -x` isolates the lowest set bit, using only `-x == ~x + 1`.
   Then state what it returns for `x == 0` and for `x == Int.MinValue`, and
   whether those results are consistent with your proof.

3. `-7 >> 1` is `-4` but `-7 / 2` is `-3`. Name the rounding mode each
   implements, and reconstruct from first principles why the compiler emits
   `(x + (x >>> 31)) >> 1` for `x / 2`.

4. A colleague writes `def mod(x: Int, n: Int): Int = x & (n - 1)` and every test
   passes. Name the precondition their tests failed to violate, and say what the
   function returns for `n = 6, x = 9`.

5. Why is the HAMT branching factor 32 rather than 8 or 128? Reason with numbers
   about trie depth for one million elements, the width of the bitmap word, and
   the cost of the array copy on insertion.

6. In Part IV.21, explain precisely what breaks if `& 0xffffffffL` is omitted.
   Give a concrete `(hi, lo)` pair that decodes wrongly, and say why.

---

# Where To Go Next

| If you want | Read |
| :--- | :--- |
| The definitive treatment of two's complement | Bryant & O'Hallaron, *CS:APP*, Ch. 2 |
| Every idiom in Part V, derived rigorously | Warren, *Hacker's Delight*, Ch. 2 and 5 |
| The normative shift semantics of Part III.14 | JVM Specification §6.5 |
| The origin of the HAMT in Part IV.20 | Bagwell (2001), *Ideal Hash Trees* |

Full annotated list, with a suggested reading order: `references.md`.
