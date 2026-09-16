package cs.se.block1.module3

/** Exercise 2 — three spellings, one answer, one ceiling. */
class Exercise2TailShapesSpec extends Module3Harness:

  private def expected(n: Int): Long = n.toLong * (n + 1) / 2

  test("all three agree wherever all three survive") {
    List(0, 1, 2, 3, 10, 1_000).foreach { n =>
      assertEquals(TailShapes.sumNaive(n), expected(n), s"sumNaive($n)")
      assertEquals(TailShapes.sumAcc(n), expected(n), s"sumAcc($n)")
      assertEquals(TailShapes.sumLoop(n), expected(n), s"sumLoop($n)")
    }

    // 10,000 is not a small number for sumNaive, and this is the least
    // affordable place in the suite to pretend that it is: the value test runs
    // first, when the method is coldest and its interpreted frames are at their
    // largest. Measured cold on a fresh JVM, the ceiling comes out at 16,383 or
    // at about 41,500 depending on whether C2 steps in during the search - and
    // it is lower still at the instant this line runs, because nothing has
    // touched sumNaive yet and any probe that measures the ceiling raises it.
    // The suite cannot report the number that decides this assertion.
    //
    // Observed: StackOverflowError inside sumNaive(10_000), once in 19 runs,
    // reported against a function behaving exactly as specified.
    //
    // The agreement under test is about values and not about depth, so the
    // stack is stated rather than hoped for. error-patterns.md pattern 16.
    StackProbe.onStack(8192) {
      val n = 10_000
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
    //
    // The factor asks for one order of magnitude and not two. This ceiling
    // moves by up to 2.51x inside a single process as C2 replaces interpreted
    // frames with compiled ones, observed here between 13,711 and 41,139 on
    // consecutive runs of this very suite. A threshold whose headroom is the
    // same size as the drift fails intermittently, and blames the
    // implementation when it does - error-patterns.md pattern 13.
    assert(
      10_000_000L > ceiling.toLong * 10,
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
    // 16x the stack, so a fixed frame would buy about 16x the depth. The two
    // measurements are taken separately and each can land on either side of
    // C2's 2.51x step, so the worst honest ratio is 16 / 2.51 = 6.4 and the
    // threshold has to sit below that with room left over. Pattern 13 again:
    // the margin is set wider than the drift that was actually observed, not
    // wider than the drift that was expected.
    assert(
      large > small * 4,
      s"the ceiling is a property of -Xss, not of the function: $small -> $large. If these are " +
        "close, the measurement is not measuring the stack"
    )
  }

end Exercise2TailShapesSpec
