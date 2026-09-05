package cs.se.annex.a1

import scala.concurrent.duration.*
import scala.util.Random

/** Shared harness for the Annex A1 suites — Bitwise Arithmetic & Binary
  * Representation.
  *
  * Bit-level code fails at its boundaries, never in its middle. An
  * implementation that is wrong only for `Int.MinValue`, only for `0`, or only
  * for negative inputs will pass any suite built from randomly sampled
  * "reasonable" numbers, and will then fail in production on the one value the
  * sampler never produced.
  *
  * Every suite in this annex therefore tests over `Corners ++ random samples`,
  * never over random samples alone. `Corners` is the list of values that have
  * historically broken each of these algorithms, and adding to it when you find
  * a new one is part of the exercise.
  *
  * The oracles are the JDK's own intrinsics. Comparing against
  * `Integer.bitCount` rather than against a second hand-written implementation
  * is deliberate: an independent, heavily exercised reference cannot repeat your
  * own misunderstanding back to you.
  *
  * One suite per exercise: `testOnly *Exercise4*` runs exactly one exercise.
  */
abstract class AnnexA1Harness extends munit.FunSuite:

  override val munitTimeout: Duration = 120.seconds

  protected val Seed = 20260904L

  /** The values that break bit algorithms. Every property in this annex is
    * asserted over these before it is asserted over anything random.
    *
    *   - `0` — no set bits; the base case of every scanning algorithm.
    *   - `-1` — all set bits; the identity of `&`, the complement of `0`.
    *   - `Int.MinValue` — the single set bit in the sign position, and the only
    *     value equal to its own negation.
    *   - `Int.MaxValue` — every bit set except the sign.
    *   - the powers of two and their neighbours — the boundaries of `log2` and
    *     of every rounding operation.
    */
  protected val Corners: List[Int] =
    val powers = (0 to 31).map(1 << _).toList
    val neighbours = powers.flatMap(p => List(p - 1, p, p + 1))
    (List(0, 1, -1, 2, -2, 7, -7, Int.MinValue, Int.MaxValue, Int.MinValue + 1) ++
      neighbours ++ powers.map(-_)).distinct

  /** Uniformly distributed over the whole 32-bit space, not over "small
    * numbers": `Random.nextInt()` draws from all `2^32` patterns, so roughly
    * half the samples are negative and the high bits carry real entropy.
    */
  protected def randomInts(n: Int, seed: Long = Seed): List[Int] =
    val rng = Random(seed)
    List.fill(n)(rng.nextInt())

  protected def randomLongs(n: Int, seed: Long = Seed): List[Long] =
    val rng = Random(seed)
    List.fill(n)(rng.nextLong())

  /** `Corners` followed by `n` random samples — the standard domain for every
    * universally quantified claim in this annex.
    */
  protected def domain(n: Int, seed: Long = Seed): List[Int] =
    Corners ++ randomInts(n, seed)

  /** The 32-character, zero-padded binary rendering, used only to make failure
    * messages readable. Independent of Exercise 1 on purpose: a broken
    * implementation must not be able to disguise its own failure output.
    */
  protected def bin(x: Int): String =
    val raw = java.lang.Integer.toBinaryString(x)
    ("0" * (32 - raw.length)) + raw

  protected def bin64(x: Long): String =
    val raw = java.lang.Long.toBinaryString(x)
    ("0" * (64 - raw.length)) + raw

  /** Median nanoseconds per call, plus a blackhole value.
    *
    * The median rather than the mean, because a single GC pause or a scheduler
    * preemption skews a mean and leaves a median untouched.
    *
    * The returned `Int` is a running hash of every result produced. Consume it —
    * print it, assert on it — or C2 will observe that nothing depends on the
    * loop's output and delete the code you were trying to measure. Dead-code
    * elimination is the reason naive JVM microbenchmarks report impossible
    * speeds. A hash rather than an XOR because an XOR over a run of identical
    * values collapses to zero, which looks like a broken instrument.
    *
    * This is a *feel the difference* instrument, not a benchmark. It has no
    * warmup control, no fork, no statistical treatment; JMH exists precisely
    * because doing this honestly is hard. No test may assert on its output.
    */
  protected def medianNanos(iterations: Int)(body: => Int): (Long, Int) =
    val samples = (1 to iterations).map { _ =>
      val start = System.nanoTime()
      val value = body
      (System.nanoTime() - start, value)
    }
    val median = samples.map(_._1).sorted.apply(iterations / 2)
    val blackhole = samples.map(_._2).foldLeft(0)((acc, v) => (acc * 31) + v)
    (median, blackhole)

  /** Run `body` enough times for C2 to compile and optimise it. */
  protected def warmup[A](times: Int)(body: => A): Unit =
    (1 to times).foreach(_ => body)

  protected def report(label: String, value: Any): Unit =
    println(s"  [observation] $label = ${value.toString}")

end AnnexA1Harness
