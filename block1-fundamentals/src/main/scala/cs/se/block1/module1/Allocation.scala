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
    * module guide §1.2), and the `Long` accumulator is itself boxed on each
    * step of a generic fold. Whether C2 manages to scalar-replace that
    * accumulator is a question you should answer by measuring, not by guessing.
    *
    * The empty list must yield `0L`.
    */
  def sumBoxed(xs: List[Int]): Long = ???

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
  def sumPrimitive(xs: Array[Int]): Long = ???
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
    * Constraints:
    *   - `xs` and `ys` must have equal length; encode how you handle the
    *     violation, and justify the choice;
    *   - no `var`, no `while`, no mutable accumulator;
    *   - keep the method small — over `-XX:FreqInlineSize` (325 bytecodes)
    *     nothing gets inlined and the whole experiment collapses.
    */
  def sumNorms(xs: Array[Double], ys: Array[Double]): Double = ???

  /** The escaping control: materialise every vector into an array.
    *
    * This is the *same* construction work as `sumNorms`, except the references
    * leave the method. Escape analysis is therefore powerless and every `Vec2`
    * becomes a real 32-byte heap object.
    *
    * Implement it purely — `Array.tabulate` builds the array without a single
    * mutation at the source level.
    */
  def collectVecs(xs: Array[Double], ys: Array[Double]): Array[Vec2] = ???
end Escape

/** Exercise 7 (Hard) — heap arithmetic from first principles.
  *
  * No reflection, no JOL, no measurement: derive the numbers from the layout
  * rules in module guide §1.2. This is the exercise that converts the theory
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

  /** Round `bytes` up to the next multiple of `AlignmentBytes`.
    *
    * Must be total for all non-negative inputs, and must be the identity on
    * values that are already aligned. Bitwise arithmetic is welcome here — it
    * is a preview of the codec work in Module 12.
    */
  def align(bytes: Int): Int = ???

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
  ): Int = ???

  /** Total heap cost of an `Array[Int]` of `length` elements.
    *
    * Includes the array header and the final 8-byte alignment. Returns `Long`
    * because a large array overflows `Int` — a bug you would find in production
    * rather than in a test, which is why the signature forecloses it.
    */
  def arrayOfIntSize(length: Int): Long = ???

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
  def listOfIntSize(length: Int): Long = ???
end Footprint
