package cs.se.annex.a1

/** Exercise 7 (Medium) — Packing: several fields in one word. */
class Exercise7PackingSpec extends AnnexA1Harness:

  test("packInts round-trips every pair, negatives included") {
    val his = domain(400)
    val los = domain(400, Seed + 1)
    (his.zip(los) ++ Corners.flatMap(a => Corners.map(b => (a, b)))).foreach { (hi, lo) =>
      val packed = Packing.packInts(hi, lo)
      assertEquals(Packing.unpackHi(packed), hi, s"high half lost for ($hi, $lo): ${bin64(packed)}")
      assertEquals(Packing.unpackLo(packed), lo, s"low half lost for ($hi, $lo): ${bin64(packed)}")
    }
  }

  test("the sign-extension trap: a negative low half must not corrupt the high half") {
    // Without `& 0xffffffffL` the widening of `lo` fills all thirty-two high
    // bits with ones, and `hi` is destroyed. These four lines are the exercise.
    assertEquals(Packing.unpackHi(Packing.packInts(0, -1)), 0, "a negative low half leaked upward")
    assertEquals(Packing.unpackHi(Packing.packInts(7, -1)), 7)
    assertEquals(Packing.unpackHi(Packing.packInts(1, Int.MinValue)), 1)
    assertEquals(Packing.packInts(0, -1), 0xffffffffL, "the packed word itself is wrong")
  }

  test("the packed layout is exactly hi in the upper half, lo in the lower") {
    assertEquals(Packing.packInts(0, 0), 0L)
    assertEquals(Packing.packInts(1, 0), 1L << 32)
    assertEquals(Packing.packInts(0, 1), 1L)
    assertEquals(Packing.packInts(-1, -1), -1L)
    assertEquals(Packing.packInts(-1, 0), 0xffffffff00000000L)
  }

  test("packing is injective — distinct pairs never collide") {
    val samples = domain(150)
    val packed = samples.flatMap(hi => samples.map(lo => Packing.packInts(hi, lo)))
    assertEquals(packed.distinct.size, packed.size, "two distinct pairs packed to the same word")
  }

  test("unpack is a left inverse in the other direction too") {
    (List(0L, -1L, Long.MinValue, Long.MaxValue) ++ randomLongs(500)).foreach { w =>
      val rebuilt = Packing.packInts(Packing.unpackHi(w), Packing.unpackLo(w))
      assertEquals(rebuilt, w, s"unpack-then-pack lost information for ${bin64(w)}")
    }
  }

  test("packRgba refuses any channel outside [0, 255]") {
    List(-1, 256, 1000, Int.MinValue, Int.MaxValue).foreach { bad =>
      assertEquals(Packing.packRgba(bad, 0, 0, 0), None, s"red = $bad must be rejected")
      assertEquals(Packing.packRgba(0, bad, 0, 0), None, s"green = $bad must be rejected")
      assertEquals(Packing.packRgba(0, 0, bad, 0), None, s"blue = $bad must be rejected")
      assertEquals(Packing.packRgba(0, 0, 0, bad), None, s"alpha = $bad must be rejected")
    }
  }

  test("packRgba round-trips every legal channel combination") {
    val channels = List(0, 1, 2, 127, 128, 200, 254, 255)
    channels.foreach { r =>
      channels.foreach { g =>
        channels.foreach { b =>
          channels.foreach { a =>
            Packing.packRgba(r, g, b, a) match
              case Some(px) =>
                assertEquals(Packing.red(px), r, s"red lost in ($r,$g,$b,$a): ${bin(px)}")
                assertEquals(Packing.green(px), g, s"green lost in ($r,$g,$b,$a)")
                assertEquals(Packing.blue(px), b, s"blue lost in ($r,$g,$b,$a)")
                assertEquals(Packing.alpha(px), a, s"alpha lost in ($r,$g,$b,$a)")
              case None => fail(s"($r,$g,$b,$a) is legal and must pack")
          }
        }
      }
    }
  }

  test("an opaque pixel is a negative Int, and alpha must still be non-negative") {
    // Alpha occupies the top byte, so full opacity sets the sign bit. An
    // arithmetic shift returns -1 here; a logical shift returns 255.
    val opaqueWhite = Packing.packRgba(255, 255, 255, 255)
    assertEquals(opaqueWhite, Some(-1), "0xFFFFFFFF is -1")
    opaqueWhite.foreach { px =>
      assert(px < 0, "the test premise: an opaque pixel is negative")
      assertEquals(Packing.alpha(px), 255, "alpha came back signed — use a logical shift")
      assertEquals(Packing.red(px), 255)
    }
    Packing.packRgba(0, 0, 0, 255).foreach { px =>
      assertEquals(Packing.alpha(px), 255, "opaque black lost its alpha")
      assertEquals(Packing.red(px), 0)
      assertEquals(px, 0xff000000, "layout must be 0xAARRGGBB")
    }
  }

  test("every channel accessor lands in [0, 255] for every possible Int") {
    domain(2000).foreach { px =>
      List(
        "red" -> Packing.red(px),
        "green" -> Packing.green(px),
        "blue" -> Packing.blue(px),
        "alpha" -> Packing.alpha(px)
      ).foreach { (name, v) =>
        assert(v >= 0 && v <= 255, s"$name of ${bin(px)} = $v escaped [0, 255]")
      }
    }
  }

  test("the four channels reconstruct the pixel exactly") {
    domain(1000).foreach { px =>
      val rebuilt =
        Packing.packRgba(Packing.red(px), Packing.green(px), Packing.blue(px), Packing.alpha(px))
      assertEquals(rebuilt, Some(px), s"decomposition of ${bin(px)} did not reassemble")
    }
  }

end Exercise7PackingSpec
