# A1 — Error Patterns

The recall set asks whether the mechanism is understood. The challenge log asks
whether it can be derived. Neither asks the question this file asks:

> **Where does composition go wrong, once the mechanism is already known?**

Every entry below was a real defect written during this annex. None of them is
ignorance of a mechanism. `1 << 7 - 1` was written by someone who knows exactly
what `<<` does; the error is in how the pieces were put together.

That is why this file is organised by **pattern** rather than by exercise.
Fourteen individual mistakes are a diary and nobody rereads a diary. Seven
recurring shapes are a review checklist.

Each entry carries four things: what the pattern is, the occurrences that
instantiated it, the rule that prevents it, and — the field that makes this
usable — **why the compiler and the test suite do not catch it**. Every pattern
but the sixth compiles cleanly under `-Wall -Werror` in all its occurrences.

| # | Pattern | Occurrences | Caught by the build? |
| :-- | :--- | :-: | :--- |
| 1 | Precedence assumed rather than declared | 2 | no |
| 2 | The mask does not have the shape you think | 3 | no |
| 3 | A mask tested against the wrong value | 1 | no |
| 4 | The index counter measures the wrong thing | 2 | no |
| 5 | Off-by-one in a limit | 2 | no — and a test passed for the wrong reason |
| 6 | Wrong collection operator | 2 | one of two |
| 7 | A default parameter hiding an omission | 2 | no |

---

## 1. Precedence assumed rather than declared

Scala derives an operator's precedence from its **first character**. `<<` begins
with `<`, so it sits in the `< >` band — **below** `+ -` and **above** `&`.
Almost nobody holds that table in their head, and the two occurrences below show
both outcomes of relying on it.

```text
written                      parsed as              intended              verdict
--------------------------   --------------------   -------------------   -------
1 << 7 - 1                   1 << (7 - 1)  = 64     (1 << 7) - 1 = 127    WRONG
bitmap & 1 << slot           bitmap & (1 << slot)   the same              right
```

The first produced a mask of a single bit where seven were wanted, and every
LEB128 group came out as `0` or `64`. The second happened to coincide with the
intent.

**The rule.** Parenthesise every mixed bitwise/arithmetic expression, including
the ones that are already correct. §III.17 of the guide states it as *"the next
reader should not have to know the table"*, and the pair above adds a second
reason: **the cost of being right by accident is that you never learn you were
guessing.**

**Why the build does not catch it.** Both parse to valid `Int` expressions.
Scala 3 rejects the famous C form `a & 1 == 0` only because `Int & Boolean` has
no such method — a type accident, not a precedence check. Where both operands
are integers, as here, nothing objects.

---

## 2. The mask does not have the shape you think

A mask is a picture. When the picture is wrong the result is still an `Int`, and
still plausible.

```text
written             actual pattern                is                    wanted
-----------------   ---------------------------   -------------------   ------------------
& -1                11111111111111111111111111111111   identity          & 0xff, one byte
~(1L << 32)         every bit except bit 32       clears ONE bit        clear 32 bits
1 << 7 - 1          01000000                      one bit               seven bits
```

`& -1` is the most instructive: `-1` is every bit set, so `x & -1 == x`. It is
the mask that lets everything through, written where the mask that lets one byte
through was meant. It cost 88% of the legal RGBA channel combinations.

**The rule.** Before using a mask, write it out in binary. And know the two
shapes by name:

```text
one bit at position i       1 << i
k contiguous low bits       (1 << k) - 1
```

The second is §IV.19's modulo-by-masking identity, and it is the shape that was
missing twice.

**Why the build does not catch it.** Every mask is a well-typed `Int`. A wrong
mask narrows or widens what survives, and the result is a number that looks like
a number.

---

## 3. A mask tested against the wrong value

```scala
val cont = (byte & 0x80) == 1     // always false
```

`byte & 0x80` yields `0` or **`128`**, never `1`. The decoder therefore treated
every byte as the last one and stopped after one group, silently truncating
every multi-byte varint.

```text
byte   (b & 0x80)   == 1     != 0
0x00   0            false    false
0x7f   0            false    false
0x80   128          false    true
0xff   128          false    true
```

**The rule.** A mask is tested against **zero**: `(x & mask) != 0` means "some of
those bits are set". Comparing against `1` is correct only when the mask *is* `1`
— as in E6's `((s >> i) & 1) == 1`, which is right for exactly that reason.

**Why the build does not catch it.** `Int == Int` is well typed and the result is
a perfectly good `Boolean`. The expression is not wrong; it answers a different
question, one whose answer is always `false`.

---

## 4. The index counter measures the wrong thing

Two counters that coincide in the common case and diverge in the general one.

```text
written                 measures                    should measure
---------------------   -------------------------   ---------------------------
pos * 7                 position in the buffer      bytes read in THIS varint
slice(index + 1, n)     the range for a removal     the range for an insertion
```

`pos * 7` is correct whenever `offset == 0`, which is every single-varint test.
It breaks the moment a varint starts anywhere else, and it breaks *plausibly*:
at `offset = 5` the shift becomes 35, the JVM masks the distance to `35 & 31 = 3`
(§III.14), and the decoder returns `56` instead of `7`. A shift that ran off the
end would have given `0`; the masking gives a number that looks like data.

**The rule.** Name what a counter measures, out loud, before using it in
arithmetic. `offset` is where a unit starts in a container; `shift` is where a
group lands inside the unit. A conversion between them is always a subtraction:
`bytes read = pos - offset`.

**Why the build does not catch it.** Both are `Int`. Both produce values in
range. One of them is wrong.

---

## 5. Off-by-one in a limit

```text
written      admits      intended
----------   ---------   ------------------
pos > 5      6 bytes     5
k > 5        6 bytes     5
```

The counter is zero-based; the budget is one-based. `k` reaches `4` on the fifth
byte, so the rejection belongs at `k == 5`, not after it.

**The rule.** Write the boundary row explicitly before writing the comparison:

```text
k=0  1st byte   valid
k=4  5th byte   valid, and the last one
k=5  6th byte   REJECT here
```

**Why the build does not catch it — and this is the important part.** The spec's
over-long test constructs **seven** bytes (six `0x80` then `0x01`), so `k > 5`
does eventually fire and the test **passes for the wrong reason**. A six-byte
input (five `0x80` then `0x00`) was accepted as valid, and nothing in the suite
built that input.

> A green test tells you the code agreed with the test. It does not tell you the
> test asked the right question.

---

## 6. Wrong collection operator

```text
written                          problem                        cost
------------------------------   ----------------------------   -----------------
sliceA +: child +: sliceB        `+:` prepends ONE element,     compile error
                                 not a collection
acc :+ byte  /  acc :+ i         appends in O(n) on a List      quadratic
```

The first failed to compile — `Found: IArray[A], Required: A` — and is the only
entry in this file the build caught. The second compiled and passed every test,
because correctness and complexity are different questions: `toList` declared
O(size) in its Scaladoc and delivered O(size²), 2080 cons cells for `Full` where
128 suffice.

**The rule.** The colon points at the collection:

```text
element +: collection        collection :+ element        collection ++ collection
```

An operator ending in `:` is right-associative and its method belongs to the
right operand — which is why `x +: xs` calls `xs.+:(x)` and why `1 :: 2 :: Nil`
chains without parentheses. And on `List`: `::` is O(1), `:+` is O(n), because
cons cells are immutable and prepending shares the tail while appending must copy
it.

**Why the build catches only one.** A collection where an element belongs is a
type error. A linear operator used in a loop is not — nothing in the type system
records complexity, and the declared bound lives in prose.

---

## 7. A default parameter hiding an omission

```scala
def loop(pos: Int = 0, acc: Int = 0) = ...
...
loop()            // offset silently ignored
```

`decodeAt` took an `offset` and never used it: the inner loop started at the
default. Every test that decoded a single varint from position zero passed, and
the failure appeared only when a second varint followed the first.

The same shape, in another form: `encode` called `loop(n)` instead of
`loop(zigZagEncode(n))` — a stage of the pipeline omitted, with the encoder still
producing well-formed output, just five bytes where one would do.

**The rule.** A default value is for a parameter a caller may legitimately omit.
It is not a convenience for the implementation, and it is never appropriate on a
recursive helper's seed when that seed comes from the enclosing method's own
argument. Without the default, `loop()` would not have compiled.

**Why the build does not catch it.** A default parameter makes the incomplete
call legal by construction. That is its entire purpose; here the purpose was
misapplied.

---

## How to use this file

Before committing an exercise, read the seven headings and ask each one of the
diff. It takes a minute, and five of the seven are invisible to `-Wall -Werror`.

The common thread is worth stating once. Every pattern here produces **a wrong
answer rather than a failure** — a plausible number, a valid array one element
short, a `Boolean` that is always `false`. This annex's guide gives traps their
own Part for exactly this reason (contract rule 7), and these are the composition
counterparts to the mechanism traps recorded there.
