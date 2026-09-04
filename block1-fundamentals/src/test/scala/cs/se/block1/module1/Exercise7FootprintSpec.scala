package cs.se.block1.module1

/** Exercise 7 (Hard) — Footprint arithmetic: deriving the HotSpot object layout
  * on paper, then asserting the derivation against known-good sizes.
  */
class Exercise7FootprintSpec extends Module1Harness:

  test("align rounds up to the next multiple of 8 and is idempotent") {
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

  test("shallowSize reproduces the real HotSpot layout") {
    assertEquals(Footprint.shallowSize(0, 0, 0, 0, 0), 16, "bare object: 12-byte header padded")
    assertEquals(Footprint.shallowSize(0, 0, 0, 2, 0), 32, "Vec2(x: Double, y: Double)")
    assertEquals(Footprint.shallowSize(2, 0, 0, 0, 0), 24, "cons cell: head + tail references")
    assertEquals(Footprint.shallowSize(0, 1, 0, 0, 0), 16, "boxed java.lang.Integer")
    assertEquals(Footprint.shallowSize(0, 0, 1, 0, 0), 24, "boxed java.lang.Long")
    assertEquals(Footprint.shallowSize(0, 0, 0, 0, 1), 16, "one boolean field")
    assertEquals(Footprint.shallowSize(1, 1, 1, 1, 1), 40, "12 + 4 + 4 + 8 + 8 + 1 = 37 -> 40")
  }

  test("the List[Int] tax over Array[Int] is exactly tenfold") {
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

end Exercise7FootprintSpec
