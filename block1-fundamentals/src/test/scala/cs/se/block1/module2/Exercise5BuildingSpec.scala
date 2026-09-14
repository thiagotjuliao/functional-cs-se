package cs.se.block1.module2

/** Exercise 5 — the doubling test, run on your own structure.
  *
  * The assertions here are on **ratios**, never on absolute byte counts. An
  * absolute threshold would encode this machine's constants into a test that has
  * to pass on someone else's; a ratio is a property of the algorithm.
  */
class Exercise5BuildingSpec extends Module2Harness:

  test("both builders produce the same list") {
    assertEquals(Building.byAppend(0).toScalaList, List.empty[Int])
    assertEquals(Building.byPrepend(0).toScalaList, List.empty[Int])
    assertEquals(Building.byAppend(-5).toScalaList, List.empty[Int], "n <= 0 yields the empty list")

    (0 to 40).foreach { n =>
      assertEquals(
        Building.byAppend(n).toScalaList,
        Building.byPrepend(n).toScalaList,
        s"the two builders disagree at n = $n"
      )
      assertEquals(
        Building.byAppend(n).toScalaList,
        (0 until n).toList,
        s"wrong contents at n = $n"
      )
    }
  }

  test("doublingRatio is the arithmetic the test rests on") {
    assertEquals(Building.doublingRatio(100L, 200L), 2.0)
    assertEquals(Building.doublingRatio(100L, 400L), 4.0)
    assertEquals(Building.doublingRatio(0L, 400L), 0.0, "no ratio against a zero baseline")
  }

  test("byAppend is quadratic and byPrepend is linear — measured, not asserted") {
    val sizes = List(2_000, 4_000, 8_000, 16_000)

    warmup(3)(Building.byAppend(2_000))
    warmup(3)(Building.byPrepend(2_000))

    val appendBytes = sizes.map(n => probeBytes(Building.byAppend(n)))
    val prependBytes = sizes.map(n => probeBytes(Building.byPrepend(n)))

    sizes.zip(appendBytes).zip(prependBytes).foreach { case ((n, a), p) =>
      report(s"n=$n  byAppend bytes", a)
      report(s"n=$n  byPrepend bytes", p)
    }

    val appendRatios = appendBytes.sliding(2).map(w => Building.doublingRatio(w(0), w(1))).toList
    val prependRatios = prependBytes.sliding(2).map(w => Building.doublingRatio(w(0), w(1))).toList

    report("byAppend  doubling ratios", appendRatios.map(r => f"$r%.3f").mkString(", "))
    report("byPrepend doubling ratios", prependRatios.map(r => f"$r%.3f").mkString(", "))

    // Quadrupling per doubling is O(n^2). The band is wide because the first
    // step carries the most constant-factor noise; the reference run measured
    // 3.964, 3.994, 3.998.
    appendRatios.foreach { r =>
      assert(r > 3.0 && r < 5.0, s"byAppend should quadruple per doubling; got $r")
    }

    // Doubling per doubling is O(n). Reference: 1.881, 2.012, 2.006.
    prependRatios.foreach { r =>
      assert(r > 1.5 && r < 2.6, s"byPrepend should double per doubling; got $r")
    }

    // And the gap itself must widen, which is what n^2 / n = n looks like from
    // the outside. This is the assertion that fails if both are the same class.
    val firstGap = appendBytes.head.toDouble / prependBytes.head
    val lastGap = appendBytes.last.toDouble / prependBytes.last
    report("gap at n=2,000", f"$firstGap%.1f x")
    report("gap at n=16,000", f"$lastGap%.1f x")
    assert(
      lastGap > firstGap * 4.0,
      s"the gap must widen with n: $firstGap at 2,000 against $lastGap at 16,000"
    )
  }

end Exercise5BuildingSpec
