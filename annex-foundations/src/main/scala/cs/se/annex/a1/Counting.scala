package cs.se.annex.a1

/** Exercise 4 (Medium) — Population count, three ways.
  *
  * `popcount` is the primitive that makes Hash Array Mapped Tries viable, and
  * therefore the primitive that makes Scala's immutable `Map` and `Vector`
  * competitive with mutable hash tables (guide, Part IV.20). Exercise 8 consumes
  * what you build here.
  *
  * The three implementations have genuinely different complexity profiles, and
  * the exercise is to feel that difference rather than to read about it:
  *
  *   - `naive` is O(32) with 32 data-dependent iterations;
  *   - `kernighan` is O(popcount(x)) — its cost depends on the *value* of the
  *     input, not its size;
  *   - `swar` is O(log 32) with no data dependence and no branches at all.
  *
  * `java.lang.Integer.bitCount` is forbidden inside this object: it is a HotSpot
  * intrinsic compiled to a single `POPCNT` instruction, and it is the oracle the
  * test suite measures you against.
  */
object PopCount:

  /** Count set bits by inspecting all 32 positions.
    *
    * Express it as a fold or a `@tailrec` helper over the shift distances. The
    * obvious `while` loop is banned, and rewriting it functionally is the point
    * of the "easy" version.
    */
  def naive(x: Int): Int = ???

  /** Count set bits by repeatedly clearing the lowest set one.
    *
    * Kernighan's algorithm: `x & (x - 1)` removes exactly one set bit
    * (guide, Part V), so the number of iterations until zero *is* the answer. Must be
    * `@tailrec`.
    *
    * State in your Scaladoc why this terminates for `Int.MinValue`, whose bit
    * pattern is a single set bit in the sign position.
    */
  def kernighan(x: Int): Int = ???

  /** Count set bits by parallel field summation (SIMD Within A Register).
    *
    * Sum adjacent 1-bit fields into 2-bit fields, then 2 into 4, 4 into 8, and
    * finally collapse the four byte lanes with one multiply (guide, Part VI). Five
    * expressions, no recursion, no branches, constant time.
    *
    * The magic constants `0x55555555`, `0x33333333`, `0x0f0f0f0f` and
    * `0x01010101` are masks selecting alternate fields of width 1, 2, 4 and 8.
    * Write them out in binary before using them, and be able to explain why the
    * final multiply performs four additions at once.
    */
  def swar(x: Int): Int = ???
end PopCount

/** Exercise 5 (Medium) — Arithmetic reconstructed from Boolean algebra.
  *
  * Addition is not a primitive that sits above bit manipulation; it is a fixed
  * point of a Boolean recurrence (guide, Part IV.23). Proving that to yourself
  * dissolves the false hierarchy between "arithmetic" and "bit twiddling".
  *
  * Permitted operators throughout this object: `^`, `&`, `|`, `~`, `<<`, `>>`,
  * `>>>`, comparison against zero, and calls to the other members of this
  * object. **Forbidden:** `+`, `-`, `*`, `/`, `%` on `Int`, in any form,
  * including inside loop counters.
  */
object BitAdder:

  /** Sum of `a` and `b` in `Z/2^32 Z`.
    *
    * The recurrence: the carry-free sum is `a ^ b`, and the carry generated is
    * `(a & b) << 1`. Recur until the carry word is zero.
    *
    * Must be `@tailrec`, and must agree with `+` on **every** pair of `Int`s,
    * including pairs that overflow — wrapping is the correct behaviour of the
    * ring, not an error.
    *
    * Termination argument you must be able to give: why does the carry word
    * strictly shrink, and why is 32 iterations therefore an upper bound?
    */
  def add(a: Int, b: Int): Int = ???

  /** Negation, from the master identity. May use `add`. */
  def negate(a: Int): Int = ???

  /** `a - b`, expressed as addition of the negation. */
  def subtract(a: Int, b: Int): Int = ???

  /** Product of `a` and `b` in `Z/2^32 Z`, by shift-and-add.
    *
    * For each set bit `i` of `b`, accumulate `a << i`. This is exactly the
    * schoolbook algorithm in base 2, and it is why the `0x01010101` multiply in
    * `PopCount.swar` sums four byte lanes.
    *
    * Must be `@tailrec` and must agree with `*` on every pair, including
    * negative operands — think carefully about whether you need a logical or an
    * arithmetic shift when consuming the bits of `b`.
    */
  def multiply(a: Int, b: Int): Int = ???
end BitAdder
