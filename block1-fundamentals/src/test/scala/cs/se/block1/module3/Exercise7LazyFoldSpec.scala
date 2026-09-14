package cs.se.block1.module3

import cs.se.block1.module2.MyList
import cs.se.block1.module2.MyList.*
import java.util.concurrent.atomic.AtomicInteger

/** Exercise 7 — the ceiling laziness removes, and the one it leaves.
  *
  * The interesting assertions here are the two that pull in opposite
  * directions: `existsLazy` must survive a million when it can stop early, and
  * `foldRightLazy` must still overflow when `f` always forces. A fix that fixed
  * both would not be laziness, and a fix that fixed neither would be nothing.
  */
class Exercise7LazyFoldSpec extends Module3Harness:

  test("foldRightLazy is a right fold") {
    val xs = MyList(1, 2, 3)
    assertEquals(LazyFold.foldRightLazy(xs, 0)((a, b) => a - b), 2, "1 - (2 - (3 - 0))")
    assertEquals(
      LazyFold.foldRightLazy(xs, "z")((a, b) => s"($a$b)"),
      "(1(2(3z)))",
      "the nesting is to the right"
    )
    assertEquals(
      LazyFold.foldRightLazy(MyList[Int](), 7)((a, b) => a + b),
      7,
      "the empty fold is z"
    )

    List(0, 1, 7, 100, 5_000).foreach { n =>
      val l = cells(n)
      assertEquals(LazyFold.foldRightLazy(l, 0)((a, b) => a - b), l.foldRight(0)(_ - _), s"n = $n")
    }
  }

  test("a strict f keeps the ceiling, and that is the honest result") {
    val ceiling = maxSurviving(1 << 18) { n =>
      LazyFold.foldRightLazy(cells(n), 0L)((a, b) => a + b)
    }
    report("largest n that foldRightLazy survives with a strict f", ceiling)
    assert(
      ceiling < (1 << 18),
      "an f that always forces its second argument must still build a frame per element. If this " +
        "has no ceiling, the by-name parameter is not what is being measured"
    )
  }

  test("the second argument is not evaluated when f ignores it") {
    val forced = AtomicInteger(0)
    val r = LazyFold.foldRightLazy(cells(1_000), 0)((a, b) =>
      if a == 0 then -1
      else
        forced.incrementAndGet(); b
    )
    assertEquals(r, -1, "the first element decided the answer")
    assertEquals(forced.get, 0, "nothing beyond the first element should have been forced")
  }

  test("existsLazy stops at the first hit") {
    val counted = AtomicInteger(0)
    def p(target: Int)(x: Int): Boolean =
      counted.incrementAndGet(); x == target

    val big = cells(1_000_000)

    counted.set(0)
    assert(LazyFold.existsLazy(big, p(3)), "3 is present")
    report("elements visited to find index 3 of 1,000,000", counted.get)
    assertEquals(counted.get, 4, "0, 1, 2, 3 - and then nothing")

    counted.set(0)
    assert(!LazyFold.existsLazy(big, p(-1)), "-1 is absent")
    report("elements visited with no match", counted.get)
    assertEquals(counted.get, 1_000_000, "with nothing to stop at, it visits everything")
  }

  test("existsLazy survives the size at which a strict foldRight does not") {
    val big = cells(1_000_000)
    assert(
      survives(LazyFold.existsLazy(big, (x: Int) => x == 3)),
      "stopping at element 3 must cost 4 frames, whatever the list's length"
    )
    assert(
      !survives(big.foldRight(false)((x, acc) => x == 3 || acc)),
      "the strict foldRight over the same list must not survive - if it does, the comparison is " +
        "not measuring what it claims"
    )
    report(
      "existsLazy bytes to find index 3 of 1,000,000",
      probeBytes(LazyFold.existsLazy(big, (x: Int) => x == 3))
    )
  }

end Exercise7LazyFoldSpec
