package cs.se.annex.a1

/** Exercise 2 (Easy) — TwosComplement: the master identity and branchless sign
  * handling.
  */
class Exercise2TwosComplementSpec extends AnnexA1Harness:

  test("negate agrees with unary minus everywhere, Int.MinValue included") {
    domain(1000).foreach { x =>
      assertEquals(TwosComplement.negate(x), -x, s"negate(${bin(x)}) is wrong")
    }
    // The asymmetry of the signed range is not an edge case, it is the ring.
    assertEquals(TwosComplement.negate(Int.MinValue), Int.MinValue)
    assertEquals(TwosComplement.negate(0), 0)
  }

  test("the master identity: x + ~x == -1, and ~x == -x - 1") {
    domain(1000).foreach { x =>
      assertEquals(x + ~x, -1, s"${bin(x)} + its complement is not all ones")
      assertEquals(~x, -x - 1, s"complement identity failed for ${bin(x)}")
      assertEquals(~(~x), x, "complement is not an involution")
    }
  }

  test("negate is an involution and a group inverse") {
    domain(1000).foreach { x =>
      assertEquals(TwosComplement.negate(TwosComplement.negate(x)), x)
      assertEquals(x + TwosComplement.negate(x), 0, s"${bin(x)} is not its own additive inverse")
    }
  }

  test("signMask is 0 for non-negative and -1 for negative") {
    domain(1000).foreach { x =>
      val expected = if x < 0 then -1 else 0
      assertEquals(TwosComplement.signMask(x), expected, s"signMask(${bin(x)}) is wrong")
    }
    assertEquals(TwosComplement.signMask(0), 0)
    assertEquals(TwosComplement.signMask(Int.MinValue), -1)
    assertEquals(TwosComplement.signMask(Int.MaxValue), 0)
  }

  test("absBranchless agrees with Math.abs, including its documented lie") {
    domain(1000).foreach { x =>
      assertEquals(TwosComplement.absBranchless(x), Math.abs(x), s"abs(${bin(x)}) is wrong")
    }
    // Math.abs is not total on Int. Neither is yours, and it must fail the same
    // way rather than inventing a different answer.
    assertEquals(TwosComplement.absBranchless(Int.MinValue), Int.MinValue)
    assert(TwosComplement.absBranchless(Int.MinValue) < 0, "the asymmetry is real")
  }

  test("abs is non-negative on every input except the one that cannot be") {
    domain(1000).filter(_ != Int.MinValue).foreach { x =>
      assert(TwosComplement.absBranchless(x) >= 0, s"abs(${bin(x)}) went negative")
    }
  }

  test("sameSign treats zero as non-negative and never multiplies") {
    val samples = domain(300)
    samples.foreach { x =>
      samples.foreach { y =>
        val expected = (x < 0) == (y < 0)
        assertEquals(TwosComplement.sameSign(x, y), expected, s"sameSign($x, $y) is wrong")
      }
    }
    assert(TwosComplement.sameSign(0, 5), "zero must count as non-negative")
    assert(!TwosComplement.sameSign(0, -5))
    // The naive `x * y > 0` version overflows here and reports the wrong answer.
    assert(TwosComplement.sameSign(Int.MaxValue, 2), "overflow-based sign tests fail here")
    // Both are negative, so they share a sign — but their product is
    // Int.MinValue, which is negative, so `x * y > 0` reports the opposite.
    assert(TwosComplement.sameSign(Int.MinValue, -1), "product-based sign tests fail here")
  }

  test("floorDiv2 floors, and diverges from / on every negative odd input") {
    domain(1000).foreach { x =>
      assertEquals(
        TwosComplement.floorDiv2(x),
        Math.floorDiv(x, 2),
        s"floorDiv2(${bin(x)}) does not floor"
      )
    }
    assertEquals(TwosComplement.floorDiv2(-7), -4)
    assertEquals(TwosComplement.floorDiv2(7), 3)
    assertEquals(TwosComplement.floorDiv2(-8), -4)
    assertEquals(TwosComplement.floorDiv2(0), 0)
    assertEquals(TwosComplement.floorDiv2(-1), -1, "floor(-0.5) is -1, not 0")

    // The disagreement is the point of the exercise: it must be there.
    val divergent = domain(1000).filter(x => TwosComplement.floorDiv2(x) != x / 2)
    assert(divergent.nonEmpty, "floorDiv2 that never disagrees with / is truncating")
    divergent.foreach { x =>
      assert(x < 0 && (x % 2 != 0), s"$x diverges but is not negative-odd — that is a bug")
    }
  }

  test("the bias correction the compiler emits for x / 2 is reproducible") {
    domain(1000).foreach { x =>
      assertEquals((x + (x >>> 31)) >> 1, x / 2, s"bias-corrected shift failed for ${bin(x)}")
    }
  }

end Exercise2TwosComplementSpec
