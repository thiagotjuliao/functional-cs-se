package cs.se.block1.module1

/** Exercise 4 (Medium) — the boxing tax, measured rather than assumed.
  *
  * Two summations that are mathematically identical and materially different at
  * the machine level. The spec asserts that they agree numerically; the
  * checklist asks you to record how far apart they are in bytes.
  */
object Boxing:

  /** Sum a `List[Int]` as a fold.
    *
    * Every element is already a boxed `java.lang.Integer` (16 bytes each, per
    * guide, Part I.4.2), and the `Long` accumulator is itself boxed on each
    * step of a generic fold. Whether C2 manages to scalar-replace that
    * accumulator is a question you should answer by measuring, not by guessing.
    *
    * The empty list must yield `0L`.
    */
  def sumBoxed(xs: List[Int]): Long =
    xs.foldLeft(0L)(_ + _)

  /** Sum an `Array[Int]` allocating nothing in steady state.
    *
    * `Array[Int]` is a true `int[]`: 4 bytes per element, no boxes. But calling
    * a generic combinator on it (`xs.foldLeft`, `xs.sum`) routes through an
    * erased signature and re-introduces boxing — which defeats the entire point
    * of the exercise.
    *
    * Constraints:
    *   - no `while`, no `var`, no mutable state;
    *   - no generic collection combinator over the elements;
    *   - a local, tail-recursive walk over indices is the intended shape, and
    *     it compiles to the same machine code the imperative loop would;
    *   - must agree with `sumBoxed(xs.toList)` for every input.
    */
  def sumPrimitive(xs: Array[Int]): Long =
    @scala.annotation.tailrec
    def loop(i: Int = 0, acc: Long = 0L): Long =
      if i == xs.length then acc
      else loop(i + 1, acc + xs(i))
    loop()
end Boxing

/** Exercise 5 (Hard) — the controlled experiment for escape analysis.
  *
  * These two functions do the same arithmetic over the same inputs and differ
  * in exactly one respect: whether the `Vec2` instances they construct can be
  * observed after the call returns. That single difference decides whether the
  * JVM allocates 6.4 MB or nothing at all.
  *
  * The spec asserts the ratio between them. Re-running the suite with
  * `-XX:-DoEscapeAnalysis` must make that assertion fail — and the failure is
  * the proof. See `docs/checklist.md` §E.
  */
object Escape:

  /** Sum the Euclidean norms of the vectors `(xs(i), ys(i))`.
    *
    * A `Vec2` must genuinely be constructed for each index — do not inline the
    * `sqrt` by hand, or you are proving nothing. The reference simply must not
    * escape: no storing it anywhere, no returning it, no passing it to a method
    * the compiler cannot see through.
    *
    * Domain: pairs of equal-length arrays. Within that domain the function is
    * total. Unequal lengths are **not** a domain case but a defect at the call
    * site — both arrays describe the same set of points, so they can only differ
    * in length if the caller built them wrongly — and the `require` marks the
    * boundary of the domain rather than handling an input. Encoding the
    * violation in the return type instead (`Option[Double]`) would force every
    * correct caller to handle an impossible case, and would invite the
    * `.getOrElse(0.0)` that turns a loud defect into a silent wrong answer.
    *
    * The interpolated message costs one captured `Function0` per call, 24 bytes,
    * whether or not the requirement holds; see `docs/challenge-log.md`, entry 6.
    * That cost is constant in the input size and was accepted deliberately.
    *
    * Constraints:
    *   - no `var`, no `while`, no mutable accumulator;
    *   - keep the method small — over `-XX:FreqInlineSize` (325 bytecodes)
    *     nothing gets inlined and the whole experiment collapses.
    */
  def sumNorms(xs: Array[Double], ys: Array[Double]): Double =
    require(
      xs.length == ys.length,
      s"both input arrays must be of the same size: got ${xs.length} and ${ys.length}"
    )
    @scala.annotation.tailrec
    def loop(i: Int = 0, acc: Double = 0.0): Double =
      if i == xs.length then acc
      else loop(i + 1, acc + Vec2(xs(i), ys(i)).norm)
    loop()

  /** The escaping control: materialize every vector into an array.
    *
    * This is the *same* construction work as `sumNorms`, except the references
    * leave the method. Escape analysis is therefore powerless and every `Vec2`
    * becomes a real 32-byte heap object.
    *
    * Implement it purely — `Array.tabulate` builds the array without a single
    * mutation at the source level.
    *
    * Same domain and same precondition as `sumNorms`, for the same reason.
    *
    * Note that `Array.tabulate` boxes the loop index: `Function1` has no
    * specialised variant with a reference return type, so each call goes through
    * the erased `apply(Object): Object`. That accounts for 3,197,952 of the
    * 10,398,016 bytes recorded in `docs/checklist.md`, §E — a third of this
    * function's allocation is not `Vec2` at all.
    */
  def collectVecs(xs: Array[Double], ys: Array[Double]): Array[Vec2] =
    require(
      xs.length == ys.length,
      s"Both input arrays must be of the same size: got ${xs.length} and ${ys.length}"
    )
    Array.tabulate(xs.length)(i => Vec2(xs(i), ys(i)))
end Escape

/** Exercise 7 (Hard) — heap arithmetic from first principles.
  *
  * No reflection, no JOL, no measurement: derive the numbers from the layout
  * rules in the guide, Part I.4.1. This is the exercise that converts the theory
  * from something you have read into something you can compute.
  *
  * Model assumptions, all of which hold on 64-bit HotSpot with a heap under
  * ~32 GB (verify with `java -XX:+PrintFlagsFinal -version | grep UseCompressedOops`):
  *   - object header: 12 bytes (8-byte mark word + 4-byte klass pointer);
  *   - array header: 16 bytes (object header + 4-byte length);
  *   - reference: 4 bytes (compressed oops);
  *   - every object's total size is padded up to a multiple of 8 bytes;
  *   - HotSpot reorders fields largest-first, so assume no internal padding —
  *     only the final alignment.
  */
object Footprint:

  // The eight constants of the model. None of them is asserted directly: each is
  // observed only through `shallowSize`, `arrayOfIntSize` and `listOfIntSize`,
  // and `align` is a rounding function that erases any error smaller than eight
  // bytes on the way out. Each doc below therefore names the assertion that
  // would fail if the value were wrong — or states plainly that none would, and
  // how wrong it could be. See `docs/error-patterns.md`, pattern 4, for the
  // replay that produced these ranges, and `docs/challenge-log.md`, entry 9, for
  // the technique that pins one.

  /** Object header: an 8-byte mark word plus a 4-byte klass pointer.
    *
    * **Unpinned, and unpinnable.** Any value in `9, 10, 11, 12` passes the whole
    * suite. Worse, no assertion on `shallowSize` could ever separate them: to
    * distinguish `H` from `H - 1` some field sum `S` would have to put a multiple
    * of 8 inside `(H - 1 + S, H + S]`, but whenever `H + S` is that multiple,
    * `H - 1 + S` rounds up to it as well. The two agree for every `S`.
    *
    * This constant enters every expression with coefficient 1 and can never be
    * multiplied, so the model observes its residue class modulo 8 and never its
    * value. The 12 comes from the JVM, not from this suite.
    */
  val HeaderBytes: Int = 12

  /** Array header: the object header plus a 4-byte length field.
    *
    * **Unpinned.** Any value in `13, 14, 15, 16` passes, for the same reason as
    * `HeaderBytes`: `arrayOfIntSize` adds it to the element bytes once, with
    * coefficient 1, and the final alignment absorbs a difference of up to 7.
    */
  val ArrayHeaderBytes: Int = 16

  /** Width of one reference, with compressed oops enabled.
    *
    * **Unpinned.** Any value in `3, 4, 5, 6` passes. It appears with coefficient
    * 2 in the cons-cell assertion `shallowSize(2, 0, 0, 0, 0) == 24`, so a
    * one-unit error moves the sum by 2 — still inside the 8-byte window that
    * `align` erases.
    *
    * Note that this is the constant whose accidental equality with
    * `IntegerBytes` hid a real defect: `shallowSize(1, 0, 0, 0, 0)` and
    * `shallowSize(0, 1, 0, 0, 0)` are both 16 only because both widths are 4
    * here. See `docs/error-patterns.md`, pattern 2.
    */
  val ReferenceBytes: Int = 4

  /** Alignment granularity: every object's size is a multiple of this.
    *
    * **Pinned.** `Exercise7FootprintSpec` walks `align` over `0 to 500` and
    * asserts `aligned % 8 == 0` at every point. That is a property over a range
    * rather than a value at a point, which is one of the two ways to defeat the
    * rounding — and the only constant here pinned that way.
    */
  val AlignmentBytes: Int = 8

  /** Width of a primitive `Int` field. Not the size of a boxed
    * `java.lang.Integer`, which is `shallowSize(0, 1, 0, 0, 0)` = 16.
    *
    * **Pinned.** `arrayOfIntSize(1_000_000) == 4_000_016` multiplies it by a
    * million, so a one-unit error moves the result by a million bytes. That is
    * the other way to defeat the rounding: make the coefficient large enough
    * that the error cannot hide inside 8 bytes.
    */
  val IntegerBytes: Int = 4

  /** Width of a primitive `Long` field: 64 bits, therefore 8 bytes.
    *
    * **Unpinned.** Any value in `5, 6, 7, 8, 9, 10, 11` passes. It is observed
    * by `shallowSize(0, 0, 1, 0, 0) == 24` and `shallowSize(1, 1, 1, 1, 1) == 40`,
    * both with coefficient 1.
    *
    * This constant was written as `16` in an earlier draft — the bit width
    * halved by eye rather than by division. The suite caught it, but only
    * because the spec author happened to assert an object with a `Long` field;
    * see `docs/error-patterns.md`, pattern 1.
    */
  val LongBytes: Int = 8

  /** Width of a primitive `Double` field: IEEE-754 binary64, therefore 8 bytes.
    *
    * **Unpinned.** Any value in `7, 8, 9, 10` passes. It appears with
    * coefficient 2 in `shallowSize(0, 0, 0, 2, 0) == 32`, which narrows the
    * range but does not close it.
    */
  val DoubleBytes: Int = 8

  /** Storage width of a `Boolean` field. A `Boolean` carries one bit of
    * information; the JVM gives it a whole byte, in objects and in `boolean[]`
    * alike.
    *
    * **Pinned**, by `shallowSize(0, 0, 0, 0, 8) == 24`. Eight booleans turn a
    * one-byte error into an eight-byte one, and eight bytes is exactly what
    * `align` can no longer hide.
    *
    * Before that assertion existed this was the worst case in the model: any
    * value in `0, 1, 2, 3, 4` passed the entire suite — including **zero**, a
    * `Boolean` field costing nothing at all — because `align8(12 + 0)` and
    * `align8(12 + 4)` are both 16. The derivation is in
    * `docs/challenge-log.md`, entry 9.
    */
  val BooleanBytes: Int = 1

  /** Round `bytes` up to the next multiple of `AlignmentBytes`.
    *
    * Total on `[0, Int.MaxValue - 7]`, and the identity on values that are
    * already aligned.
    *
    * The upper bound is not a weakness of this implementation — it is forced by
    * the signature. For the top seven non-negative `Int` values the answer is
    * `2^31`, which no `Int` can hold, so `bytes + 7` wraps and the result comes
    * back as `Int.MinValue`:
    * {{{
    * align(2147483640) = 2147483640   // Int.MaxValue - 7, already aligned
    * align(2147483641) = -2147483648  // outside the domain
    * align(Int.MaxValue) = -2147483648
    * }}}
    * An earlier draft of this contract claimed totality over every non-negative
    * input. That claim is unsatisfiable by any `Int => Int`, and narrowing the
    * documented domain is the honest repair. `arrayOfIntSize` faces the same
    * arithmetic at a larger scale and answers it the other way, by moving to
    * `Long`.
    */
  def align(bytes: Int): Int =
    (bytes + (AlignmentBytes - 1)) & ~(AlignmentBytes - 1)

  private def alignL(bytes: Long): Long =
    (bytes + (AlignmentBytes - 1L)) & ~(AlignmentBytes - 1L)

  /** Shallow size, in bytes, of one instance of a class with the given fields.
    *
    * Worked examples you must reproduce:
    *   - no fields at all: 16 bytes (12 header, padded);
    *   - `Vec2(x: Double, y: Double)`: 32 bytes;
    *   - a cons cell `::` (head and tail references): 24 bytes;
    *   - a boxed `java.lang.Integer` (one int field): 16 bytes.
    *
    * "Shallow" means the object itself, excluding anything its references point
    * to. The distinction between shallow and retained size is the difference
    * between a heap dump you can read and one you cannot.
    */
  def shallowSize(
      references: Int,
      ints: Int,
      longs: Int,
      doubles: Int,
      booleans: Int
  ): Int =
    align(
      HeaderBytes +
        references * ReferenceBytes +
        ints * IntegerBytes +
        longs * LongBytes +
        doubles * DoubleBytes +
        booleans * BooleanBytes
    )

  /** Total heap cost of an `Array[Int]` of `length` elements.
    *
    * Includes the array header and the final 8-byte alignment. Returns `Long`
    * because a large array overflows `Int` — a bug you would find in production
    * rather than in a test, which is why the signature forecloses it.
    */
  def arrayOfIntSize(length: Int): Long =
    alignL(
      ArrayHeaderBytes.toLong +
        IntegerBytes * length.toLong
    )

  /** Total heap cost of a `List[Int]` of `length` elements.
    *
    * Count one cons cell plus one boxed `Integer` per element. `Nil` is a
    * singleton and must be excluded. Assume every value falls outside the
    * `Integer.valueOf` cache of −128..127, so no box is shared.
    *
    * For `length = 1,000,000` this must land on 40,000,000 — the 10× tax over
    * `arrayOfIntSize(1_000_000)`. If your two functions do not produce that
    * ratio, one of them is wrong.
    */
  def listOfIntSize(length: Int): Long =
    val headAndTail = shallowSize(2, 0, 0, 0, 0)
    val intBoxing = shallowSize(0, 1, 0, 0, 0)
    length.toLong * (headAndTail + intBoxing).toLong
end Footprint
