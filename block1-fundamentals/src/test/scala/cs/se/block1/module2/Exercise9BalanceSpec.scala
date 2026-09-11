package cs.se.block1.module2

/** Exercise 9 (Hard) — what insertion order does to depth, and what depth does
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

    // depth n against depth log2(n), plus the new leaf on each side.
    val expected = (n + 1).toDouble / (Sharing.balancedDepth(n) + 1).toDouble
    assertRatio(predicted, expected, 0.05, "the factor is a ratio of path lengths")

    warmup(20)(Balance.fromBalanced(64).insert(-1))

    val sorted = Balance.fromSorted(n)
    val balanced = Balance.fromBalanced(n)
    warmup(20)(sorted.insert(-1))
    warmup(20)(balanced.insert(-1))

    val sortedCost = probeBytes(sorted.insert(-1))
    val balancedCost = probeBytes(balanced.insert(-1))
    val measured = sortedCost.toDouble / balancedCost

    report("one insert into the degenerate tree (bytes)", sortedCost)
    report("one insert into the balanced tree (bytes)", balancedCost)
    report("measured degeneration factor", f"$measured%.1f x")

    assert(
      measured > predicted / 3.0,
      s"predicted a factor of about $predicted, measured $measured — if the measurement is " +
        "far smaller, the insert is not copying the path it walks"
    )
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
