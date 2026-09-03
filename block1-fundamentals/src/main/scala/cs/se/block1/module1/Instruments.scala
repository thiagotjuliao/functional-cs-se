package cs.se.block1.module1

/** Exercise 3 (Easy) — the measuring instrument for the entire module.
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

  /** Cumulative bytes allocated by the *current thread* since it started.
    *
    * Monotonically non-decreasing. The absolute value is meaningless; only
    * differences between two readings carry information.
    */
  def allocatedBytes: Long = ???

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
  def measure[A](body: => A): (A, Long) = ???

/** Exercise 9 (Hard) — a minimal, honest timing harness.
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
  def consume(value: Double): Unit = ???

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
    *
    * Collecting the sample without mutation is the real exercise. Building an
    * `Array[Long]` via `Array.tabulate` keeps the code pure at the source
    * level, at the cost of one allocation *outside* the timed region — which is
    * exactly the right trade.
    */
  def medianNanos(warmup: Int, iterations: Int)(body: () => Unit): Long = ???
end Bench
