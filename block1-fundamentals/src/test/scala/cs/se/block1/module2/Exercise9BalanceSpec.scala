package cs.se.block1.module2

import cs.se.block1.module1.Footprint

/** Exercise 9 — what insertion order does to depth, and what depth does
  * to cost.
  *
  * The whole module rests on one number, and that number is not a property of
  * the data. It is a property of the order the data arrived in — which makes
  * this the exercise that decides whether §15's 504 bytes was a result or an
  * accident of the fixture.
  */
class Exercise9BalanceSpec extends Module2Harness:

  test("sorted input degenerates the tree into a list wearing a tree's type") {
    List(16, 64, 256).foreach { n =>
      val sorted = Balance.fromSorted(n)
      assertEquals(sorted.size, n, s"fromSorted($n) must hold $n values")
      assertEquals(sorted.depth, n, s"every node has one child, so the depth is n at n = $n")
    }

    val n = 4_096
    val sorted = Balance.fromSorted(n)
    val balanced = Balance.fromBalanced(n)

    report("depth, sorted input", sorted.depth)
    report("depth, balanced construction", balanced.depth)

    assertEquals(sorted.size, balanced.size, "both hold the same values")
    assertEquals(
      sorted.toMyList.toScalaList,
      balanced.toMyList.toScalaList,
      "and in the same order"
    )
    assertEquals(balanced.depth, Sharing.balancedDepth(n), "the balanced one matches the model")
    assert(
      sorted.depth > balanced.depth * 100,
      "the degenerate tree should be hundreds of times deeper"
    )
  }

  test("the degeneration factor is read off the structure, then confirmed by measurement") {
    val n = 4_096
    val predicted = Balance.degenerationFactor(n)
    report("predicted degeneration factor", f"$predicted%.1f x")

    assertEquals(Balance.degenerationFactor(0), 0.0)

    // One insert allocates the path it rebuilds *and* the new leaf: `depth + 1`
    // nodes, which is what `Sharing.treeInsertNodes` already states. The `+1` is
    // noise against a depth of n and worth 7% against a depth of log2(n), so a
    // factor built from depths alone lands outside the tolerance below — and it
    // is the balanced side, the small one, that the constant distorts.
    val expected = (n + 1).toDouble / (Sharing.balancedDepth(n) + 1).toDouble
    assertRatio(predicted, expected, 0.05, "the factor is a ratio of nodes allocated")

    val sorted = Balance.fromSorted(n)
    val balanced = Balance.fromBalanced(n)

    // The probe value must exceed every value held, or the experiment reports
    // the opposite of the truth. Insert something *below* the minimum and the
    // ascending-built tree answers with its shallowest path — it is a spine to
    // the right, so a value smaller than the root lands as the root's left
    // child, two nodes — and the degenerate tree measures cheaper than the
    // balanced one. Worst case for a right spine is a value above its maximum.
    val probe = n

    warmup(20)(sorted.insert(probe))
    warmup(20)(balanced.insert(probe))

    // `insert` boxes its argument: `A` is erased to `Object`, and `probe` is far
    // above the `Integer` cache, so one `java.lang.Integer` is allocated inside
    // the measured window on each side. The model counts *nodes* and says so —
    // `Sharing.reverseCells`: "Note what is not allocated: the elements" — so
    // the box comes off both measurements before they are compared. Derived
    // from Module 1's layout model rather than written as 16.
    val box = Footprint.align(Footprint.HeaderBytes + Footprint.IntegerBytes).toLong
    val sortedCost = probeBytes(sorted.insert(probe)) - box
    val balancedCost = probeBytes(balanced.insert(probe)) - box
    val measured = sortedCost.toDouble / balancedCost

    report("one insert into the degenerate tree (bytes, box removed)", sortedCost)
    report("one insert into the balanced tree (bytes, box removed)", balancedCost)
    report("nodes, degenerate", sortedCost / Sharing.NodeBytes)
    report("nodes, balanced", balancedCost / Sharing.NodeBytes)
    report("measured degeneration factor", f"$measured%.1f x")

    // Direction before magnitude: a probe value that misses the worst case
    // inverts this, and inverts it silently.
    assert(
      sortedCost > balancedCost,
      s"the degenerate tree must cost more, not less: $sortedCost against $balancedCost"
    )
    assertRatio(measured, predicted, 0.05, "the measurement must land on the model")
  }

  test("depth also governs lookup, not only allocation") {
    val n = 2_048
    val sorted = Balance.fromSorted(n)
    val balanced = Balance.fromBalanced(n)

    // Both must answer correctly; the difference is what it costs them.
    (0 until n by 97).foreach { x =>
      assert(sorted.contains(x), s"$x missing from the degenerate tree")
      assert(balanced.contains(x), s"$x missing from the balanced tree")
    }
    assert(!sorted.contains(-1))
    assert(!balanced.contains(-1))

    // The worst case is the deepest value, and in a degenerate ascending tree
    // that is the last one inserted.
    assertEquals(sorted.depth, n, "lookup of the last value walks every node")
    assert(balanced.depth <= Sharing.balancedDepth(n), "the balanced tree bounds the same walk")
  }

end Exercise9BalanceSpec
