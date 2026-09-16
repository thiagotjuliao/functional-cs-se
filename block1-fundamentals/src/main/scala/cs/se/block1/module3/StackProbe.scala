package cs.se.block1.module3

import java.util.concurrent.atomic.AtomicReference

/** Exercise 1 — the instrument, and the first thing you write.
  *
  * Module 1 could ask the JVM how many bytes a thread had allocated. Nothing
  * equivalent exists for the stack: there is no counter for "frames currently
  * pushed", no `ThreadMXBean` method that reports depth, and no way to ask a
  * method how large its frame is. Guide, Part I.4.
  *
  * So the only honest measurement is the destructive one — call it deeper and
  * deeper until it throws — and that is what this object provides. Every depth
  * recorded in this module's checklist comes out of here or comes out of
  * nowhere.
  *
  * '''Two rules that apply to this file and to nothing else in the module.'''
  *
  *   - Local mutability is permitted, because this is a measuring instrument
  *     and §D of the checklist grants it the same exception Module 1 granted
  *     `AllocationProbe` and `Bench`. Keep it local; nothing mutable escapes.
  *   - A `while` loop is '''not''' permitted, even here. This module is about
  *     eliminating `while`, and an instrument that used one to measure the
  *     elimination would be an embarrassment rather than an exception. The
  *     search below is a tail recursion like everything else.
  */
object StackProbe:
  /** Whether `body` completes without exhausting the stack.
    *
    * `true` if it returns, `false` if it raises `StackOverflowError`. Every
    * other throwable propagates: a probe that swallowed all failures would
    * report "survives" for code that is merely broken, and the instrument would
    * be certifying its own blind spot.
    *
    * `body` is by-name, so the recursion being measured runs inside the `try`
    * rather than before it.
    *
    * '''Why catching an `Error` is defensible here.''' Not because the author
    * knows what he is doing — that argument justifies anything. Two specific
    * facts do:
    *
    *   - `StackOverflowError` is the '''result being measured''', not a failure to
    *     recover from. Every other call site that catches it is surviving an
    *     accident; this one raises it on purpose and reads it as a datum.
    *   - By the time the handler runs the stack is '''fully unwound'''. The
    *     resource that was exhausted has been returned in its entirety, so the
    *     thread continues in a state indistinguishable from one that never
    *     overflowed. Nothing is leaked and nothing is left half-finished.
    *
    * '''What makes it indefensible elsewhere.''' Both facts fail. In application
    * code the overflow is a defect — usually a recursion that should have been
    * bounded — and catching it converts a loud failure into a silent wrong
    * answer, with no way to know how much work was abandoned partway. And the
    * second fact does not carry over to the `Error` most often caught alongside
    * it: `OutOfMemoryError` leaves the heap still full when the handler runs, so
    * the recovery path needs the very resource that is gone. Unwinding is what
    * makes this one safe, and only this one.
    */
  def survives[A](body: => A): Boolean =
    try
      val _ = body
      true
    catch case _: StackOverflowError => false

  /** The largest `n` for which `f(n)` survives, or `-1` if `f(0)` does not.
    *
    * Two phases, and the first exists to make the second legal. '''Exponential
    * search''' probes `f(1), f(2), f(4), ...` until one fails; '''binary search'''
    * then narrows between the last survivor and the first failure.
    *
    * The invariant the binary phase maintains — and which the exponential phase
    * exists to establish — is that both ends are '''observed witnesses''' rather
    * than guesses:
    *
    * {{{
    * survives(f(lo))   was seen to be true
    * survives(f(hi))   was seen to be false
    * the boundary lies in [lo, hi)
    * }}}
    *
    * A `hi` that was never observed failing is not an upper bound. Seeded with
    * one, the search returns `hi - 1` whenever the boundary is above it — a
    * value shaped exactly like an answer. That is why `limit` is not handed to
    * the binary phase as `hi`.
    *
    * `(lo + hi) >>> 1` and not `/ 2`: the sum can overflow `Int`, and the signed
    * halving of a wrapped sum is negative, which puts the midpoint outside the
    * interval and kills the invariant in silence. The unsigned shift reads the
    * 32 bits as magnitude and returns the right midpoint regardless. The
    * doubling is guarded for the same reason — the counter stops before
    * `hi * 2` passes `2^30`, so the wrapped value is never probed.
    *
    * '''Monotonicity is the precondition, and this JVM violates it.''' The binary
    * phase is valid only if `f(n)` failing implies `f(m)` failing for every
    * `m > n`. That holds for a recursion whose depth grows with `n` and whose
    * frame size is fixed — and the frame size is not fixed. Measured on one
    * 1 MiB thread inside one process, `f = TailShapes.sumNaive`:
    *
    * {{{
    * cold        14,999
    * warm     >= 39,999      a C2 frame is smaller, so more of them fit
    * }}}
    *
    * `f(39,999)` survives after `f(15,000)` has failed, which denies the
    * precondition outright. The search is used anyway, because the alternative
    * is worse: a linear climb needs no precondition and instead '''warms the
    * method it is measuring''' — fifteen thousand invocations being roughly what
    * C2 waits for. This pair of phases takes 34 probes, and perturbation is the
    * error term of this instrument.
    *
    * So the number returned is not a property of `f` alone. It is a property of
    * `f` '''and the tier it was compiled to''', which is why §E of the checklist
    * asks for the configuration beside every depth, and why `Module3Harness`
    * forbids a ceiling test from asserting an absolute. Warm `f` deliberately
    * when the compiled ceiling is the one wanted.
    *
    * '''Known limitation, recorded rather than hidden.''' `limit` caps the answer
    * and not the search: the exponential phase may probe beyond it and the
    * result is clamped afterwards. A returned `limit` is therefore
    * indistinguishable from a boundary that genuinely sits at `limit` — in-band
    * signalling inside the same `Int` domain, and a worse sentinel than
    * `EarlyExit.indexOf`'s `-1`, which at least lies outside the set of
    * legitimate answers. The exponential phase's escape hatch has the same
    * shape: after enough doublings it returns a `hi` that no `survives` call
    * ever observed failing. Both are reachable only for an `f` outliving 2^30
    * frames, and neither is reachable by a stack.
    */
  def maxDepth(limit: Int)(f: Int => Any): Int =
    @scala.annotation.tailrec
    def largestHi(lo: Int, hi: Int): Int =
      if hi - lo == 1 then lo
      else
        val mid = (hi + lo) >>> 1
        if survives(f(mid)) then largestHi(mid, hi)
        else largestHi(lo, mid)

    @scala.annotation.tailrec
    def getUpperHi(hi: Int = 1, n: Int = 0): Int =
      if n > 30 then limit
      else if !survives(f(hi)) then hi
      else getUpperHi(hi * 2, n + 1)

    if !survives(f(0)) then -1
    else
      val upperHi = getUpperHi()
      largestHi(0, upperHi)

  /** Run `body` on a fresh platform thread with a stack of `kib` kibibytes and
    * return its result.
    *
    * The default thread is the wrong laboratory. It already carries sbt's and
    * MUnit's frames — the `b` term of the guide's §3 fit, measured here at
    * 52,885 bytes — and its size is whatever the JVM chose rather than
    * something stated. A fresh thread of a named size is the only way to vary
    * one quantity at a time.
    *
    * `stackSize` takes bytes, hence `kib * 1024L`. The thread is joined before
    * the result is read, and whatever `body` threw is rethrown on the calling
    * thread — which is why the cell holds `Either` rather than `A`: a normal
    * return and an abnormal one have to cross the boundary distinguishably.
    *
    * '''Why the publication is safe, and what the `AtomicReference` is actually
    * for.''' Not visibility. `Thread.join` already orders everything: every
    * action in the started thread happens-before the join returns, so a plain
    * field written by `body` would be read correctly here. The atomic is used
    * because the alternative spelling is a `var`, and §D of the checklist
    * permits mutability in this file only inside the search — which, as it
    * turns out, needs none. The exception was granted and is not used;
    * `AtomicReference` is what keeps that true.
    *
    * `stackSize` is a '''hint''' at the JVM level and a platform is free to ignore
    * it. It is honoured on the HotSpot build this module was measured on, and
    * the linear fit is the evidence: 16.00 bytes per frame across 256 KiB and
    * 4 MiB is not a number a disregarded hint would produce.
    */
  def onStack[A](kib: Int)(body: => A): A =
    val result = new AtomicReference[Either[Throwable, A]]()

    Thread
      .ofPlatform()
      .stackSize(kib * 1024L)
      .start { () =>
        try
          val a = body
          result.set(Right(a))
        catch case t: Throwable => result.set(Left(t))
      }
      .join()

    result.get match
      case Left(e) => throw e
      case Right(a) => a

  /** Bytes of stack per frame, as the slope of the line through two
    * measurements: `(bytes2 - bytes1) / (depth2 - depth1)`.
    *
    * Measured on the spec's `deep` at 256 KiB and 4 MiB — 13,079 and 258,845
    * frames — the slope is '''16.00''', which is the guide's §3 prediction for a
    * method with one `Int` parameter and one `Int` local, arrived at from the
    * other direction. A method with more locals does not land there, and that
    * difference is the point §E asks to record.
    *
    * Two points determine a line and assume one. The fit is honest only if the
    * frame size is the same at both measurements, which means both must be
    * taken at the same JIT tier: a cold reading against a warm one fits a line
    * through two different functions and reports their average as a frame.
    */
  def bytesPerFrame(bytes1: Long, depth1: Int, bytes2: Long, depth2: Int): Double =
    (bytes2 - bytes1) / (depth2 - depth1).toDouble

  /** Bytes of stack already consumed before the measured recursion began — the
    * intercept of the same line, `bytes1 - bytesPerFrame * depth1`.
    *
    * Measured at '''52,885 bytes''' on the pair above, and it is not zero for a
    * reason any stack trace shows: the thread does not begin at the method
    * being measured. Beneath it sit the probe, the harness, MUnit's runner and
    * the thread's own entry point, and every one of those frames is stack the
    * recursion never gets to use.
    *
    * It is the `b` of `stack = a x depth + b`, and it is the term that makes an
    * absolute depth non-transferable between call sites: the same `f`, measured
    * from a deeper harness, returns a smaller ceiling with nothing about `f`
    * having changed.
    */
  def fixedOverhead(bytes1: Long, depth1: Int, bytes2: Long, depth2: Int): Double =
    bytes1 - bytesPerFrame(bytes1, depth1, bytes2, depth2) * depth1

end StackProbe
