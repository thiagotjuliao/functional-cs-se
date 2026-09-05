package cs.se.annex.a1

/** Exercise 9 (Hard) — VarIntCodec: zig-zag plus LEB128, the encoding used by
  * Protocol Buffers.
  */
class Exercise9VarIntCodecSpec extends AnnexA1Harness:

  private def bytesOf(n: Int): List[Int] =
    VarIntCodec.encode(n).toList.map(_ & 0xff)

  test("zigZag is a bijection on the whole of Int") {
    domain(2000).foreach { n =>
      assertEquals(
        VarIntCodec.zigZagDecode(VarIntCodec.zigZagEncode(n)),
        n,
        s"zig-zag did not round-trip ${bin(n)}"
      )
    }
    val encoded = domain(1000).map(VarIntCodec.zigZagEncode)
    assertEquals(encoded.distinct.size, encoded.size, "zig-zag is not injective")
  }

  test("zigZag interleaves signed values onto the naturals") {
    assertEquals(VarIntCodec.zigZagEncode(0), 0)
    assertEquals(VarIntCodec.zigZagEncode(-1), 1)
    assertEquals(VarIntCodec.zigZagEncode(1), 2)
    assertEquals(VarIntCodec.zigZagEncode(-2), 3)
    assertEquals(VarIntCodec.zigZagEncode(2), 4)
    assertEquals(
      VarIntCodec.zigZagEncode(Int.MaxValue),
      -2,
      "wraps; the bit pattern is what matters"
    )
    assertEquals(VarIntCodec.zigZagEncode(Int.MinValue), -1)
    List(0, -1, 1, -2, 2, Int.MaxValue, Int.MinValue).foreach { n =>
      assertEquals(VarIntCodec.zigZagDecode(VarIntCodec.zigZagEncode(n)), n)
    }
  }

  test("zigZag keeps small magnitudes small — the entire reason it exists") {
    (-100 to 100).foreach { n =>
      val z = VarIntCodec.zigZagEncode(n)
      assert(z >= 0, s"zig-zag of the small value $n produced the negative $z")
      assert(z <= 200, s"zig-zag of $n produced $z, which is not small")
    }
  }

  test("encode produces between one and five bytes, and no non-canonical tail") {
    domain(2000).foreach { n =>
      val bytes = bytesOf(n)
      assert(bytes.nonEmpty, s"encoding of $n is empty")
      assert(bytes.length <= 5, s"encoding of $n took ${bytes.length} bytes")
      bytes.init.foreach { b =>
        assert((b & 0x80) != 0, s"a non-final byte of $n is missing its continuation flag")
      }
      assertEquals(bytes.last & 0x80, 0, s"the final byte of $n still sets the continuation flag")
      if bytes.length > 1 then
        assertNotEquals(bytes.last, 0, s"encoding of $n has a redundant trailing zero byte")
    }
  }

  test("small values cost one byte — the whole motivation for the format") {
    (-64 to 63).foreach { n =>
      assertEquals(bytesOf(n).length, 1, s"$n should fit in a single byte after zig-zag")
    }
    assertEquals(bytesOf(0), List(0))
    assertEquals(bytesOf(-1), List(1))
    assertEquals(bytesOf(1), List(2))
    // 64 zig-zags to 128, which needs the second septet.
    assertEquals(bytesOf(64).length, 2)
    assertEquals(bytesOf(64), List(0x80, 0x01))
  }

  test("encodedSize predicts the length without encoding") {
    domain(2000).foreach { n =>
      assertEquals(
        VarIntCodec.encodedSize(n),
        bytesOf(n).length,
        s"encodedSize($n) disagrees with the encoder"
      )
    }
  }

  test("decode inverts encode over the whole Int range") {
    domain(3000).foreach { n =>
      assertEquals(VarIntCodec.decode(VarIntCodec.encode(n)), Some(n), s"$n did not round-trip")
    }
    List(Int.MinValue, Int.MaxValue, 0, -1, 1).foreach { n =>
      assertEquals(VarIntCodec.decode(VarIntCodec.encode(n)), Some(n), s"boundary $n failed")
    }
  }

  test("the sign-extension trap: bytes above 0x7f must be masked when read") {
    // Every payload byte with its continuation flag set is >= 0x80, i.e. a
    // negative Byte. Reading it without `& 0xff` poisons the accumulator, and
    // these are the smallest values that expose it.
    List(64, 128, 8192, 1_000_000, Int.MaxValue, Int.MinValue).foreach { n =>
      val encoded = VarIntCodec.encode(n)
      assert(
        encoded.toList.exists(b => b < 0),
        s"test premise: the encoding of $n should contain a negative Byte"
      )
      assertEquals(VarIntCodec.decode(encoded), Some(n), s"$n was corrupted by sign extension")
    }
  }

  test("decodeAt reports where the varint ended, so a stream can be folded") {
    val values = List(0, -1, 1, 300, -70000, Int.MaxValue, Int.MinValue, 7)
    val buffer = IArray.from(values.flatMap(v => VarIntCodec.encode(v).toList))

    val decoded = values.foldLeft(Option((List.empty[Int], 0))) { (acc, _) =>
      acc.flatMap { (seen, offset) =>
        VarIntCodec.decodeAt(buffer, offset).map((v, next) => (v :: seen, next))
      }
    }

    decoded match
      case Some((seen, offset)) =>
        assertEquals(seen.reverse, values, "the concatenated stream did not decode back")
        assertEquals(offset, buffer.length, "the decoder did not consume the whole buffer")
      case None => fail("decoding a concatenation of well-formed varints failed")
  }

  test("decodeAt advances by exactly encodedSize") {
    domain(500).foreach { n =>
      val encoded = VarIntCodec.encode(n)
      assertEquals(
        VarIntCodec.decodeAt(encoded, 0),
        Some((n, VarIntCodec.encodedSize(n))),
        s"decodeAt disagrees with encodedSize for $n"
      )
    }
  }

  test("a truncated or over-long input is rejected, never guessed at") {
    val fiveByte = VarIntCodec.encode(Int.MinValue)
    assert(fiveByte.length >= 2, "test premise: this value needs several bytes")

    // Every proper prefix is truncated: the last available byte still asks for
    // a continuation that is not there.
    (1 until fiveByte.length).foreach { cut =>
      val truncated = IArray.from(fiveByte.toList.take(cut))
      assertEquals(VarIntCodec.decode(truncated), None, s"a $cut-byte prefix must be rejected")
    }

    assertEquals(VarIntCodec.decode(IArray.empty[Byte]), None, "an empty buffer decodes to nothing")
    assertEquals(VarIntCodec.decodeAt(IArray.empty[Byte], 0), None)

    // Six continuation bytes cannot describe a 32-bit value.
    val overlong = IArray.from(List.fill(6)(0x80.toByte) ++ List(0x01.toByte))
    assertEquals(VarIntCodec.decode(overlong), None, "an over-long varint must be rejected")
  }

  test("decode rejects trailing bytes rather than ignoring them") {
    val withTail = IArray.from(VarIntCodec.encode(42).toList ++ List(0x00.toByte))
    assertEquals(VarIntCodec.decode(withTail), None, "trailing input must not be silently dropped")
    // decodeAt, by contrast, is allowed to stop early — that is its contract.
    assertEquals(VarIntCodec.decodeAt(withTail, 0).map(_._1), Some(42))
  }

  test("the encoding never expands a value beyond its fixed-width cost by much") {
    // The honest statement of the format's trade-off: values needing all 32
    // bits cost five bytes, one more than a fixed encoding. Everything smaller
    // wins. Record the crossover.
    val small = (-1000 to 1000).map(VarIntCodec.encodedSize).sum
    val fixed = 2001 * 4
    assert(
      small < fixed,
      s"varint cost $small bytes vs $fixed fixed — the format is not paying off"
    )
    assertEquals(VarIntCodec.encodedSize(Int.MinValue), 5)
    assertEquals(VarIntCodec.encodedSize(Int.MaxValue), 5)
    report("varint bytes for [-1000, 1000]", s"$small vs $fixed fixed-width")
  }

end Exercise9VarIntCodecSpec
