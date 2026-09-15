package cs.se.block1.module3

import cs.se.block1.module2.MyList.*

/** Exercise 3 — accumulators that are not running totals. */
class Exercise3ArithmeticSpec extends Module3Harness:

  test("gcd satisfies its defining equations") {
    assertEquals(Arithmetic.gcd(12, 18), 6)
    assertEquals(Arithmetic.gcd(18, 12), 6, "gcd is symmetric")
    assertEquals(Arithmetic.gcd(7, 13), 1, "coprime")
    assertEquals(Arithmetic.gcd(9, 0), 9, "gcd(a, 0) = a")
    assertEquals(Arithmetic.gcd(0, 9), 9)
    assertEquals(Arithmetic.gcd(270, 192), 6)

    // The law, on a sample rather than on one pair: every common divisor of a
    // and b divides the gcd, and the gcd divides both.
    val pairs = for a <- 1 to 40; b <- 1 to 40 yield (a, b)
    pairs.foreach { (a, b) =>
      val g = Arithmetic.gcd(a, b)
      assert(g > 0 && a % g == 0 && b % g == 0, s"gcd($a, $b) = $g does not divide both")
      (1 to math.min(a, b)).foreach { d =>
        if a % d == 0 && b % d == 0 then
          assert(d <= g, s"$d divides both and exceeds gcd($a, $b) = $g")
      }
    }
  }

  test("gcd is not bounded by the size of its arguments") {
    // Euclid's worst case is consecutive Fibonacci numbers, and even there the
    // depth is logarithmic. If this overflows, the implementation is subtracting
    // rather than taking a remainder.
    assert(survives(Arithmetic.gcd(1836311903, 1134903170)), "consecutive Fibonacci numbers")
  }

  test("power squares rather than multiplying") {
    assertEquals(Arithmetic.power(2, 10), 1024L)
    assertEquals(Arithmetic.power(3, 4), 81L)
    assertEquals(Arithmetic.power(5, 1), 5L)
    assertEquals(Arithmetic.power(7, 0), 1L, "anything to the zero is one")
    assertEquals(Arithmetic.power(0, 5), 0L)
    assertEquals(Arithmetic.power(2, -3), 1L, "a non-positive exponent yields 1 by contract")
    (0 to 20).foreach(e => assertEquals(Arithmetic.power(2, e), 1L << e, s"2^$e"))
  }

  test("digits reads most significant first") {
    assertEquals(Arithmetic.digits(1024).toScalaList, List(1, 0, 2, 4))
    assertEquals(Arithmetic.digits(0).toScalaList, List(0), "zero has one digit")
    assertEquals(Arithmetic.digits(7).toScalaList, List(7))
    assertEquals(Arithmetic.digits(100).toScalaList, List(1, 0, 0), "trailing zeros are digits")

    // The boundary of the input type, pinned literally. |Int.MinValue| is 2^31
    // and the largest Int is 2^31 - 1, so an abs taken at 32 bits returns its
    // argument unchanged and the loop exits before its first iteration. The
    // failure is an empty list: no exception, no warning, and a reassembly of 0.
    assertEquals(
      Arithmetic.digits(Int.MinValue).toScalaList,
      List(2, 1, 4, 7, 4, 8, 3, 6, 4, 8),
      "the abs must be taken after the widening, not before"
    )

    // The law that pins the order: reassembling the digits returns the
    // magnitude of the input. digits preserves |n|, not n — the sign is
    // discarded by contract, so the law is stated over the absolute value.
    //
    // math.abs(n.toLong), never math.abs(n).toLong: abs and toLong do not
    // commute, and the wrong order reproduces here, in the test, the exact
    // 32-bit defect the test exists to catch.
    List(0, 7, 42, -42, 1024, -1024, 999_999, Int.MaxValue, Int.MinValue).foreach { n =>
      val back = Arithmetic.digits(n).foldLeft(0L)((acc, d) => acc * 10 + d)
      assertEquals(back, math.abs(n.toLong), s"digits($n) does not reassemble to |$n|")
    }
  }

  test("collatzLength counts the steps to 1") {
    assertEquals(Arithmetic.collatzLength(1L), 0, "1 is already there")
    assertEquals(Arithmetic.collatzLength(2L), 1)
    assertEquals(Arithmetic.collatzLength(6L), 8, "6 -> 3 -> 10 -> 5 -> 16 -> 8 -> 4 -> 2 -> 1")
    assertEquals(Arithmetic.collatzLength(27L), 111, "the famous long one under 100")

    // Long trajectories are where a non-tail version dies. 27 reaches 9,232 on
    // the way, and 63,728,127 takes 949 steps.
    assert(survives(Arithmetic.collatzLength(63_728_127L)), "a 949-step trajectory")
    assertEquals(Arithmetic.collatzLength(63_728_127L), 949)
  }

end Exercise3ArithmeticSpec
