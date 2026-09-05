package cs.se.annex.a1

/** Exercise 1 (Easy) — Bits: addressing individual bits. */
class Exercise1BitsSpec extends AnnexA1Harness:

  private val indices = 0 to 31

  test("testBit agrees with the JDK oracle on every value and every index") {
    domain(200).foreach { x =>
      indices.foreach { i =>
        val expected = ((x >>> i) & 1) == 1
        assertEquals(
          Bits.testBit(x, i),
          expected,
          s"testBit(${bin(x)}, $i) disagrees with the shift-and-mask oracle"
        )
      }
    }
  }

  test("bit 31 is the sign bit") {
    domain(200).foreach { x =>
      assertEquals(Bits.testBit(x, 31), x < 0, s"bit 31 of ${bin(x)} must be the sign")
    }
  }

  test("setBit and clearBit change exactly one bit and are idempotent") {
    domain(100).foreach { x =>
      indices.foreach { i =>
        val set = Bits.setBit(x, i)
        val cleared = Bits.clearBit(x, i)

        assert(Bits.testBit(set, i), s"setBit(${bin(x)}, $i) left the bit clear")
        assert(!Bits.testBit(cleared, i), s"clearBit(${bin(x)}, $i) left the bit set")

        assertEquals(Bits.setBit(set, i), set, "setBit is not idempotent")
        assertEquals(Bits.clearBit(cleared, i), cleared, "clearBit is not idempotent")

        // Nothing else may move: mask out the target bit and compare.
        val mask = ~(1 << i)
        assertEquals(set & mask, x & mask, s"setBit(${bin(x)}, $i) disturbed another bit")
        assertEquals(cleared & mask, x & mask, s"clearBit(${bin(x)}, $i) disturbed another bit")
      }
    }
  }

  test("toggleBit is an involution and equals xor with a singleton") {
    domain(100).foreach { x =>
      indices.foreach { i =>
        val once = Bits.toggleBit(x, i)
        assertEquals(Bits.toggleBit(once, i), x, s"toggleBit is not an involution at $i")
        assertEquals(once, x ^ (1 << i), s"toggleBit(${bin(x)}, $i) is not xor with 1 << $i")
        assertNotEquals(once, x, "toggling must change the value")
      }
    }
  }

  test("set / clear / test form a coherent algebra") {
    domain(100).foreach { x =>
      indices.foreach { i =>
        assertEquals(Bits.clearBit(Bits.setBit(x, i), i), Bits.clearBit(x, i))
        assertEquals(Bits.setBit(Bits.clearBit(x, i), i), Bits.setBit(x, i))
        // Setting a bit that is already set, or clearing one already clear, is
        // the identity on the whole word.
        if Bits.testBit(x, i) then assertEquals(Bits.setBit(x, i), x)
        else assertEquals(Bits.clearBit(x, i), x)
      }
    }
  }

  test("toBinaryString is exactly 32 zero-padded characters, MSB first") {
    domain(500).foreach { x =>
      val s = Bits.toBinaryString(x)
      assertEquals(s.length, 32, s"rendering of $x has the wrong width: '$s'")
      assert(s.forall(c => c == '0' || c == '1'), s"rendering of $x is not binary: '$s'")
      assertEquals(s, bin(x), s"rendering of $x disagrees with the JDK oracle")
    }
  }

  test("toBinaryString round-trips through parsing") {
    domain(300).foreach { x =>
      val parsed = java.lang.Long.parseLong(Bits.toBinaryString(x), 2).toInt
      assertEquals(parsed, x, s"$x did not survive render-then-parse")
    }
    assertEquals(Bits.toBinaryString(0), "0" * 32)
    assertEquals(Bits.toBinaryString(-1), "1" * 32)
    assertEquals(Bits.toBinaryString(Int.MinValue), "1" + ("0" * 31))
    assertEquals(Bits.toBinaryString(Int.MaxValue), "0" + ("1" * 31))
  }

end Exercise1BitsSpec
