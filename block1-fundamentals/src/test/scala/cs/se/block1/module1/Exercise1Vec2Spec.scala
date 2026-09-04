package cs.se.block1.module1

import scala.util.Random

/** Exercise 1 (Easy) — Vec2: component-wise algebra and its monoid laws. */
class Exercise1Vec2Spec extends Module1Harness:

  test("addition is component-wise") {
    val sum = Vec2(1.0, 2.0) + Vec2(3.0, 4.0)
    assertEqualsDouble(sum.x, 4.0, Tolerance)
    assertEqualsDouble(sum.y, 6.0, Tolerance)
  }

  test("(Vec2, +, Zero) is a commutative monoid") {
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

  test("scalar multiplication distributes over addition") {
    val rng = Random(Seed)
    (1 to 200).foreach { _ =>
      val a = Vec2(rng.between(-100, 100).toDouble, rng.between(-100, 100).toDouble)
      val b = Vec2(rng.between(-100, 100).toDouble, rng.between(-100, 100).toDouble)
      val k = rng.between(-10, 10).toDouble
      assertEquals((a + b) * k, (a * k) + (b * k), "k(a+b) != ka + kb")
    }
    assertEquals(Vec2(3.0, -7.0) * 1.0, Vec2(3.0, -7.0), "1 is not the scalar identity")
  }

  test("dot is commutative and norm is sqrt of self-dot") {
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

end Exercise1Vec2Spec
