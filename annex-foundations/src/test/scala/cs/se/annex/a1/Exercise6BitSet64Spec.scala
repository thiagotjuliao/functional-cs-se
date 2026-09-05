package cs.se.annex.a1

/** Exercise 6 (Medium) — BitSet64: a Boolean algebra in one machine word. */
class Exercise6BitSet64Spec extends AnnexA1Harness:

  private val members = 0 to 63

  private def sets(n: Int, seed: Long = Seed): List[BitSet64] =
    (List(0L, -1L, 1L, Long.MinValue, Long.MaxValue) ++ randomLongs(n, seed))
      .map(BitSet64.fromRaw)

  test("Empty and Full are what they claim to be") {
    assertEquals(BitSet64.Empty.raw, 0L)
    assertEquals(BitSet64.Full.raw, -1L)
    assertEquals(BitSet64.Empty.size, 0)
    assertEquals(BitSet64.Full.size, 64)
    members.foreach { i =>
      assert(!BitSet64.Empty.contains(i), s"the empty set contains $i")
      assert(BitSet64.Full.contains(i), s"the universe is missing $i")
    }
  }

  test("of builds exactly the set it is given") {
    val s = BitSet64.of(0, 3, 63)
    assertEquals(s.size, 3)
    assertEquals(s.toList, List(0, 3, 63))
    members.foreach { i =>
      assertEquals(s.contains(i), List(0, 3, 63).contains(i), s"membership of $i is wrong")
    }
    assertEquals(BitSet64.of().raw, BitSet64.Empty.raw, "the empty varargs case")
    assertEquals(BitSet64.of(5, 5, 5).toList, List(5), "of must be idempotent in its arguments")
    assertEquals(BitSet64.of(members*).raw, BitSet64.Full.raw, "all sixty-four members")
  }

  test("incl and excl are idempotent and mutually inverse") {
    sets(100).foreach { s =>
      members.foreach { i =>
        val added = s.incl(i)
        val removed = s.excl(i)
        assert(added.contains(i), s"incl($i) did not add")
        assert(!removed.contains(i), s"excl($i) did not remove")
        assertEquals(added.incl(i).raw, added.raw, "incl is not idempotent")
        assertEquals(removed.excl(i).raw, removed.raw, "excl is not idempotent")
        // Nothing else may move.
        members.filter(_ != i).foreach { j =>
          assertEquals(added.contains(j), s.contains(j), s"incl($i) disturbed $j")
          assertEquals(removed.contains(j), s.contains(j), s"excl($i) disturbed $j")
        }
      }
    }
  }

  test("bit 63 is reachable — the shift must be a Long shift") {
    // `1 << 63` on an Int is zero after masking; `1L << 63` is Long.MinValue.
    // This test exists solely to catch that confusion.
    val s = BitSet64.Empty.incl(63)
    assert(s.contains(63), "member 63 was lost: check that you shifted a Long, not an Int")
    assertEquals(s.size, 1)
    assertEquals(s.raw, Long.MinValue, s"expected the sign bit, got ${bin64(s.raw)}")
    assertEquals(s.toList, List(63))
  }

  test("union is a commutative, idempotent monoid with identity Empty") {
    val xs = sets(120)
    xs.foreach { s =>
      assertEquals((s union BitSet64.Empty).raw, s.raw, "right identity")
      assertEquals((BitSet64.Empty union s).raw, s.raw, "left identity")
      assertEquals((s union s).raw, s.raw, "idempotence")
      assertEquals((s union BitSet64.Full).raw, BitSet64.Full.raw, "Full absorbs")
    }
    xs.sliding(3).foreach {
      case a :: b :: c :: Nil =>
        assertEquals(((a union b) union c).raw, (a union (b union c)).raw, "associativity")
        assertEquals((a union b).raw, (b union a).raw, "commutativity")
      case _ => ()
    }
  }

  test("intersect is a commutative, idempotent monoid with identity Full") {
    val xs = sets(120)
    xs.foreach { s =>
      assertEquals((s intersect BitSet64.Full).raw, s.raw, "right identity")
      assertEquals((s intersect s).raw, s.raw, "idempotence")
      assertEquals((s intersect BitSet64.Empty).raw, BitSet64.Empty.raw, "Empty annihilates")
    }
    xs.sliding(3).foreach {
      case a :: b :: c :: Nil =>
        assertEquals(((a intersect b) intersect c).raw, (a intersect (b intersect c)).raw)
        assertEquals((a intersect b).raw, (b intersect a).raw)
      case _ => ()
    }
  }

  test("De Morgan's laws hold") {
    val xs = sets(100)
    xs.zip(xs.reverse).foreach { (a, b) =>
      assertEquals(
        (a union b).complement.raw,
        (a.complement intersect b.complement).raw,
        s"~(a | b) != ~a & ~b for ${bin64(a.raw)} / ${bin64(b.raw)}"
      )
      assertEquals(
        (a intersect b).complement.raw,
        (a.complement union b.complement).raw,
        s"~(a & b) != ~a | ~b for ${bin64(a.raw)} / ${bin64(b.raw)}"
      )
    }
  }

  test("complement is an involution and partitions the universe") {
    sets(120).foreach { s =>
      assertEquals(s.complement.complement.raw, s.raw, "complement is not an involution")
      assertEquals(
        (s union s.complement).raw,
        BitSet64.Full.raw,
        "a set and its complement must cover"
      )
      assertEquals((s intersect s.complement).raw, BitSet64.Empty.raw, "they must not overlap")
      assertEquals(s.size + s.complement.size, 64, "cardinalities must sum to the universe")
    }
  }

  test("symmetric difference makes the algebra a group of exponent two") {
    val xs = sets(120)
    xs.foreach { s =>
      assertEquals((s symDiff s).raw, BitSet64.Empty.raw, "every element is its own inverse")
      assertEquals((s symDiff BitSet64.Empty).raw, s.raw, "Empty is the identity")
      assertEquals((s symDiff BitSet64.Full).raw, s.complement.raw, "xor with Full complements")
    }
    xs.sliding(3).foreach {
      case a :: b :: c :: Nil =>
        assertEquals(((a symDiff b) symDiff c).raw, (a symDiff (b symDiff c)).raw, "associativity")
        assertEquals((a symDiff b).raw, (b symDiff a).raw, "commutativity")
      case _ => ()
    }
  }

  test("diff, subsetOf and size cohere with the set reading") {
    val xs = sets(100)
    xs.zip(xs.reverse).foreach { (a, b) =>
      assertEquals((a diff b).raw, (a intersect b.complement).raw, "diff is not a & ~b")
      assert((a diff b) subsetOf a, "a difference must be a subset of its left operand")
      assert(a subsetOf a, "subsetOf must be reflexive")
      assert(BitSet64.Empty subsetOf a, "the empty set is a subset of everything")
      assert(a subsetOf BitSet64.Full, "everything is a subset of the universe")
      assertEquals(a subsetOf b, (a union b).raw == b.raw, "subsetOf disagrees with union")
      assertEquals(
        (a union b).size,
        a.size + b.size - (a intersect b).size,
        "cardinality violates inclusion-exclusion"
      )
    }
  }

  test("toList is sorted, duplicate-free, and agrees with contains") {
    sets(200).foreach { s =>
      val listed = s.toList
      assertEquals(listed, listed.sorted, s"toList is not ascending for ${bin64(s.raw)}")
      assertEquals(listed, listed.distinct, "toList repeated a member")
      assertEquals(listed.size, s.size, "toList disagrees with size")
      members.foreach { i =>
        assertEquals(listed.contains(i), s.contains(i), s"toList disagrees with contains at $i")
      }
    }
    assertEquals(BitSet64.Empty.toList, Nil)
    assertEquals(BitSet64.Full.toList, members.toList)
  }

  test("of and toList round-trip") {
    sets(200).foreach { s =>
      assertEquals(BitSet64.of(s.toList*).raw, s.raw, "of . toList is not the identity")
    }
  }

end Exercise6BitSet64Spec
