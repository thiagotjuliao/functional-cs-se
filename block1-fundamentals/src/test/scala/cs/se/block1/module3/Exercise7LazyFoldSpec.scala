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

    // These sizes stay small on purpose. `f` here is strict, so this loop runs
    // at the very ceiling the next test measures — and that ceiling is both
    // lower than `MyList.foldRight`'s and unstable, because how many of the
    // three frames per element the JIT collapses depends on how warm the call
    // site is. A value check needs no depth at all: n = 512 proves the nesting
    // exactly as well as n = 5,000 would, and it proves it on any -Xss. An
    // absolute size here would be a stack-depth assertion wearing a
    // correctness test's clothes, which the harness forbids.
    List(0, 1, 7, 100, 512).foreach { n =>
      val l = cells(n)
      assertEquals(LazyFold.foldRightLazy(l, 0)((a, b) => a - b), l.foldRight(0)(_ - _), s"n = $n")
    }
  }

  test("a strict f keeps the ceiling, and that is the honest result") {
    val ceiling = maxSurviving(1 << 18) { n =>
      LazyFold.foldRightLazy(cells(n), 0L)((a, b) => a + b)
    }
    val strict = maxSurviving(1 << 18)(n => cells(n).foldRight(0L)((a, b) => a + b))
    report("largest n that foldRightLazy survives with a strict f", ceiling)
    report("largest n that MyList.foldRight survives, for comparison", strict)
    report("frames per element, lazy against strict", strict.toDouble / ceiling)
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

    // The no-match case runs on a *small* list, and the size is the lesson.
    //
    // With nothing to stop at, `||` forces its right operand at every step and
    // the recursion descends the whole list, so this path has exactly the
    // ceiling the previous test measured. Asking it to visit a million and
    // report the count is asking the early exit to rescue the one case that
    // has no early exit in it — unsatisfiable for any correct `existsLazy`,
    // and a red suite that would have looked like a defect in the exercise.
    // Laziness buys the hit, never the miss.
    val short = cells(512)
    counted.set(0)
    assert(!LazyFold.existsLazy(short, p(-1)), "-1 is absent")
    report("elements visited with no match", counted.get)
    assertEquals(counted.get, 512, "with nothing to stop at, it visits everything")
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
