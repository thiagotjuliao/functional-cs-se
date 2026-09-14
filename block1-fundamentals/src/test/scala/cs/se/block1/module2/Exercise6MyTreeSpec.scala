package cs.se.block1.module2

/** Exercise 6 — the BST, its invariant, and what depth depends on. */
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

  // ---------------------------------------------------------------------------
  // What `contains` costs, as distinct from what it returns.
  //
  // Every test above asks what `contains` answers, and a search that visits
  // every node answers all of them correctly. That is not a hypothetical: the
  // first implementation did exactly that — `(v == x) || l.contains(x) ||
  // r.contains(x)` — and this suite was green through it, with an `O(depth)`
  // contract in the Scaladoc and an `O(n)` traversal under it.
  //
  // The two tests below are the ones that separate the two implementations.
  // They assert different regressions and neither subsumes the other.
  // Challenge-log entry 23.
  // ---------------------------------------------------------------------------

  /** A key whose `equals` is never true, and whose `Ordering` is ordinary.
    *
    * The two notions of sameness are deliberately in conflict, because the
    * tree was built by the `Ordering` and must therefore be searched by it. A
    * `contains` that reaches for `==` cannot find anything here; one that uses
    * the `Ordering`'s trichotomy is unaffected.
    *
    * Contrived on purpose, but the conflict is not: `equals` and `compare`
    * disagree for any type ordered on a subset of its fields, which is every
    * record sorted by a key.
    */
  private final class Key(val v: Int):
    override def equals(other: Any): Boolean = false
    override def hashCode: Int = v
    override def toString: String = s"Key($v)"

  private given Ordering[Key] = Ordering.by(_.v)

  test("contains asks the Ordering, not ==") {
    val values = List(8, 4, 12, 2, 6, 10, 14)
    val t = values.foldLeft(MyTree.Leaf: MyTree[Key])((acc, x) => acc.insert(Key(x)))

    assertEquals(
      t.size,
      values.size,
      "insert already uses the Ordering; if this fails, that changed"
    )

    values.foreach { x =>
      assert(
        t.contains(Key(x)),
        s"$x was inserted and cannot be found. `equals` is false for every Key here, so a " +
          "`contains` that tests equality instead of trichotomy finds nothing at all"
      )
    }
    List(0, 1, 7, 15, 100).foreach { x =>
      assert(!t.contains(Key(x)), s"$x was never inserted and must not be found")
    }
  }

  test("contains descends one side, so its cost follows depth and not size") {
    val n = 4_096
    val counter = java.util.concurrent.atomic.AtomicLong(0)
    given counting: Ordering[Int] = (a, b) =>
      counter.incrementAndGet()
      Integer.compare(a, b)

    def comparisons(t: MyTree[Int], x: Int): Long =
      counter.set(0)
      val _ = t.contains(x)(using counting)
      counter.get

    val balanced = MyTree.fromRange(0, n)
    val degenerate = MyTree.fromMyList(Building.byPrepend(n))
    assertEquals(balanced.size, n)
    assertEquals(degenerate.size, n)

    // A node costs one comparison when the search goes left and two when it
    // goes right or stops, so a descent of d nodes costs at most 2d.
    val ceiling = 2L * (balanced.depth + 1)
    val aboveMax = comparisons(balanced, n)
    val belowMin = comparisons(balanced, -1)
    val present = comparisons(balanced, n / 2)

    report("balanced depth", balanced.depth)
    report(
      "balanced comparisons: above max / below min / present",
      s"$aboveMax / $belowMin / $present"
    )

    List("above the maximum" -> aboveMax, "below the minimum" -> belowMin, "present" -> present)
      .foreach { (label, got) =>
        assert(
          got > 0,
          s"contains($label) consulted the Ordering zero times — it is not searching by order"
        )
        assert(
          got <= ceiling,
          s"contains($label) cost $got comparisons over a tree $n deep in size and " +
            s"${balanced.depth} deep in path; a descent cannot exceed $ceiling. A search that " +
            "visits both subtrees is O(n) behind an O(depth) contract"
        )
      }

    // And the same call on a degenerate tree must cost what that tree's shape
    // says, which is the whole subject of Exercise 9. If this ratio collapses,
    // `contains` has stopped reading the invariant.
    val degenerateAboveMax = comparisons(degenerate, n)
    report("degenerate comparisons above max", degenerateAboveMax)
    assert(
      degenerateAboveMax > 100L * aboveMax,
      s"the degenerate tree cost $degenerateAboveMax against the balanced tree's $aboveMax. " +
        "Two structures holding the same values, one of them 4,096 deep, must not cost the same"
    )

    // Probing below the minimum of a right spine is that tree's *cheapest*
    // query, not its worst — error-patterns.md pattern 11, twice recorded.
    // Asserted here so the trap is documented by a test and not only by prose.
    assertEquals(
      comparisons(degenerate, -1),
      1L,
      "a value below the minimum stops at the root's empty left child; if this is not 1, the " +
        "search is not stopping where the invariant says it may"
    )
  }

end Exercise6MyTreeSpec
