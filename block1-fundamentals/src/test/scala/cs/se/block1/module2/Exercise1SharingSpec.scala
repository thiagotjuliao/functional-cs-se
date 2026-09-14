package cs.se.block1.module2

import cs.se.block1.module1.Footprint

/** Exercise 1 — the cost model. Arithmetic only: this suite can go green
  * before a single data structure exists, which is why the exercise is first.
  */
class Exercise1SharingSpec extends Module2Harness:

  test("the cell and node sizes are inherited from Module 1, not restated") {
    assertEquals(Sharing.CellBytes, Footprint.shallowSize(2, 0, 0, 0, 0))
    assertEquals(Sharing.NodeBytes, Footprint.shallowSize(3, 0, 0, 0, 0))

    // The value both land on, on this JVM. If these fail, Module 1's model
    // changed and this module inherits the change rather than contradicting it.
    assertEquals(Sharing.CellBytes, 24, "12 header + 4 head + 4 tail = 20 -> 24")
    assertEquals(Sharing.NodeBytes, 24, "the third reference fits in the cell's padding")
  }

  test("prepend does not depend on n; append, reverse and map are linear in it") {
    List(0, 1, 2, 1_000, 100_000, 10_000_000).foreach { n =>
      assertEquals(Sharing.prependCells(n), 1L, s"prepend must cost one cell at n = $n")
    }

    assertEquals(Sharing.appendCells(0), 0L, "appending to the empty list rebuilds nothing")
    assertEquals(Sharing.appendCells(1), 1L)
    assertEquals(Sharing.appendCells(100_000), 100_000L)
    assertEquals(Sharing.reverseCells(100_000), 100_000L)
    assertEquals(Sharing.mapCells(100_000), 100_000L)

    // No Int overflow at scale: this is why the return type is Long.
    assert(Sharing.appendCells(2_000_000_000) > 0L, "appendCells overflowed")
    assert(Sharing.cellBytes(Sharing.appendCells(2_000_000_000)) > 0L, "cellBytes overflowed")
  }

  test("balancedDepth counts nodes on the path, and the boundaries are exact") {
    assertEquals(Sharing.balancedDepth(0), 0, "the empty tree has no path")
    assertEquals(Sharing.balancedDepth(1), 1)
    assertEquals(Sharing.balancedDepth(2), 2)
    assertEquals(Sharing.balancedDepth(3), 2, "2^2 - 1: a root and two children")
    assertEquals(Sharing.balancedDepth(4), 3, "one past a full level: the boundary")
    assertEquals(Sharing.balancedDepth(7), 3)
    assertEquals(Sharing.balancedDepth(15), 4)
    assertEquals(Sharing.balancedDepth(16), 5, "off-by-one here is error-patterns pattern 3")
    assertEquals(Sharing.balancedDepth(1_000), 10)
    assertEquals(Sharing.balancedDepth(1_000_000), 20)
    assertEquals(Sharing.balancedDepth(1_048_575), 20, "2^20 - 1, exactly full")
    assertEquals(Sharing.balancedDepth(1_048_576), 21, "one more value, one more level")

    // Monotone, and it grows by at most one per doubling. That is the whole
    // content of O(log n), asserted as a property rather than sampled.
    // k stops at 29 so that `2 * n` is at most 2^30 and stays inside Int.
    (1 to 29).foreach { k =>
      val n = 1 << k
      assert(
        Sharing.balancedDepth(2 * n) - Sharing.balancedDepth(n) <= 1,
        s"doubling from $n added more than one level"
      )
    }

    // A full tree of 2^k - 1 values is exactly k deep, across the whole range.
    // The upper half of this walk is not decoration: an implementation built on
    // `Math.ceil(Math.log(n + 1) / Math.log(2))` agrees with this one everywhere
    // below 2^29 and then reports 30 at 2^29 - 1, because the quotient comes
    // back as 29.000000000000004 and `ceil` promotes 4e-15 into a whole level.
    (1 to 30).foreach { k =>
      val n = (1 << k) - 1
      assertEquals(Sharing.balancedDepth(n), k, s"2^$k - 1 is a full tree of depth $k")
    }

    // The top of the domain. `n + 1` is Int arithmetic, so any implementation
    // that forms it overflows to Int.MinValue here; `Math.log` of a negative is
    // NaN, and narrowing NaN to Int yields 0 without throwing (JLS 5.1.3). The
    // symptom is that the largest tree expressible reports the depth of the
    // empty one.
    assertEquals(Sharing.balancedDepth(Int.MaxValue), 31, "2^31 - 1 values sit 31 levels deep")
    assertEquals(Sharing.treeInsertNodes(Int.MaxValue), 32L, "and an insert copies all 31")
  }

  test("an insert copies the path plus one new leaf, and the ratio is enormous") {
    assertEquals(Sharing.treeInsertNodes(0), 1L, "inserting into the empty tree makes one node")
    assertEquals(Sharing.treeInsertNodes(1_048_575), 21L, "depth 20, plus the new leaf")

    (1 to 20).foreach { k =>
      val n = (1 << k) - 1
      assertEquals(
        Sharing.treeInsertNodes(n),
        Sharing.balancedDepth(n).toLong + 1L,
        s"at n = $n the count must exceed the depth by exactly one"
      )
    }

    val ratio = Sharing.sharingRatio(1_048_575)
    report("predicted sharing ratio at 2^20 - 1", ratio)
    assertRatio(ratio, 49_932.0, 0.02, "the measured reference is about 49,932x")
    assertEquals(Sharing.sharingRatio(0), 0.0, "nothing to share in an empty tree")
  }

end Exercise1SharingSpec
