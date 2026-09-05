package cs.se.annex.a1

/** Exercise 5 (Medium) — BitAdder: arithmetic reconstructed from Boolean
  * algebra.
  */
class Exercise5BitAdderSpec extends AnnexA1Harness:

  private def pairs(n: Int): List[(Int, Int)] =
    val xs = domain(n)
    val ys = domain(n, Seed + 1)
    xs.zip(ys) ++ Corners.flatMap(a => Corners.map(b => (a, b)))

  test("add agrees with + on every pair, overflow included") {
    pairs(400).foreach { (a, b) =>
      assertEquals(BitAdder.add(a, b), a + b, s"add(${bin(a)}, ${bin(b)}) is wrong")
    }
  }

  test("add wraps rather than saturating — the ring is Z/2^32 Z") {
    assertEquals(BitAdder.add(Int.MaxValue, 1), Int.MinValue, "overflow must wrap")
    assertEquals(BitAdder.add(Int.MinValue, -1), Int.MaxValue, "underflow must wrap")
    assertEquals(BitAdder.add(Int.MaxValue, Int.MaxValue), -2)
    assertEquals(BitAdder.add(Int.MinValue, Int.MinValue), 0)
  }

  test("(Int, add, 0) is a commutative monoid") {
    domain(500).foreach { a =>
      assertEquals(BitAdder.add(a, 0), a, "right identity")
      assertEquals(BitAdder.add(0, a), a, "left identity")
    }
    pairs(300).foreach { (a, b) =>
      assertEquals(BitAdder.add(a, b), BitAdder.add(b, a), s"commutativity failed on $a, $b")
    }
    val xs = domain(200)
    xs.sliding(3).foreach {
      case a :: b :: c :: Nil =>
        assertEquals(
          BitAdder.add(BitAdder.add(a, b), c),
          BitAdder.add(a, BitAdder.add(b, c)),
          s"associativity failed on $a, $b, $c"
        )
      case _ => ()
    }
  }

  test("every element has an additive inverse, so it is a group") {
    domain(500).foreach { a =>
      assertEquals(BitAdder.negate(a), -a, s"negate(${bin(a)}) is wrong")
      assertEquals(BitAdder.add(a, BitAdder.negate(a)), 0, s"$a is not cancelled by its negation")
    }
    assertEquals(BitAdder.negate(Int.MinValue), Int.MinValue)
  }

  test("subtract agrees with -") {
    pairs(400).foreach { (a, b) =>
      assertEquals(BitAdder.subtract(a, b), a - b, s"subtract(${bin(a)}, ${bin(b)}) is wrong")
    }
    assertEquals(BitAdder.subtract(0, Int.MinValue), Int.MinValue)
  }

  test("multiply agrees with * on every pair, including negatives and overflow") {
    pairs(400).foreach { (a, b) =>
      assertEquals(BitAdder.multiply(a, b), a * b, s"multiply(${bin(a)}, ${bin(b)}) is wrong")
    }
  }

  test("multiply: the cases a sign-propagating shift gets wrong") {
    assertEquals(BitAdder.multiply(-1, -1), 1, "the classic failure of an arithmetic shift on b")
    assertEquals(BitAdder.multiply(-1, 5), -5)
    assertEquals(BitAdder.multiply(5, -1), -5)
    assertEquals(BitAdder.multiply(Int.MinValue, -1), Int.MinValue, "wraps, does not throw")
    assertEquals(BitAdder.multiply(0, Int.MinValue), 0)
    assertEquals(BitAdder.multiply(65536, 65536), 0, "2^16 * 2^16 overflows to zero")
  }

  test("(Int, add, multiply) is a ring: distributivity holds") {
    val xs = domain(150)
    xs.sliding(3).foreach {
      case a :: b :: c :: Nil =>
        assertEquals(
          BitAdder.multiply(a, BitAdder.add(b, c)),
          BitAdder.add(BitAdder.multiply(a, b), BitAdder.multiply(a, c)),
          s"a(b + c) != ab + ac for $a, $b, $c"
        )
      case _ => ()
    }
    domain(300).foreach { a =>
      assertEquals(BitAdder.multiply(a, 1), a, "1 is not the multiplicative identity")
      assertEquals(BitAdder.multiply(a, 0), 0, "0 is not the annihilator")
    }
  }

  test("multiplication by a power of two is a left shift") {
    (0 to 30).foreach { k =>
      domain(100).foreach { a =>
        assertEquals(
          BitAdder.multiply(a, 1 << k),
          a << k,
          s"${bin(a)} * 2^$k is not a shift"
        )
      }
    }
  }

end Exercise5BitAdderSpec
