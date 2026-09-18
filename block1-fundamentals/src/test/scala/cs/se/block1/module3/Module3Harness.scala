package cs.se.block1.module3

import cs.se.block1.module1.AllocationProbe
import cs.se.block1.module2.MyList
import scala.concurrent.duration.*

/** Shared harness for the Module 3 suites — Stack Optimization & Control Flow
  * Elimination.
  *
  * The suites carry three kinds of test, and the distinction decides how tight
  * each assertion is allowed to be:
  *
  *   - **Correctness** tests assert values. Exact, and they must pass anywhere.
  *   - **Shape** tests assert that a transformation preserved a value while
  *     changing a cost. Exact on the value, never on the cost.
  *   - **Ceiling** tests assert that one implementation survives an input
  *     another cannot. These are experiments, and they may **never** assert an
  *     absolute depth: the guide's §23 measured a 40x range across stack sizes
  *     on one machine, so an absolute is a test of `-Xss`. Assert the
  *     *separation*, and let `report` print the number.
  *
  * One suite per exercise. `Exercise1StackProbeSpec` needs nothing but its own
  * exercise; every other suite needs `StackProbe` and is unrunnable until it
  * exists — which is why the checklist says to build it first.
  */
abstract class Module3Harness extends munit.FunSuite:

  override val munitTimeout: Duration = 300.seconds

  /** Whether `body` completes without exhausting the stack.
    *
    * Deliberately duplicated from `StackProbe.survives` rather than delegated:
    * `Exercise1StackProbeSpec` has to test that function, and a suite that
    * tested it through itself would pass for any implementation at all. Every
    * other suite may use either.
    */
  protected def survives[A](body: => A): Boolean =
    try
      val _ = body; true
    catch case _: StackOverflowError => false

  /** Depth and round count for the warm-up below.
    *
    * `WarmDepth` must be deep enough that `f` reaches its recursive body and
    * shallow enough that it cannot overflow on any `-Xss`; the product with
    * `WarmRounds` is what has to clear C2's compilation threshold, and 25,600
    * invocations clears it with room to spare.
    */
  private val WarmDepth = 128
  private val WarmRounds = 200

  /** The largest `n` in `[0, limit)` for which `f(n)` survives, by binary
    * search, assuming survival is monotone in `n`.
    *
    * '''The warm-up is not an optimisation and removing it changes the
    * answer.''' A compiled frame is smaller than an interpreted one — §23
    * measured 2.51x — so a search entered cold converges while `f` is still
    * migrating between the two tiers, and returns a number that was true for
    * part of the search and false for the rest.
    *
    * The cost of omitting it is not theoretical. `MyList.foldRight` was
    * measured by two specs of this suite, in one forked JVM, as 16,895 and
    * 30,862: E6's own search left the method compiled and E7 then measured the
    * compiled frame. Run either spec alone and both report 16,895. Neither
    * number is wrong; neither is a property of `foldRight` alone.
    *
    * This mirrors `probeBytes` below, which has warmed its subject from the
    * start. The asymmetry — warm-up discipline for allocation, none for
    * depth — is occurrence 4 of pattern 13 in `error-patterns.md`, and
    * challenge 41 of `challenge-log.md` carries the measurements.
    *
    * It belongs here rather than at the call sites: the defect was the
    * instrument's, and fixing it in one spec would have left the others wrong.
    */
  protected def maxSurviving(limit: Int)(f: Int => Any): Int =
    (0 until WarmRounds).foreach(_ => survives(f(WarmDepth)): Unit)

    @annotation.tailrec
    def loop(lo: Int, hi: Int): Int =
      if lo >= hi - 1 then lo
      else
        val mid = (lo + hi) >>> 1
        if survives(f(mid)) then loop(mid, hi) else loop(lo, mid)
    loop(0, limit)

  /** Bytes allocated while evaluating `body`, after warm-up. */
  protected def probeBytes[A](body: => A): Long =
    (0 until 20).foreach(_ => body)
    AllocationProbe.measure(body)._2

  /** The list `0 until n`, built without depending on any Module 3 exercise. */
  protected def cells(n: Int): MyList[Int] =
    @annotation.tailrec
    def loop(i: Int, acc: MyList[Int]): MyList[Int] =
      if i < 0 then acc else loop(i - 1, MyList.Cons(i, acc))
    loop(n - 1, MyList.Nil)

  protected def report(label: String, value: Any): Unit =
    println(s"  [observation] $label = ${value.toString}")

  /** Assert that `actual` is within `tolerance` (as a fraction) of `expected`. */
  protected def assertRatio(
      actual: Double,
      expected: Double,
      tolerance: Double,
      clue: String
  ): Unit =
    val low = expected * (1.0 - tolerance)
    val high = expected * (1.0 + tolerance)
    assert(
      actual >= low && actual <= high,
      s"$clue: expected about $expected (within ${tolerance * 100}%), got $actual"
    )

end Module3Harness
