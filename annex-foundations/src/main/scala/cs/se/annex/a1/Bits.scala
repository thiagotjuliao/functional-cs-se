package cs.se.annex.a1

/** Exercise 1 (Easy) — Addressing individual bits.
  *
  * The vocabulary every later exercise is written in. A 32-bit word is read here
  * as the characteristic function of a subset of `{0, ..., 31}`: `testBit` is
  * membership, `setBit` is insertion, `clearBit` is deletion, `toggleBit` is
  * symmetric difference with a singleton.
  *
  * Bit indices are counted from the least significant end, so bit 0 has
  * positional weight 1 and bit 31 is the sign bit (guide, Part I.2).
  */
object Bits:

  /** Is bit `index` of `x` set?
    *
    * Precondition: `index` in `[0, 31]`. The JVM masks shift distances to their
    * low five bits (guide, Part III.14), so an out-of-range index silently aliases onto
    * a valid one rather than failing. Your implementation inherits that
    * behaviour; the tests only exercise the documented domain, but you must be
    * able to say what `testBit(1, 32)` returns and why.
    *
    * Must not allocate and must not branch.
    */
  def testBit(x: Int, index: Int): Boolean = ???

  /** `x` with bit `index` set. Idempotent: setting a set bit changes nothing. */
  def setBit(x: Int, index: Int): Int = ???

  /** `x` with bit `index` cleared. Idempotent, and the left inverse of `setBit`
    * only when the bit was clear to begin with — state which of these holds:
    *   - `clearBit(setBit(x, i), i) == clearBit(x, i)` for all `x`
    *   - `setBit(clearBit(x, i), i) == setBit(x, i)` for all `x`
    */
  def clearBit(x: Int, index: Int): Int = ???

  /** `x` with bit `index` flipped. Must be an involution: applying it twice with
    * the same index is the identity, for every `x`.
    */
  def toggleBit(x: Int, index: Int): Int = ???

  /** The 32-character, zero-padded binary rendering of `x`, most significant bit
    * first.
    *
    * `java.lang.Integer.toBinaryString` does **not** pad, which is how
    * off-by-one errors survive inspection. This is the microscope you will read
    * every other exercise through, so it must be exactly 32 characters for every
    * input, including `0` and negative values.
    *
    * Build it functionally — a range, a map and a `mkString`, or a fold. No
    * `StringBuilder`, no `var`, no loop.
    */
  def toBinaryString(x: Int): String = ???
end Bits

/** Exercise 2 (Easy) — Two's complement and branchless sign handling.
  *
  * Everything here descends from the master identity `-x == ~x + 1`
  * (guide, Part I.6.4). The point of the exercise is not the four lines of code
  * but the derivation: none of these may be implemented with an `if` on the
  * sign, and none may call `Math.abs`.
  */
object TwosComplement:

  /** Arithmetic negation implemented from the identity, using only `~` and `+`.
    *
    * Must agree with the built-in unary `-` on **every** `Int`, including
    * `Int.MinValue` — where both are the identity, because the signed range is
    * asymmetric (guide, Part I.6.5). That is not an edge case to special-case; it is
    * the correct answer in `Z/2^32 Z`.
    */
  def negate(x: Int): Int = ???

  /** `0` when `x >= 0`, and `-1` (all bits set) when `x < 0`.
    *
    * One arithmetic shift. This is the fundamental *mask* primitive: it converts
    * a sign into a value that can be `&`-ed and `^`-ed with, which is how every
    * branchless algorithm below eliminates its conditional.
    */
  def signMask(x: Int): Int = ???

  /** Absolute value with no branch and no call to `Math.abs`.
    *
    * Hint, not a solution: with `m = signMask(x)`, the expression `(x ^ m) - m`
    * is the identity when `m == 0` and is `~x + 1` when `m == -1`. Prove that
    * before you type it.
    *
    * Document what it returns for `Int.MinValue`, and why that is forced rather
    * than chosen.
    */
  def absBranchless(x: Int): Int = ???

  /** Do `x` and `y` have the same sign? Zero counts as non-negative.
    *
    * One `^` and one comparison. No multiplication (it overflows) and no
    * comparison of `x < 0` against `y < 0` (that is the branching version).
    */
  def sameSign(x: Int, y: Int): Boolean = ???

  /** Division by two rounding toward **negative infinity**, i.e. `floor(x / 2)`.
    *
    * Scala's `/` truncates toward zero, so this must *disagree* with `x / 2` on
    * every negative odd input: `floorDiv2(-7) == -4` while `-7 / 2 == -3`
    * (guide, Part III.15). Implement it as a single shift, and then answer in the
    * checklist whether the compiler emits the same instructions for `x / 2`.
    */
  def floorDiv2(x: Int): Int = ???
end TwosComplement

/** Exercise 3 (Easy) — Powers of two.
  *
  * The arithmetic that makes hash tables and tries fast. Every function here
  * must run in constant time with no loop and no floating point: `Math.log`,
  * `Math.pow` and `Math.sqrt` are forbidden, because their rounding makes them
  * wrong near the boundaries in ways that are hard to see and easy to ship.
  */
object PowersOfTwo:

  /** Is `x` exactly `2^k` for some `k >= 0`?
    *
    * `0` is **not** a power of two, and neither is any negative number — note
    * that `Int.MinValue` is `-2^31` and its bit pattern has a single set bit, so
    * a naive `x & (x - 1) == 0` test accepts it. Handle that.
    */
  def isPowerOfTwo(x: Int): Boolean = ???

  /** `x mod n` computed by masking, valid **only** when `n` is a power of two.
    *
    * Precondition: `isPowerOfTwo(n)`. The precondition is the exercise: this is
    * the single most-used identity in hash table implementations (guide, Part IV.19)
    * and also the most-misapplied. Encode the precondition in the return type
    * rather than trusting the caller.
    *
    * For non-negative `x` the result must equal `x % n`.
    */
  def modPowerOfTwo(x: Int, n: Int): Option[Int] = ???

  /** The smallest power of two greater than or equal to `x`.
    *
    * Returns `1` for every `x <= 1`. Returns `None` when the answer is not
    * representable as a positive `Int`, i.e. when `x > 2^30`.
    *
    * The classical implementation is the bit-smearing cascade
    * (`x |= x >>> 1; x |= x >>> 2; ...`) expressed as a fold over the shift
    * distances `1, 2, 4, 8, 16`. Write it that way: it is a fold, not a loop,
    * and seeing that is half the point.
    */
  def nextPowerOfTwo(x: Int): Option[Int] = ???

  /** `floor(log2(x))`, i.e. the index of the highest set bit.
    *
    * `None` for `x <= 0`, where the logarithm is undefined — the partiality is
    * real and must live in the type.
    *
    * Do **not** call `Integer.numberOfLeadingZeros` here; it is the oracle the
    * tests compare you against. Build it from the bit-smearing cascade of
    * `nextPowerOfTwo` plus a population count, or from a branchless binary
    * search over the five shift distances.
    */
  def log2Floor(x: Int): Option[Int] = ???
end PowersOfTwo
