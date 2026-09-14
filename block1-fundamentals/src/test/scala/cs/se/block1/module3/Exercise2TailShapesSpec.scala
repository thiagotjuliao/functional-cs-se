package cs.se.block1.module3

/** Exercise 2 — three spellings, one answer, one ceiling. */
class Exercise2TailShapesSpec extends Module3Harness:

  private def expected(n: Int): Long = n.toLong * (n + 1) / 2

  test("all three agree wherever all three survive") {
    List(0, 1, 2, 3, 10, 1_000, 10_000).foreach { n =>
      assertEquals(TailShapes.sumNaive(n), expected(n), s"sumNaive($n)")
      assertEquals(TailShapes.sumAcc(n), expected(n), s"sumAcc($n)")
      assertEquals(TailShapes.sumLoop(n), expected(n), s"sumLoop($n)")
    }
    List(0, -1, -100).foreach { n =>
      assertEquals(TailShapes.sumNaive(n), 0L, s"sumNaive($n) must be 0 on the empty range")
      assertEquals(TailShapes.sumAcc(n), 0L, s"sumAcc($n)")
      assertEquals(TailShapes.sumLoop(n), 0L, s"sumLoop($n)")
    }
  }

  test("two of the three have no ceiling, and one does") {
    val ceiling = StackProbe.maxDepth(1 << 20)(TailShapes.sumNaive)
    report("largest n that sumNaive survives", ceiling)

    assert(ceiling > 1_000, s"sumNaive should manage a few thousand frames; got $ceiling")
    assert(
      ceiling < (1 << 20),
      "if sumNaive has no ceiling it is not the control this exercise needs"
    )

    assert(survives(TailShapes.sumAcc(10_000_000)), "sumAcc must not have a ceiling at 10,000,000")
    assert(
      survives(TailShapes.sumLoop(10_000_000)),
      "sumLoop must not have a ceiling at 10,000,000"
    )

    // The separation is the result. The number is a property of this machine's
    // stack and is reported rather than asserted - guide §23.
    assert(
      10_000_000L > ceiling.toLong * 100,
      s"the two shapes should differ by orders of magnitude, not by a margin; sumNaive stops at $ceiling"
    )
  }

  test("sumAcc and sumLoop agree at a size sumNaive cannot reach") {
    val n = 1_000_000
    assertEquals(
      TailShapes.sumAcc(n),
      TailShapes.sumLoop(n),
      "the recursion and the loop must remain the same function past the naive version's ceiling"
    )
    assertEquals(TailShapes.sumAcc(n), expected(n))
  }

  test("the ceiling moves with the stack, which is why it is never asserted") {
    val small = StackProbe.onStack(256)(StackProbe.maxDepth(1 << 22)(TailShapes.sumNaive))
    val large = StackProbe.onStack(4096)(StackProbe.maxDepth(1 << 22)(TailShapes.sumNaive))
    report("sumNaive ceiling at 256 KiB", small)
    report("sumNaive ceiling at 4 MiB", large)
    assert(
      large > small * 8,
      s"the ceiling is a property of -Xss, not of the function: $small -> $large. If these are " +
        "close, the measurement is not measuring the stack"
    )
  }

end Exercise2TailShapesSpec
