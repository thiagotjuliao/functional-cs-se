package cs.se.block1.module1

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory

/** Exercise 3 — the measuring instrument for the entire module.
  *
  * Implement this one first: Exercises 4, 5 and 8 are validated through it.
  *
  * This object is one of the two deliberate exceptions to the purity rule. It
  * reads the JVM's own instrumentation, so it is by definition impure — a
  * thermometer is allowed to touch the patient. Every *other* object in this
  * module must remain referentially transparent.
  *
  * The instrument you need is HotSpot-specific:
  * {{{
  * import com.sun.management.ThreadMXBean          // note: com.sun, not java.lang
  * import java.lang.management.ManagementFactory
  * }}}
  * `ManagementFactory.getThreadMXBean` returns the `java.lang` interface; the
  * allocation counter lives on the `com.sun` sub-interface, so a cast is
  * required. Resolve the bean **once**, at object initialisation — resolving it
  * per call would make the instrument allocate more than the code it measures,
  * which is the classic way to build a lying benchmark.
  */
object AllocationProbe:

  /** The HotSpot-specific bean, resolved once at object initialisation.
    *
    * Three preconditions this `ThreadMXBean` type does not express, all of them
    * recorded with their evidence in `docs/challenge-log.md`, entry 4:
    *
    *   - **The JVM must be forked.** Type identity here is the pair *(binary
    *     name, defining loader)*, and a REPL or a layered sbt classloader
    *     supplies its own `com.sun.management.ThreadMXBean`, so the cast below
    *     compares two distinct interfaces that share a name and fails with a
    *     `ClassCastException`. `build.sbt` sets `Test / fork := true`, which
    *     makes every measurement in this module possible as a side effect of a
    *     setting written for an unrelated reason.
    *   - **It counts the calling thread only.** Allocation on any other thread —
    *     a `Future`, an `ExecutorService`, `.par`, and from Block 3 onwards a
    *     virtual thread — is invisible to it. A body that allocates hundreds of
    *     megabytes off-thread measures as a few hundred bytes, with no defect
    *     anywhere.
    *   - **It can report `-1`.** `getCurrentThreadAllocatedBytes` returns `-1`
    *     when per-thread allocation measurement is disabled, which
    *     `setThreadAllocatedMemoryEnabled(false)` can do at runtime. Disabled
    *     between the two readings of `measure`, it yields a large negative
    *     delta. Neither this object nor its callers check
    *     `isThreadAllocatedMemoryEnabled`.
    */
  val bean: ThreadMXBean = ManagementFactory.getThreadMXBean.asInstanceOf[ThreadMXBean]

  /** Cumulative bytes allocated by the *current thread* since it started.
    *
    * Monotonically non-decreasing. The absolute value is meaningless; only
    * differences between two readings carry information.
    */
  def allocatedBytes: Long =
    bean.getCurrentThreadAllocatedBytes

  /** Evaluate `body` and report both its result and the bytes it allocated.
    *
    * Contract:
    *   - the returned byte count must never be negative;
    *   - the measurement itself allocates (a tuple, and boxing of the `Long`),
    *     so the floor of this instrument is tens of bytes, not zero — every
    *     assertion built on it must carry a tolerance;
    *   - `body` is by-name, so it must be evaluated exactly once, *between* the
    *     two readings. Evaluating it before the first reading silently returns
    *     zero and is the single most likely bug in this exercise.
    */
  def measure[A](body: => A): (A, Long) =
    val firstReading = allocatedBytes
    val value = body
    val measureResult = allocatedBytes - firstReading
    (value, measureResult)
end AllocationProbe

/** Exercise 9 — a minimal, honest timing harness.
  *
  * Before writing a single line here, watch Shipilëv's *Nanotrusting the
  * Nanotime*. This exercise exists so that you feel, first-hand, the three
  * problems JMH was built to solve:
  *
  *   1. **Warmup.** The first thousands of iterations run interpreted, then
  *      C1-compiled, then C2-compiled. Timing them together measures the
  *      compiler, not the code.
  *   2. **Dead code elimination.** If a computed value is never observed, C2
  *      is entitled to delete the computation that produced it. Your benchmark
  *      then measures an empty loop and reports a spectacular result.
  *   3. **Distribution.** The mean of a timing sample is dominated by GC pauses
  *      and scheduler noise. The median is the honest statistic.
  */
object Bench:

  /** Force the JVM to treat `value` as observed, defeating dead code
    * elimination.
    *
    * Contract:
    *   - must not allocate;
    *   - must not perform I/O on any realistic input;
    *   - must not use `var`, a mutable field, or any mutable collection;
    *   - C2 must be unable to prove the call is a no-op.
    *
    * Hint: the JIT can delete code it can prove unreachable, but it cannot
    * evaluate a data-dependent predicate at compile time. Guard a genuine side
    * effect behind a condition on `value` that is astronomically unlikely to
    * hold, and the compiler is forced to keep the computation that produces
    * `value` alive.
    */
  def consume(value: Double): Unit =
    if value == Double.MinValue then println(value)

  /** Run `body` `warmup` times, discard those timings, then run it
    * `iterations` times and return the **median** wall-clock nanoseconds per
    * iteration.
    *
    * Contract:
    *   - use `System.nanoTime()`, never `currentTimeMillis` — the latter is a
    *     wall clock subject to NTP correction, not a monotonic timer;
    *   - the timed region must not allocate beyond what `body` itself
    *     allocates, so no per-iteration boxing into a `List[Long]`;
    *   - the returned value must be strictly positive for any non-trivial
    *     `body`;
    *   - `iterations` must be at least 1; document what you do when it is not,
    *     and encode that decision in the type if you can.
    *    - `iterations` values less than 1 will produce a -1L default value.
    *
    * Collecting the sample without mutation is the real exercise. Building an
    * `Array[Long]` via `Array.tabulate` keeps the code pure at the source
    * level, at the cost of one allocation *outside* the timed region — which is
    * exactly the right trade.
    */
  def medianNanos(warmup: Int, iterations: Int)(body: () => Unit): Long =
    def timedRuns(n: Int): Array[Long] =
      Array.tabulate(n) { _ =>
        val start = System.nanoTime()
        body()
        System.nanoTime() - start
      }

    if iterations < 1 then -1L
    else
      (1 to warmup).foreach(_ => body()) // warmup
      val sorted = timedRuns(iterations).sorted

      if iterations % 2 != 0 then sorted(iterations / 2)
      else sorted.slice(iterations / 2 - 1, iterations / 2 + 1).sum / 2
end Bench
