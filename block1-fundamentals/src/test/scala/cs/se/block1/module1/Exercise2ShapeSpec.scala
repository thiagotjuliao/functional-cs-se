package cs.se.block1.module1

/** Exercise 2 (Easy) — Shape: an enum ADT and its area fold. */
class Exercise2ShapeSpec extends Module1Harness:

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
