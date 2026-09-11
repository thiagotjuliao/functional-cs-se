package cs.se.block1.module2

import cs.se.block1.module1.AllocationProbe
import scala.concurrent.duration.*
import scala.util.Random

/** Shared harness for the Module 2 suites — Manual Persistent Data Structures.
  *
  * The suites carry three kinds of test, and the distinction decides how tight
  * each assertion is allowed to be:
  *
  *   - **Correctness** tests assert mathematical facts about your structure.
  *     Exact, and they must pass on any JVM at any load.
  *   - **Model** tests assert that Exercise 1's arithmetic agrees with itself
  *     and with Module 1's `Footprint`. Also exact: it is arithmetic.
  *   - **Measurement** tests compare the model against `AllocationProbe`. These
  *     are experiments. Their thresholds are ratios and tolerances, never
  *     absolute byte counts, because an absolute threshold encodes this
  *     machine's constants into a test that has to pass on someone else's.
  *
  * One suite per exercise, so that `testOnly *Exercise5*` runs exactly one and
  * the IDE offers one run action per exercise.
  *
  * Note the ordering dependency, which is deliberate: `Exercise1SharingSpec`
  * needs nothing but arithmetic and can go green before any data structure
  * exists. Every other suite needs `MyList.apply` from Exercise 2.
  */
abstract class Module2Harness extends munit.FunSuite:

  override val munitTimeout: Duration = 180.seconds

  protected val Seed = 20260911L

  /** Bytes allocated while evaluating `body`, straight from the Module 1
    * instrument, with no floor subtracted. Subtracting the floor is Exercise 8's
    * job and its Scaladoc explains which floor applies.
    */
  protected def probeBytes[A](body: => A): Long = AllocationProbe.measure(body)._2

  /** Run `body` enough times for C2 to compile and optimise it. */
  protected def warmup[A](times: Int)(body: => A): Unit =
    (1 to times).foreach(_ => body)

  /** A shuffled permutation of `0 until n`, from a fixed seed so that a failure
    * is reproducible.
    */
  protected def shuffled(n: Int): scala.List[Int] =
    Random(Seed).shuffle((0 until n).toList)

  protected def report(label: String, value: Any): Unit =
    println(s"  [observation] $label = ${value.toString}")

  /** Assert that `actual` is within `tolerance` (as a fraction) of `expected`.
    * For ratios, where an exact equality would be asserting the machine rather
    * than the model.
    */
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

end Module2Harness
