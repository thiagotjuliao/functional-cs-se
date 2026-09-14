package cs.se.block1.module3

import cs.se.block1.module2.{MyList, Sharing}
import cs.se.block1.module2.MyList.*

/** Exercise 6 — the ceiling removed, and the bill.
  *
  * The folds here are tested with **subtraction**, not addition. A suite that
  * folds with `+` cannot tell `f(a, b)` from `f(b, a)`, and the argument swap
  * that `foldRightSafe` requires is exactly that mistake waiting to happen.
  * Guide §21.
  */
class Exercise6SafeFoldSpec extends Module3Harness:

  test("foldRightSafe agrees with foldRight wherever foldRight survives") {
    val xs = MyList(1, 2, 3)

    // The canonical separator: (-) is neither associative nor commutative.
    assertEquals(xs.foldRight(0)(_ - _), 2, "1 - (2 - (3 - 0)) = 2")
    assertEquals(xs.foldLeft(0)(_ - _), -6, "((0 - 1) - 2) - 3 = -6, a different function")
    assertEquals(
      SafeFold.foldRightSafe(xs, 0)(_ - _),
      2,
      "foldRightSafe must be foldRight, not foldLeft wearing its name"
    )

    List(0, 1, 2, 7, 100, 5_000).foreach { n =>
      val l = cells(n)
      assertEquals(
        SafeFold.foldRightSafe(l, 0)(_ - _),
        l.foldRight(0)(_ - _),
        s"the two folds disagree at n = $n"
      )
    }

    // A fold into a different type, so the argument order cannot type-check by
    // accident the way it can when A and B coincide.
    assertEquals(
      SafeFold.foldRightSafe(MyList(1, 2, 3), "z")((a, b) => s"($a$b)"),
      "(1(2(3z)))",
      "the nesting must be to the right"
    )
  }

  test("foldRightSafe survives where foldRight does not") {
    val ceiling = maxSurviving(1 << 18)(n => cells(n).foldRight(0L)((a, b) => a + b))
    report("largest n that MyList.foldRight survives", ceiling)
    assert(ceiling < (1 << 18), "if foldRight has no ceiling, this module has no subject")

    val big = cells(1_000_000)
    assert(
      survives(SafeFold.foldRightSafe(big, 0L)((a, b) => a + b)),
      "foldRightSafe must survive a million"
    )
    assertEquals(SafeFold.foldRightSafe(big, 0L)((a, b) => a + b), 499_999_500_000L)
  }

  test("the bill is one spine, and the model says which") {
    val n = 100_000
    val l = cells(n)
    val direct = probeBytes(l.foldLeft(0L)((b, a) => b + a))
    val safe = probeBytes(SafeFold.foldRightSafe(l, 0L)((a, b) => a + b))
    val delta = safe - direct

    report("foldLeft bytes", direct)
    report("foldRightSafe bytes", safe)
    report("delta", s"$delta = ${delta / Sharing.CellBytes} cells")

    // One reverse, and Module 2's cost model already names its price. The
    // tolerance is on the *ratio* because the boxing of the accumulator differs
    // between the two calls and is not the subject here.
    assertRatio(
      delta.toDouble,
      Sharing.cellBytes(Sharing.reverseCells(n)).toDouble,
      0.05,
      "the extra allocation against one reversed spine"
    )
  }

  test("foldRightComposed returns the same answer and relocates the cost") {
    val xs = MyList(1, 2, 3)
    assertEquals(SafeFold.foldRightComposed(xs, 0)(_ - _), 2, "still a right fold")
    List(0, 1, 7, 100).foreach { n =>
      val l = cells(n)
      assertEquals(SafeFold.foldRightComposed(l, 0)(_ - _), l.foldRight(0)(_ - _), s"at n = $n")
    }

    val n = 50_000
    val l = cells(n)
    report(
      "foldRightComposed bytes",
      probeBytes(SafeFold.foldRightComposed(l, 0L)((a, b) => a + b))
    )
    val composedCeiling = maxSurviving(1 << 18) { m =>
      SafeFold.foldRightComposed(cells(m), 0L)((a, b) => a + b)
    }
    report("largest n that foldRightComposed survives", composedCeiling)

    // No assertion on which side of the comparison it lands. Whether building a
    // chain of closures removes the ceiling or merely moves it is the finding
    // this exercise exists to produce, and §G asks for it in your own words.
  }

end Exercise6SafeFoldSpec
