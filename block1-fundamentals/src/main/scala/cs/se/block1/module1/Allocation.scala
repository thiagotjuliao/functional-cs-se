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

  val HeaderBytes: Int = 12
  val ArrayHeaderBytes: Int = 16
  val ReferenceBytes: Int = 4
  val AlignmentBytes: Int = 8

  val IntegerBytes: Int = 4
  val LongBytes: Int = 8
  val DoubleBytes: Int = 8
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
