package cs.se.annex.a1

/** Exercise 8 (Hard) — BitmapIndex: the HAMT node primitive. */
class Exercise8BitmapIndexSpec extends AnnexA1Harness:

  private val slots = 0 to 31

  /** Build a node holding `slots` mapped to their own index as a value, so that
    * every assertion can name which child it expected.
    */
  private def nodeOf(occupied: List[Int]): SparseNode[String] =
    val bitmap = occupied.foldLeft(0)((b, s) => b | (1 << s))
    val children = IArray.from(occupied.sorted.map(s => s"child$s"))
    SparseNode(bitmap, children)

  private def invariant(node: SparseNode[String], context: String): Unit =
    assertEquals(
      node.children.length,
      Integer.bitCount(node.bitmap),
      s"$context: the dense array must hold exactly popcount(bitmap) children"
    )

  test("hashSlice reads five bits per level and never leaves [0, 31]") {
    domain(1000).foreach { hash =>
      (0 to 6).foreach { level =>
        val slot = BitmapIndex.hashSlice(hash, level)
        assert(slot >= 0 && slot <= 31, s"slice of ${bin(hash)} at level $level = $slot")
        assertEquals(slot, (hash >>> (5 * level)) & 0x1f, s"slice of ${bin(hash)} at $level")
      }
    }
  }

  test("hashSlice on a negative hash must not sign-extend") {
    // Level 6 reads the top two bits. An arithmetic shift smears the sign
    // across them and produces a slot outside the valid range.
    assertEquals(BitmapIndex.hashSlice(-1, 6), 3, "the top two bits of -1 are 11")
    assertEquals(BitmapIndex.hashSlice(Int.MinValue, 6), 2, "sign bit only")
    assertEquals(BitmapIndex.hashSlice(-1, 0), 31)
    assertEquals(BitmapIndex.hashSlice(0, 0), 0)
    List(-1, Int.MinValue, -12345678).foreach { h =>
      (0 to 6).foreach { l =>
        val s = BitmapIndex.hashSlice(h, l)
        assert(s >= 0 && s <= 31, s"negative hash $h at level $l produced slot $s")
      }
    }
  }

  test("consecutive levels partition the hash without overlap or loss") {
    domain(500).foreach { hash =>
      val rebuilt = (0 to 6).foldLeft(0) { (acc, level) =>
        acc | (BitmapIndex.hashSlice(hash, level) << (5 * level))
      }
      assertEquals(rebuilt, hash, s"the slices of ${bin(hash)} do not reconstruct it")
    }
  }

  test("hasSlot and arity read the bitmap correctly") {
    domain(500).foreach { bitmap =>
      assertEquals(BitmapIndex.arity(bitmap), Integer.bitCount(bitmap), s"arity of ${bin(bitmap)}")
      slots.foreach { s =>
        assertEquals(
          BitmapIndex.hasSlot(bitmap, s),
          (bitmap & (1 << s)) != 0,
          s"hasSlot(${bin(bitmap)}, $s)"
        )
      }
    }
    assertEquals(BitmapIndex.arity(0), 0)
    assertEquals(BitmapIndex.arity(-1), 32, "a full node holds thirty-two children")
  }

  test("physicalIndex counts the occupants strictly below the slot") {
    domain(500).foreach { bitmap =>
      slots.foreach { s =>
        val expected = Integer.bitCount(bitmap & ((1 << s) - 1))
        assertEquals(
          BitmapIndex.physicalIndex(bitmap, s),
          expected,
          s"physicalIndex(${bin(bitmap)}, $s)"
        )
      }
    }
    assertEquals(BitmapIndex.physicalIndex(0, 31), 0, "an empty node has no occupants below")
    assertEquals(BitmapIndex.physicalIndex(-1, 31), 31, "a full node has thirty-one below slot 31")
    assertEquals(BitmapIndex.physicalIndex(-1, 0), 0)
  }

  test("physicalIndex is monotone and stays within the dense array") {
    domain(300).foreach { bitmap =>
      val arity = Integer.bitCount(bitmap)
      val indices = slots.map(s => BitmapIndex.physicalIndex(bitmap, s))
      assertEquals(indices, indices.sorted, s"physicalIndex is not monotone for ${bin(bitmap)}")
      slots.foreach { s =>
        val i = BitmapIndex.physicalIndex(bitmap, s)
        if BitmapIndex.hasSlot(bitmap, s) then
          assert(i >= 0 && i < arity, s"occupied slot $s maps outside [0, $arity)")
        else assert(i >= 0 && i <= arity, s"free slot $s maps outside [0, $arity]")
      }
    }
  }

  test("get returns the child stored at the logical slot") {
    val occupied = List(0, 3, 7, 31)
    val node = nodeOf(occupied)
    invariant(node, "fixture")
    slots.foreach { s =>
      val expected = if occupied.contains(s) then Some(s"child$s") else None
      assertEquals(BitmapIndex.get(node, s), expected, s"get at slot $s")
    }
  }

  test("inserted places the child so that get finds it, preserving the others") {
    val occupied = List(2, 9, 30)
    val node = nodeOf(occupied)
    slots.filterNot(occupied.contains).foreach { s =>
      BitmapIndex.inserted(node, s, "new") match
        case Some(updated) =>
          invariant(updated, s"after inserting at $s")
          assertEquals(updated.children.length, node.children.length + 1)
          assertEquals(BitmapIndex.get(updated, s), Some("new"), s"the new child is not at $s")
          occupied.foreach { o =>
            assertEquals(BitmapIndex.get(updated, o), Some(s"child$o"), s"$o moved or was lost")
          }
        case None => fail(s"slot $s is free and must accept an insertion")
    }
  }

  test("inserted refuses an occupied slot rather than silently replacing") {
    val node = nodeOf(List(2, 9, 30))
    List(2, 9, 30).foreach { s =>
      assertEquals(BitmapIndex.inserted(node, s, "clobber"), None, s"slot $s is occupied")
    }
  }

  test("inserted does not mutate its input — the whole point of structural sharing") {
    val node = nodeOf(List(1, 5))
    val before = node.children.toList
    val beforeBitmap = node.bitmap
    val _ = BitmapIndex.inserted(node, 20, "new")
    assertEquals(node.children.toList, before, "the source node's array was mutated")
    assertEquals(node.bitmap, beforeBitmap, "the source node's bitmap was mutated")
  }

  test("removed vacates exactly one slot and refuses an empty one") {
    val occupied = List(0, 4, 17, 31)
    val node = nodeOf(occupied)
    occupied.foreach { s =>
      BitmapIndex.removed(node, s) match
        case Some(updated) =>
          invariant(updated, s"after removing $s")
          assertEquals(updated.children.length, node.children.length - 1)
          assertEquals(BitmapIndex.get(updated, s), None, s"slot $s survived removal")
          occupied.filter(_ != s).foreach { o =>
            assertEquals(BitmapIndex.get(updated, o), Some(s"child$o"), s"$o was disturbed")
          }
        case None => fail(s"slot $s is occupied and must be removable")
    }
    slots.filterNot(occupied.contains).foreach { s =>
      assertEquals(BitmapIndex.removed(node, s), None, s"slot $s is already empty")
    }
  }

  test("inserted then removed is the identity on the whole node") {
    val node = nodeOf(List(3, 11, 22))
    slots.filterNot(List(3, 11, 22).contains).foreach { s =>
      val roundTripped = BitmapIndex.inserted(node, s, "temp").flatMap(BitmapIndex.removed(_, s))
      roundTripped match
        case Some(back) =>
          assertEquals(back.bitmap, node.bitmap, s"bitmap not restored after insert/remove at $s")
          assertEquals(back.children.toList, node.children.toList, s"children not restored at $s")
        case None => fail(s"the round trip at slot $s failed")
    }
  }

  test("a node built by repeated insertion in any order holds the same contents") {
    val target = List(1, 4, 8, 15, 16, 23, 31)
    val forwards = target.foldLeft(Option(SparseNode(0, IArray.empty[String]))) { (acc, s) =>
      acc.flatMap(n => BitmapIndex.inserted(n, s, s"child$s"))
    }
    val backwards = target.reverse.foldLeft(Option(SparseNode(0, IArray.empty[String]))) {
      (acc, s) =>
        acc.flatMap(n => BitmapIndex.inserted(n, s, s"child$s"))
    }
    (forwards, backwards) match
      case (Some(a), Some(b)) =>
        invariant(a, "forwards")
        assertEquals(a.bitmap, b.bitmap, "insertion order changed the bitmap")
        assertEquals(a.children.toList, b.children.toList, "insertion order changed the layout")
        assertEquals(
          a.children.toList,
          target.map(s => s"child$s"),
          "the dense array is not sorted"
        )
      case _ => fail("building a node by repeated insertion failed")
  }

end Exercise8BitmapIndexSpec
