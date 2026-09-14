package cs.se.block1.module2

import cs.se.block1.module1.Footprint

/** Exercise 8 — the model against the machine.
  *
  * Every assertion here compares a measurement with what Exercise 1 predicted
  * from arithmetic alone. Tolerances are generous, because the instrument has a
  * floor and the JIT has opinions; what the tolerances may never do is hide a
  * difference in *class*, which is why the prepend test asserts independence
  * from `n` rather than a byte count.
  */
class Exercise8SharingProofSpec extends Module2Harness:

  private val N = 100_000

  /** One `java.lang.Integer`.
    *
    * Every measurement that introduces an element introduces an `Int` above the
    * `Integer` cache into a structure whose `A` is erased to `Object`, so the
    * box is allocated inside the measured window. The model counts cells and
    * nodes and says so — `reverseCells`: "Note what is *not* allocated: the
    * elements" — so it comes off before the two are compared. Derived from
    * Module 1's layout rather than written as 16.
    *
    * `measureReverse` is the one measurement that does not pay it: reversing
    * introduces no element. That asymmetry is the check on this whole
    * subtraction — if `reverse` ever needs it, the box is not what it is.
    */
  private val Box: Long =
    Footprint.align(Footprint.HeaderBytes + Footprint.IntegerBytes).toLong

  test("prepend costs one cell, and the cost does not move with n") {
    val small = SharingProof.measurePrepend(1_000) - Box
    val large = SharingProof.measurePrepend(N) - Box
    val predicted = Sharing.cellBytes(Sharing.prependCells(N))

    report("measured prepend at n=1,000 (box removed)", small)
    report("measured prepend at n=100,000 (box removed)", large)
    report("predicted prepend", predicted)

    assertEquals(small, large, "prepend must not depend on the length of the list")
    // With the element's box accounted for there is nothing left to excuse: one
    // cell, and the measurement is an equality rather than a ceiling.
    assertEquals(
      large,
      predicted,
      s"one cell is ${Sharing.CellBytes} bytes; anything above it is a cell that was " +
        "rebuilt instead of shared"
    )
  }

  test("append and reverse rebuild the whole spine — and one of them twice") {
    val retained = Sharing.cellBytes(Sharing.appendCells(N))
    val allocated = Sharing.cellBytes(Sharing.appendAllocatedCells(N))
    val appended = SharingProof.measureAppend(N) - Box
    val reversed = SharingProof.measureReverse(N)

    report("append: spine retained (model)", retained)
    report("append: cells allocated (model)", allocated)
    report("measured append bytes (box removed)", appended)
    report("predicted reverse bytes", Sharing.cellBytes(Sharing.reverseCells(N)))
    report("measured reverse bytes", reversed)

    // `reverse` allocates exactly what it retains, so it lands on the model with
    // no slack at all: n cells, no temporary, no element.
    assertRatio(
      reversed.toDouble,
      Sharing.cellBytes(Sharing.reverseCells(N)).toDouble,
      0.01,
      "reverse against the model"
    )

    // Append does not, and the difference is the whole point of splitting the
    // model in two. A `@tailrec` concat builds an intermediate spine and drops
    // it; the probe counts allocation, not retention, so it sees both.
    assertRatio(appended.toDouble, allocated.toDouble, 0.01, "append against the allocation model")
    assert(
      appended > retained * 3L / 2L,
      s"measured $appended against a retained spine of $retained — if these coincide, either " +
        "the implementation found a way to append without a temporary, or the probe is not " +
        "seeing the garbage it produces"
    )

    // And the headline: the same operation at the two ends, five orders apart.
    val ratio = appended.toDouble / (SharingProof.measurePrepend(N) - Box)
    report("append / prepend", f"$ratio%.0f x")
    assert(ratio > 10_000.0, s"the two ends should differ by orders of magnitude; got $ratio")
  }

  test("one tree insert costs the path, not the tree") {
    val n = 1_048_575
    val predicted = Sharing.nodeBytes(Sharing.treeInsertNodes(n))
    val measured = SharingProof.measureTreeInsert(n) - Box
    val whole = Sharing.nodeBytes(n.toLong)

    report("whole tree bytes", whole)
    report("predicted one insert", predicted)
    report("measured one insert (box removed)", measured)
    report("measured sharing ratio", f"${whole.toDouble / measured}%.0f x")

    // Within one node of the prediction. The reference measurement is 504 bytes:
    // 21 nodes at 24, for a depth of 20 plus one new leaf — and with the
    // element's box removed it is 504 on the nose, because an insert into a
    // persistent tree allocates the path and discards nothing.
    assert(
      math.abs(measured - predicted) <= Sharing.NodeBytes.toLong,
      s"predicted $predicted bytes, measured $measured — a gap of more than one node " +
        "means the path length is not what the model thinks it is"
    )
    assert(
      whole.toDouble / measured > 10_000.0,
      "one insert should be four orders of magnitude cheaper than the tree it updates"
    )
  }

end Exercise8SharingProofSpec
