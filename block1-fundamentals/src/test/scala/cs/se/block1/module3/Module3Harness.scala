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

  /** The largest `n` in `[0, limit)` for which `f(n)` survives, by binary
    * search, assuming survival is monotone in `n`.
    */
  protected def maxSurviving(limit: Int)(f: Int => Any): Int =
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
