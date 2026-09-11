package cs.se.block1.module2

/** Exercise 8 (Hard) — the model against the machine.
  *
  * Every assertion here compares a measurement with what Exercise 1 predicted
  * from arithmetic alone. Tolerances are generous, because the instrument has a
  * floor and the JIT has opinions; what the tolerances may never do is hide a
  * difference in *class*, which is why the prepend test asserts independence
  * from `n` rather than a byte count.
  */
class Exercise8SharingProofSpec extends Module2Harness:

  private val N = 100_000

  test("prepend costs one cell, and the cost does not move with n") {
    val small = SharingProof.measurePrepend(1_000)
    val large = SharingProof.measurePrepend(N)

    report("measured prepend at n=1,000", small)
    report("measured prepend at n=100,000", large)
    report("predicted prepend", Sharing.cellBytes(Sharing.prependCells(N)))

    assertEquals(small, large, "prepend must not depend on the length of the list")
    assert(
      large <= Sharing.CellBytes.toLong * 2L,
      s"one cell is ${Sharing.CellBytes} bytes; measured $large. More than two cells means " +
        "something is being rebuilt that should have been shared."
    )
  }

  test("append and reverse rebuild the whole spine, as predicted") {
    val predicted = Sharing.cellBytes(Sharing.appendCells(N))
    val appended = SharingProof.measureAppend(N)
    val reversed = SharingProof.measureReverse(N)

    report("predicted append/reverse bytes", predicted)
    report("measured append bytes", appended)
    report("measured reverse bytes", reversed)

    assertRatio(appended.toDouble, predicted.toDouble, 0.10, "append against the model")
    assertRatio(reversed.toDouble, predicted.toDouble, 0.10, "reverse against the model")

    // And the headline: the same operation at the two ends, four orders apart.
    val ratio = appended.toDouble / SharingProof.measurePrepend(N)
    report("append / prepend", f"$ratio%.0f x")
    assert(ratio > 10_000.0, s"the two ends should differ by orders of magnitude; got $ratio")
  }

  test("one tree insert costs the path, not the tree") {
    val n = 1_048_575
    val predicted = Sharing.nodeBytes(Sharing.treeInsertNodes(n))
    val measured = SharingProof.measureTreeInsert(n)
    val whole = Sharing.nodeBytes(n.toLong)

    report("whole tree bytes", whole)
    report("predicted one insert", predicted)
    report("measured one insert", measured)
    report("measured sharing ratio", f"${whole.toDouble / measured}%.0f x")

    // Within a couple of nodes of the prediction. The reference measurement is
    // 504 bytes: 21 nodes at 24, for a depth of 20 plus one new leaf.
    assert(
      math.abs(measured - predicted) <= Sharing.NodeBytes.toLong * 3L,
      s"predicted $predicted bytes, measured $measured — a gap of more than three nodes " +
        "means the path length is not what the model thinks it is"
    )
    assert(
      whole.toDouble / measured > 10_000.0,
      "one insert should be four orders of magnitude cheaper than the tree it updates"
    )
  }

end Exercise8SharingProofSpec
