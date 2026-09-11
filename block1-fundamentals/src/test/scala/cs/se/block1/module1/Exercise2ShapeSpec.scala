package cs.se.block1.module1

import scala.util.Random

/** Exercise 2 (Easy) — Shape: an enum ADT and its area fold. */
class Exercise2ShapeSpec extends Module1Harness:

  /** The same element count `Exercise4BoxingSpec` uses, so that the two
    * per-element figures are directly comparable.
    */
  private val FoldSize = 100_000

  private def randomShape(rng: Random): Shape =
    rng.between(0, 3) match
      case 0 => Shape.Circle(rng.between(1.0, 10.0))
      case 1 => Shape.Rectangle(rng.between(1.0, 10.0), rng.between(1.0, 10.0))
      case _ => Shape.Triangle(rng.between(1.0, 10.0), rng.between(1.0, 10.0))

  test("area is correct for every constructor") {
    assertEqualsDouble(Shape.Circle(2.0).area, math.Pi * 4.0, Tolerance)
    assertEqualsDouble(Shape.Rectangle(3.0, 4.0).area, 12.0, Tolerance)
    assertEqualsDouble(Shape.Triangle(6.0, 5.0).area, 15.0, Tolerance)
    assertEqualsDouble(Shape.Circle(0.0).area, 0.0, Tolerance)
  }

  test("totalArea folds, and the empty list is the monoid identity") {
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

  test("totalArea allocates for the fold, not for the shapes") {
    val rng = Random(Seed)
    val shapes = List.fill(FoldSize)(randomShape(rng))
    val ints = List.fill(FoldSize)(rng.between(1, 1_000))

    // Both lists are fully built before the counter is read for the first time.
    // What this measures is therefore what the fold allocates, never what the
    // data cost to construct — the distinction the module exists to teach.
    warmup(20)(Shape.totalArea(shapes))
    warmup(20)(Boxing.sumBoxed(ints))

    val areaBytes = bytesOf(Shape.totalArea(shapes))
    val boxedBytes = bytesOf(Boxing.sumBoxed(ints))

    report(s"totalArea over $FoldSize shapes (bytes)", areaBytes)
    report("totalArea per element (bytes)", areaBytes / FoldSize)
    report(s"sumBoxed over $FoldSize ints (bytes)", boxedBytes)
    report("sumBoxed per element (bytes)", boxedBytes / FoldSize)

    // Deliberately loose, and deliberately not a constant. Whether C2
    // scalar-replaces the boxed accumulator of a generic fold is a property of
    // this JVM rather than of this code, so the experiment must not prejudge
    // it: the two reported per-element figures are the result, and comparing
    // them against each other is the point of measuring both in one run.
    //
    // The single outcome this assertion rules out is the one that would mean
    // the measurement is not measuring the fold at all. A boxed Double costs 24
    // bytes; a Rectangle or Triangle costs 32 and a Circle 24. Allocation above
    // 48 bytes per element cannot be a boxed accumulator alone, and would mean
    // totalArea is constructing shapes that were supposed to already exist.
    assert(
      areaBytes < FoldSize.toLong * 48L,
      s"""totalArea allocated $areaBytes bytes over $FoldSize elements, which is
         |${areaBytes / FoldSize} per element. A boxed accumulator accounts for at most 24.
         |Anything above 48 means the fold is allocating per-element objects beyond the
         |accumulator — check that `area` is not rebuilding the shape it matches on.""".stripMargin
    )
  }
end Exercise2ShapeSpec
