package cs.se.annex.a1

/** Exercise 3 (Easy) — PowersOfTwo: the arithmetic behind hash tables and tries. */
class Exercise3PowersOfTwoSpec extends AnnexA1Harness:

  private val positivePowers: List[Int] = (0 to 30).map(1 << _).toList

  test("isPowerOfTwo accepts exactly the 31 positive powers") {
    positivePowers.foreach { p =>
      assert(
        PowersOfTwo.isPowerOfTwo(p),
        s"$p = 2^${31 - Integer.numberOfLeadingZeros(p)} rejected"
      )
    }
    assert(!PowersOfTwo.isPowerOfTwo(0), "zero is not a power of two")
    assert(!PowersOfTwo.isPowerOfTwo(3))
    assert(!PowersOfTwo.isPowerOfTwo(6))
    assert(!PowersOfTwo.isPowerOfTwo(Int.MaxValue))
  }

  test("isPowerOfTwo rejects every negative, Int.MinValue included") {
    // Int.MinValue has exactly one set bit, so the naive `x & (x - 1) == 0`
    // test accepts it. -2^31 is not 2^k for any non-negative k.
    assert(!PowersOfTwo.isPowerOfTwo(Int.MinValue), "Int.MinValue is the classic false positive")
    domain(500).filter(_ < 0).foreach { x =>
      assert(!PowersOfTwo.isPowerOfTwo(x), s"$x is negative and cannot be a power of two")
    }
  }

  test("isPowerOfTwo agrees with a popcount oracle on the positives") {
    domain(1000).filter(_ > 0).foreach { x =>
      assertEquals(
        PowersOfTwo.isPowerOfTwo(x),
        Integer.bitCount(x) == 1,
        s"disagreement with popcount on ${bin(x)}"
      )
    }
  }

  test("modPowerOfTwo refuses a non-power-of-two modulus") {
    List(0, 3, 6, 100, -4, Int.MinValue, Int.MaxValue).foreach { n =>
      assertEquals(
        PowersOfTwo.modPowerOfTwo(10, n),
        None,
        s"modulus $n is not a power of two and must be rejected"
      )
    }
  }

  test("modPowerOfTwo agrees with % for non-negative dividends") {
    positivePowers.foreach { n =>
      (List(0, 1, 2, 7, 63, 1000, 65535, Int.MaxValue) ++ randomInts(200).map(x =>
        x & Int.MaxValue
      ))
        .foreach { x =>
          assertEquals(
            PowersOfTwo.modPowerOfTwo(x, n),
            Some(x % n),
            s"$x mod $n is wrong"
          )
        }
    }
  }

  test("modPowerOfTwo lands in [0, n) for every dividend, including negatives") {
    positivePowers.foreach { n =>
      domain(300).foreach { x =>
        PowersOfTwo.modPowerOfTwo(x, n) match
          case Some(r) => assert(r >= 0 && r < n, s"$x mod $n = $r escaped [0, $n)")
          case None => fail(s"$n is a power of two and must be accepted")
      }
    }
  }

  test("nextPowerOfTwo rounds up, is idempotent on powers, and bottoms out at 1") {
    List(Int.MinValue, -100, -1, 0, 1).foreach { x =>
      assertEquals(PowersOfTwo.nextPowerOfTwo(x), Some(1), s"nextPowerOfTwo($x) must be 1")
    }
    positivePowers.foreach { p =>
      assertEquals(PowersOfTwo.nextPowerOfTwo(p), Some(p), s"$p is already a power of two")
    }
    assertEquals(PowersOfTwo.nextPowerOfTwo(3), Some(4))
    assertEquals(PowersOfTwo.nextPowerOfTwo(5), Some(8))
    assertEquals(PowersOfTwo.nextPowerOfTwo(1000), Some(1024))
    assertEquals(PowersOfTwo.nextPowerOfTwo(1 << 30), Some(1 << 30))
  }

  test("nextPowerOfTwo refuses what it cannot represent") {
    List((1 << 30) + 1, Int.MaxValue - 1, Int.MaxValue).foreach { x =>
      assertEquals(PowersOfTwo.nextPowerOfTwo(x), None, s"$x rounds up past Int range")
    }
  }

  test("nextPowerOfTwo is the least upper bound, verified against the oracle") {
    domain(500).filter(x => x > 0 && x <= (1 << 30)).foreach { x =>
      PowersOfTwo.nextPowerOfTwo(x) match
        case Some(p) =>
          assert(p >= x, s"nextPowerOfTwo($x) = $p is below its argument")
          assert(PowersOfTwo.isPowerOfTwo(p), s"nextPowerOfTwo($x) = $p is not a power of two")
          assert(p / 2 < x, s"nextPowerOfTwo($x) = $p is not the *least* upper bound")
        case None => fail(s"$x is representable and must round up")
    }
  }

  test("log2Floor is undefined at and below zero") {
    List(0, -1, -1000, Int.MinValue).foreach { x =>
      assertEquals(PowersOfTwo.log2Floor(x), None, s"log2 of $x is not defined")
    }
  }

  test("log2Floor agrees with the leading-zeros oracle on every positive") {
    domain(1000).filter(_ > 0).foreach { x =>
      val expected = 31 - Integer.numberOfLeadingZeros(x)
      assertEquals(PowersOfTwo.log2Floor(x), Some(expected), s"log2Floor(${bin(x)}) is wrong")
    }
  }

  test("log2Floor satisfies its defining inequality: 2^k <= x < 2^(k+1)") {
    domain(500).filter(_ > 0).foreach { x =>
      PowersOfTwo.log2Floor(x) match
        case Some(k) =>
          assert(k >= 0 && k <= 30 || (k == 31 && x < 0), s"log2Floor($x) = $k is out of range")
          assert((1 << k) <= x, s"2^$k > $x")
          assert(k == 30 || x < (1 << (k + 1)) || (1 << (k + 1)) < 0, s"$x >= 2^${k + 1}")
        case None => fail(s"$x is positive and must have a logarithm")
    }
  }

end Exercise3PowersOfTwoSpec
