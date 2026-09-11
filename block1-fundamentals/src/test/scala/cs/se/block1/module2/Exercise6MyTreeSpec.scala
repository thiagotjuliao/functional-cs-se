package cs.se.block1.module2

/** Exercise 6 (Medium) — the BST, its invariant, and what depth depends on. */
class Exercise6MyTreeSpec extends Module2Harness:

  private def build(xs: Int*): MyTree[Int] =
    xs.foldLeft(MyTree.Leaf: MyTree[Int])((t, x) => t.insert(x))

  test("insert and contains respect the search invariant") {
    val empty = MyTree.Leaf: MyTree[Int]
    assert(!empty.contains(1), "the empty tree contains nothing")
    assertEquals(empty.size, 0)
    assertEquals(empty.depth, 0, "the empty tree has no path")

    val t = build(8, 4, 12, 2, 6, 10, 14)
    List(8, 4, 12, 2, 6, 10, 14).foreach(x => assert(t.contains(x), s"$x should be present"))
    List(0, 1, 3, 5, 7, 9, 11, 13, 15, 100).foreach(x =>
      assert(!t.contains(x), s"$x should be absent")
    )
    assertEquals(t.size, 7)
    assertEquals(t.depth, 3, "seven values inserted level by level is three deep")
  }

  test("inserting a value already present does not change the contents") {
    val t = build(5, 3, 8)
    val again = t.insert(5)
    assertEquals(again.size, t.size, "a duplicate must not grow the tree")
    assertEquals(again.toMyList.toScalaList, t.toMyList.toScalaList)

    // Whether a duplicate insert returns the *same* tree or an equal copy is a
    // design choice, and it is worth knowing which one you made: one is free and
    // one costs the whole path. This only reports it; §G asks you to defend it.
    val shared = again.asInstanceOf[AnyRef] eq t.asInstanceOf[AnyRef]
    report("duplicate insert returns the same instance", shared)
  }

  test("the old version survives the new one untouched") {
    val before = build(8, 4, 12)
    val after = before.insert(6)

    assertEquals(before.size, 3, "the old tree must not have grown")
    assert(!before.contains(6), "the old tree must not see the new value")
    assertEquals(after.size, 4)
    assert(after.contains(6))

    // Every value of the old tree is still reachable from it.
    List(8, 4, 12).foreach { x =>
      assert(before.contains(x), s"$x vanished from the old version")
      assert(after.contains(x), s"$x vanished from the new version")
    }
  }

  test("fromRange is perfectly balanced, and depth follows Exercise 1's model") {
    assertEquals(MyTree.fromRange(0, 0).size, 0)
    assertEquals(MyTree.fromRange(0, 1).size, 1)
    assertEquals(MyTree.fromRange(0, 1).depth, 1)

    List(3, 7, 15, 31, 1023).foreach { n =>
      val t = MyTree.fromRange(0, n)
      assertEquals(t.size, n, s"fromRange(0, $n) must hold $n values")
      assertEquals(
        t.depth,
        Sharing.balancedDepth(n),
        s"at n = $n the tree's depth must match the model's prediction"
      )
      (0 until n).foreach(x => assert(t.contains(x), s"$x missing from fromRange(0, $n)"))
    }
  }

  test("fromMyList inserts left to right, so the order decides the depth") {
    val ascending = MyTree.fromMyList(Building.byPrepend(64))
    val balanced = MyTree.fromRange(0, 64)

    assertEquals(ascending.size, 64)
    assertEquals(balanced.size, 64)

    report("depth from ascending inserts", ascending.depth)
    report("depth from balanced construction", balanced.depth)

    assertEquals(ascending.depth, 64, "ascending input degenerates the tree into a list")
    assert(
      balanced.depth <= 7,
      s"a balanced tree of 64 values is at most 7 deep, got ${balanced.depth}"
    )
  }

end Exercise6MyTreeSpec
