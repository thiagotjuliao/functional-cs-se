package cs.se.block1.module1

import scala.concurrent.duration.*
import scala.util.Random

/** Validation suite for Module 1 — JVM Semantics & Immutability Allocation
  * Stress.
  *
  * The suite has two kinds of tests, and the distinction matters:
  *
  *   - **Correctness tests** assert mathematical facts. They must pass on any
  *     JVM, on any machine, at any load.
  *   - **Allocation tests** assert facts about *this* JVM's optimiser. They are
  *     experiments, and their thresholds are deliberately loose so that they
  *     fail only when the underlying claim is false — not when the machine is
  *     busy. Re-running the suite with `-XX:-DoEscapeAnalysis` must flip
  *     `sumNorms is scalar-replaced` to red. That flip is the result.
  *
  * No timing test asserts an absolute duration. A test that fails because a
  * laptop throttled is not a test, it is a coin toss.
  */
class Module1Spec extends munit.FunSuite:

  override val munitTimeout: Duration = 120.seconds

  // ---------------------------------------------------------------------------
  // Harness
  // ---------------------------------------------------------------------------

  private val Seed = 20260903L
  private val Tolerance = 1e-9
  private val EscapeSize = 200_000

  /** Bytes allocated while evaluating `body`, discarding its value. */
  private def bytesOf[A](body: => A): Long = AllocationProbe.measure(body)._2

  /** Run `body` enough times for C2 to compile and optimise it. */
  private def warmup[A](times: Int)(body: => A): Unit =
    (1 to times).foreach(_ => body)

  /** Integer-valued doubles: exact under IEEE-754, so associativity laws can be
    * asserted without a tolerance hiding a real defect.
    */
  private def exactDoubles(rng: Random, n: Int): Array[Double] =
    Array.fill(n)(rng.between(-1000, 1000).toDouble)

  private def report(label: String, value: Any): Unit =
    println(s"  [observation] $label = ${value.toString}")

  // ---------------------------------------------------------------------------
  // Exercise 1 (Easy) — Vec2
  // ---------------------------------------------------------------------------

  test("E1: Vec2 addition is component-wise") {
    val sum = Vec2(1.0, 2.0) + Vec2(3.0, 4.0)
    assertEqualsDouble(sum.x, 4.0, Tolerance)
    assertEqualsDouble(sum.y, 6.0, Tolerance)
  }

  test("E1: (Vec2, +, Zero) is a commutative monoid") {
    val rng = Random(Seed)
    val samples = List.fill(200) {
      Vec2(rng.between(-1000, 1000).toDouble, rng.between(-1000, 1000).toDouble)
    }

    samples.foreach { v =>
      assertEquals(v + Vec2.Zero, v, "right identity failed")
      assertEquals(Vec2.Zero + v, v, "left identity failed")
    }

    samples.sliding(3).foreach {
      case a :: b :: c :: Nil =>
        assertEquals((a + b) + c, a + (b + c), "associativity failed")
        assertEquals(a + b, b + a, "commutativity failed")
      case _ => ()
    }
  }

  test("E1: scalar multiplication distributes over addition") {
    val rng = Random(Seed)
    (1 to 200).foreach { _ =>
      val a = Vec2(rng.between(-100, 100).toDouble, rng.between(-100, 100).toDouble)
      val b = Vec2(rng.between(-100, 100).toDouble, rng.between(-100, 100).toDouble)
      val k = rng.between(-10, 10).toDouble
      assertEquals((a + b) * k, (a * k) + (b * k), "k(a+b) != ka + kb")
    }
    assertEquals(Vec2(3.0, -7.0) * 1.0, Vec2(3.0, -7.0), "1 is not the scalar identity")
  }

  test("E1: dot is commutative and norm is sqrt of self-dot") {
    val rng = Random(Seed)
    (1 to 200).foreach { _ =>
      val a = Vec2(rng.between(-100, 100).toDouble, rng.between(-100, 100).toDouble)
      val b = Vec2(rng.between(-100, 100).toDouble, rng.between(-100, 100).toDouble)
      assertEqualsDouble(a.dot(b), b.dot(a), Tolerance)
      assertEqualsDouble(a.norm, math.sqrt(a.dot(a)), Tolerance)
      assert(a.norm >= 0.0, s"norm must be non-negative, got ${a.norm} for ${a.toString}")
    }
    assertEqualsDouble(Vec2(3.0, 4.0).norm, 5.0, Tolerance)
    assertEqualsDouble(Vec2.Zero.norm, 0.0, Tolerance)
  }

  // ---------------------------------------------------------------------------
  // Exercise 2 (Easy) — Shape
  // ---------------------------------------------------------------------------

  test("E2: area is correct for every constructor") {
    assertEqualsDouble(Shape.Circle(2.0).area, math.Pi * 4.0, Tolerance)
    assertEqualsDouble(Shape.Rectangle(3.0, 4.0).area, 12.0, Tolerance)
    assertEqualsDouble(Shape.Triangle(6.0, 5.0).area, 15.0, Tolerance)
    assertEqualsDouble(Shape.Circle(0.0).area, 0.0, Tolerance)
  }

  test("E2: totalArea folds, and the empty list is the monoid identity") {
    assertEqualsDouble(Shape.totalArea(Nil), 0.0, Tolerance)

    val shapes = List(
      Shape.Rectangle(2.0, 3.0),
      Shape.Triangle(4.0, 2.0),
      Shape.Rectangle(1.0, 1.0)
    )
    assertEqualsDouble(Shape.totalArea(shapes), 11.0, Tolerance)

    // Folding is order-independent for a commutative monoid.
    assertEqualsDouble(Shape.totalArea(shapes.reverse), Shape.totalArea(shapes), Tolerance)
  }

  // ---------------------------------------------------------------------------
  // Exercise 3 (Easy) — AllocationProbe
  // ---------------------------------------------------------------------------

  test("E3: the probe is monotonic and returns the body's value") {
    val before = AllocationProbe.allocatedBytes
    val after = AllocationProbe.allocatedBytes
    assert(after >= before, s"allocatedBytes went backwards: $before -> $after")

    val (value, bytes) = AllocationProbe.measure(Array.fill(10_000)(Vec2(1.0, 2.0)))
    assertEquals(value.length, 10_000)
    assert(bytes > 0L, "allocating 10,000 objects must be visible to the probe")
  }

  test("E3: the probe evaluates the body exactly once") {
    // A by-name body evaluated twice — or zero times — is the classic bug.
    // 10,000 Vec2 instances plus the backing array is ~360 KB; a double
    // evaluation would show up as roughly twice that.
    val single = bytesOf(Array.fill(10_000)(Vec2(1.0, 2.0)))
    val double = bytesOf {
      val first = Array.fill(10_000)(Vec2(1.0, 2.0))
      val second = Array.fill(10_000)(Vec2(1.0, 2.0))
      first.length + second.length
    }
    assert(
      double > single * 3 / 2,
      s"expected the two-allocation body to cost clearly more: single=$single double=$double"
    )
  }

  test("E3: the probe's own overhead is small") {
    warmup(1_000)(bytesOf(()))
    val overhead = bytesOf(())
    report("probe overhead (bytes)", overhead)
    assert(
      overhead >= 0L && overhead < 1_024L,
      s"instrument overhead of $overhead bytes is too large to measure anything with"
    )
  }

  // ---------------------------------------------------------------------------
  // Exercise 4 (Medium) — Boxing
  // ---------------------------------------------------------------------------

  test("E4: both summations agree, on every input") {
    assertEquals(Boxing.sumBoxed(Nil), 0L)
    assertEquals(Boxing.sumPrimitive(Array.empty[Int]), 0L)

    val rng = Random(Seed)
    (1 to 50).foreach { _ =>
      val arr = Array.fill(rng.between(0, 500))(rng.between(-100_000, 100_000))
      assertEquals(Boxing.sumPrimitive(arr), Boxing.sumBoxed(arr.toList))
    }

    // Overflow safety: the accumulator must be a Long, not an Int.
    val large = Array.fill(1_000)(Int.MaxValue)
    assertEquals(Boxing.sumPrimitive(large), 1_000L * Int.MaxValue)
  }

  test("E4: sumPrimitive allocates nothing in steady state") {
    val arr = Array.fill(100_000)(Random(Seed).between(1_000, 100_000))
    warmup(20)(Boxing.sumPrimitive(arr))

    val primitiveBytes = bytesOf(Boxing.sumPrimitive(arr))
    val list = arr.toList
    warmup(20)(Boxing.sumBoxed(list))
    val boxedBytes = bytesOf(Boxing.sumBoxed(list))

    report("sumPrimitive over 100k Ints (bytes)", primitiveBytes)
    report("sumBoxed over 100k Ints (bytes)", boxedBytes)

    assert(
      primitiveBytes < 2_048L,
      s"a tail-recursive walk over an int[] must allocate nothing; measured $primitiveBytes bytes. " +
        "A non-zero result means a generic combinator re-introduced boxing."
    )
  }

  // ---------------------------------------------------------------------------
  // Exercise 5 (Hard) — Escape analysis
  // ---------------------------------------------------------------------------

  test("E5: sumNorms is numerically correct") {
    val rng = Random(Seed)
    val xs = exactDoubles(rng, 1_000)
    val ys = exactDoubles(rng, 1_000)

    val expected = xs.indices.foldLeft(0.0) { (acc, i) =>
      acc + math.sqrt(xs(i) * xs(i) + ys(i) * ys(i))
    }
    assertEqualsDouble(Escape.sumNorms(xs, ys), expected, 1e-6)
    assertEqualsDouble(Escape.sumNorms(Array.empty, Array.empty), 0.0, Tolerance)
  }

  test("E5: collectVecs materialises every vector") {
    val rng = Random(Seed)
    val xs = exactDoubles(rng, 100)
    val ys = exactDoubles(rng, 100)
    val out = Escape.collectVecs(xs, ys)

    assertEquals(out.length, 100)
    assertEquals(out(0), Vec2(xs(0), ys(0)))
    assertEquals(out(99), Vec2(xs(99), ys(99)))
  }

  test("E5: sumNorms is scalar-replaced while collectVecs is not") {
    val rng = Random(Seed)
    val xs = exactDoubles(rng, EscapeSize)
    val ys = exactDoubles(rng, EscapeSize)

    warmup(10)(Escape.sumNorms(xs, ys))
    warmup(3)(Escape.collectVecs(xs, ys))

    val nonEscaping = bytesOf(Escape.sumNorms(xs, ys))
    val escaping = bytesOf(Escape.collectVecs(xs, ys))

    report(s"sumNorms over $EscapeSize elements (bytes)", nonEscaping)
    report(s"collectVecs over $EscapeSize elements (bytes)", escaping)
    report("ratio", if nonEscaping == 0L then "infinite" else escaping.toDouble / nonEscaping)

    assert(
      escaping > EscapeSize.toLong * 16L,
      s"the escaping variant must allocate at least one real object per element; measured $escaping bytes"
    )
    assert(
      nonEscaping < escaping / 4L,
      s"""sumNorms allocated $nonEscaping bytes against collectVecs' $escaping.
         |Either the Vec2 is escaping (check that nothing stores or returns it, and that the
         |method is small enough to inline), or escape analysis is disabled — which is exactly
         |what running with -XX:-DoEscapeAnalysis proves.""".stripMargin
    )
  }

  // ---------------------------------------------------------------------------
  // Exercise 6 (Medium) — WordStats
  // ---------------------------------------------------------------------------

  test("E6: wordFrequencies counts verbatim") {
    assertEquals(WordStats.wordFrequencies(Nil), Map.empty[String, Int])
    assertEquals(
      WordStats.wordFrequencies(List("a", "b", "a", "c", "a", "b")),
      Map("a" -> 3, "b" -> 2, "c" -> 1)
    )
    // No case folding, no trimming: normalisation is the caller's business.
    assertEquals(
      WordStats.wordFrequencies(List("Scala", "scala", " scala")),
      Map("Scala" -> 1, "scala" -> 1, " scala" -> 1)
    )
  }

  test("E6: the total count is preserved by the fold") {
    val rng = Random(Seed)
    val words = List.fill(5_000)(s"w${rng.between(0, 200)}")
    assertEquals(WordStats.wordFrequencies(words).values.sum, words.length)
  }

  test("E6: topN ordering is total and deterministic") {
    val freqs = Map("b" -> 5, "a" -> 5, "c" -> 9, "d" -> 1)

    // Descending count, ties broken by ascending word.
    assertEquals(
      WordStats.topN(freqs, 3),
      List("c" -> 9, "a" -> 5, "b" -> 5)
    )
    assertEquals(WordStats.topN(freqs, 0), Nil)
    assertEquals(WordStats.topN(freqs, -1), Nil)
    assertEquals(WordStats.topN(freqs, 100).length, 4)
    assertEquals(WordStats.topN(Map.empty[String, Int], 5), Nil)

    // Same input, repeated calls: identical output. Hash order must not leak.
    assertEquals(WordStats.topN(freqs, 4), WordStats.topN(freqs, 4))
  }

  // ---------------------------------------------------------------------------
  // Exercise 7 (Hard) — Footprint arithmetic
  // ---------------------------------------------------------------------------

  test("E7: align rounds up to the next multiple of 8 and is idempotent") {
    assertEquals(Footprint.align(0), 0)
    assertEquals(Footprint.align(1), 8)
    assertEquals(Footprint.align(8), 8)
    assertEquals(Footprint.align(9), 16)
    assertEquals(Footprint.align(12), 16)
    assertEquals(Footprint.align(28), 32)
    (0 to 500).foreach { n =>
      val aligned = Footprint.align(n)
      assertEquals(Footprint.align(aligned), aligned, s"align is not idempotent at $n")
      assert(aligned >= n, s"align($n) = $aligned shrank the object")
      assertEquals(aligned % 8, 0, s"align($n) = $aligned is not 8-byte aligned")
    }
  }

  test("E7: shallowSize reproduces the real HotSpot layout") {
    assertEquals(Footprint.shallowSize(0, 0, 0, 0, 0), 16, "bare object: 12-byte header padded")
    assertEquals(Footprint.shallowSize(0, 0, 0, 2, 0), 32, "Vec2(x: Double, y: Double)")
    assertEquals(Footprint.shallowSize(2, 0, 0, 0, 0), 24, "cons cell: head + tail references")
    assertEquals(Footprint.shallowSize(0, 1, 0, 0, 0), 16, "boxed java.lang.Integer")
    assertEquals(Footprint.shallowSize(0, 0, 1, 0, 0), 24, "boxed java.lang.Long")
    assertEquals(Footprint.shallowSize(0, 0, 0, 0, 1), 16, "one boolean field")
    assertEquals(Footprint.shallowSize(1, 1, 1, 1, 1), 40, "12 + 4 + 4 + 8 + 8 + 1 = 37 -> 40")
  }

  test("E7: the List[Int] tax over Array[Int] is exactly tenfold") {
    assertEquals(Footprint.arrayOfIntSize(0), 16L)
    assertEquals(Footprint.arrayOfIntSize(1), 24L, "16 + 4 = 20, padded to 24")
    assertEquals(Footprint.arrayOfIntSize(2), 24L)
    assertEquals(Footprint.arrayOfIntSize(1_000_000), 4_000_016L)

    assertEquals(Footprint.listOfIntSize(0), 0L, "Nil is a singleton and costs nothing here")
    assertEquals(Footprint.listOfIntSize(1), 40L, "24-byte cons cell + 16-byte boxed Integer")
    assertEquals(Footprint.listOfIntSize(1_000_000), 40_000_000L)

    val ratio = Footprint.listOfIntSize(1_000_000).toDouble / Footprint.arrayOfIntSize(1_000_000)
    assert(ratio > 9.9 && ratio < 10.1, s"expected a ~10x tax, derived $ratio")

    // No Int overflow at scale: this is why the return type is Long.
    assert(Footprint.arrayOfIntSize(1_000_000_000) > 0L, "arrayOfIntSize overflowed")
  }

  // ---------------------------------------------------------------------------
  // Exercise 8 (Hard) — allocation complexity
  // ---------------------------------------------------------------------------

  /** The control: string building by repeated concatenation. Every `+` copies
    * the whole accumulated prefix, making total allocation quadratic in the
    * output length. Do not imitate this in `Csv`.
    */
  private def naiveRender(rows: List[List[String]]): String =
    rows.foldLeft("") { (acc, row) =>
      val line = row.foldLeft("") { (cells, cell) =>
        if cells.isEmpty then cell else cells + "," + cell
      }
      if acc.isEmpty then line else acc + "\n" + line
    }

  test("E8: renderCsv produces the expected text") {
    assertEquals(Csv.renderCsv(Nil), "")
    assertEquals(Csv.renderCsv(List(Nil)), "")
    assertEquals(Csv.renderCsv(List(List("a"))), "a")
    assertEquals(Csv.renderCsv(List(List("a", "b"), List("c", "d"))), "a,b\nc,d")
    assertEquals(Csv.renderCsv(List(List("a"), Nil, List("b"))), "a\n\nb")

    val rng = Random(Seed)
    val rows = List.fill(200)(List.fill(5)(rng.alphanumeric.take(6).mkString))
    assertEquals(Csv.renderCsv(rows), naiveRender(rows), "must agree with the naive control")
  }

  test("E8: renderCsv allocates linearly, not quadratically") {
    val rng = Random(Seed)
    val rows = List.fill(2_000)(List.fill(8)(rng.alphanumeric.take(6).mkString))

    warmup(3)(Csv.renderCsv(rows))
    val linear = bytesOf(Csv.renderCsv(rows))
    val quadratic = bytesOf(naiveRender(rows))
    val outputLength = Csv.renderCsv(rows).length.toLong

    report("output length (chars)", outputLength)
    report("renderCsv (bytes)", linear)
    report("naive concatenation (bytes)", quadratic)

    assert(
      linear < quadratic / 10L,
      s"""renderCsv allocated $linear bytes against the naive control's $quadratic.
         |An O(n) renderer over a ~$outputLength character output should be several orders of
         |magnitude cheaper than O(n^2) concatenation.""".stripMargin
    )
  }

  // ---------------------------------------------------------------------------
  // Exercise 9 (Hard) — the timing harness
  // ---------------------------------------------------------------------------

  private def work(n: Int): Unit =
    Bench.consume((1 to n).foldLeft(0.0)((acc, i) => acc + math.sqrt(i.toDouble)))

  test("E9: consume is cheap and does not allocate") {
    warmup(10_000)(Bench.consume(math.Pi))
    val bytes = bytesOf(Bench.consume(math.Pi))
    report("consume (bytes)", bytes)
    assert(bytes < 512L, s"consume must not allocate; measured $bytes bytes")
  }

  test("E9: medianNanos reports a positive, plausible duration") {
    val nanos = Bench.medianNanos(1_000, 1_000)(() => work(100))
    report("medianNanos of work(100)", nanos)
    assert(nanos > 0L, "a non-trivial body cannot take zero nanoseconds")
    assert(nanos < 1_000_000_000L, s"$nanos ns for 100 square roots is not plausible")
  }

  test("E9: medianNanos separates a heavy body from a light one") {
    val light = Bench.medianNanos(2_000, 2_000)(() => work(10))
    val heavy = Bench.medianNanos(2_000, 2_000)(() => work(1_000))

    report("work(10) ns", light)
    report("work(1000) ns", heavy)

    // 100x the work. Demanding only 2x guards against a machine under load
    // while still failing loudly if dead code elimination deleted the body.
    assert(
      heavy > light * 2L,
      s"""100x the work measured as light=$light ns, heavy=$heavy ns.
         |Either the harness is not timing the body, or C2 eliminated the computation
         |because `consume` failed to make its result observable.""".stripMargin
    )
  }

end Module1Spec
