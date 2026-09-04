package cs.se.block1.module1

import scala.concurrent.duration.*
import scala.util.Random

/** Shared harness for the Module 1 suites — JVM Semantics & Immutability
  * Allocation Stress.
  *
  * The suites carry two kinds of tests, and the distinction matters:
  *
  *   - **Correctness tests** assert mathematical facts. They must pass on any
  *     JVM, on any machine, at any load.
  *   - **Allocation tests** assert facts about *this* JVM's optimiser. They are
  *     experiments, and their thresholds are deliberately loose so that they
  *     fail only when the underlying claim is false — not when the machine is
  *     busy. Re-running the suites with `-XX:-DoEscapeAnalysis` must flip
  *     `sumNorms is scalar-replaced` to red. That flip is the result.
  *
  * No timing test asserts an absolute duration. A test that fails because a
  * laptop throttled is not a test, it is a coin toss.
  *
  * One suite per exercise: MUnit has no nesting construct, so the *class* is
  * the unit of grouping. `testOnly *Exercise4*` runs exactly one exercise, and
  * the IDE offers a single run action per exercise rather than per assertion.
  */
abstract class Module1Harness extends munit.FunSuite:

  override val munitTimeout: Duration = 120.seconds

  protected val Seed = 20260903L
  protected val Tolerance = 1e-9

  /** Bytes allocated while evaluating `body`, discarding its value. */
  protected def bytesOf[A](body: => A): Long = AllocationProbe.measure(body)._2

  /** Run `body` enough times for C2 to compile and optimise it. */
  protected def warmup[A](times: Int)(body: => A): Unit =
    (1 to times).foreach(_ => body)

  /** Integer-valued doubles: exact under IEEE-754, so associativity laws can be
    * asserted without a tolerance hiding a real defect.
    */
  protected def exactDoubles(rng: Random, n: Int): Array[Double] =
    Array.fill(n)(rng.between(-1000, 1000).toDouble)

  protected def report(label: String, value: Any): Unit =
    println(s"  [observation] $label = ${value.toString}")

end Module1Harness
