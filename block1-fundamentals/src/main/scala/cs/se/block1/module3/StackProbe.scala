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
    * `true` if it returns, `false` if it throws `StackOverflowError`. Any other
    * throwable propagates — a probe that swallowed every failure would report
    * "survives" for code that is simply broken.
    *
    * This is the one place in the module that catches an `Error`, and the
    * Scaladoc you write here must say why that is defensible. The argument is
    * not "we know what we are doing"; it is that `StackOverflowError` is the
    * *result being measured* rather than a failure to recover from, and that
    * the stack is fully unwound by the time the handler runs. Say it in your
    * own words, and say what would make it indefensible elsewhere.
    */
  def survives[A](body: => A): Boolean =
    try
      val _ = body
      true
    catch case _: StackOverflowError => false

  /** The largest `n` in `[0, limit]` for which `f(n)` survives.
    *
    * Binary search, and the precondition that makes it valid is worth stating
    * before you write it: survival must be '''monotone''' in `n` — if `f(n)`
    * overflows then `f(m)` overflows for every `m > n`. That holds for a
    * recursion whose depth grows with `n` and fails for anything that allocates
    * differently at different sizes. Document the assumption; the spec tests a
    * function that satisfies it.
    *
    * Must be a tail recursion. `limit` is the exclusive upper bound of the
    * search, not a claim about the answer.
    *
    * Returns `-1` if `f(0)` itself does not survive.
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
    * The default thread is the wrong laboratory: it already carries sbt's and
    * MUnit's frames, which is the `b` term of the guide's §3 fit, and its size
    * is whatever the JVM chose. A fresh thread of a stated size is the only way
    * to vary one quantity at a time.
    *
    * `Thread.ofPlatform().stackSize(...)` takes bytes. The thread must be
    * joined before the result is read, and the result must cross the thread
    * boundary safely — which in a pure module is the one place a mutable cell
    * is unavoidable. Use the narrowest one that works and say why it is safe.
    *
    * Propagates whatever `body` throws, on the calling thread.
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

  /** Bytes of stack per frame, fitted from two measurements.
    *
    * Given depths measured at two different stack sizes, the slope of the line
    * through them: `(bytes2 - bytes1) / (depth2 - depth1)`.
    *
    * The guide's §3 fit gives 16.00 for a method with one `Int` parameter and
    * one `Int` local. Yours should land there for the same method and should
    * '''not''' for a method with more locals — that difference is the point,
    * and §E asks you to record both.
    */
  def bytesPerFrame(bytes1: Long, depth1: Int, bytes2: Long, depth2: Int): Double =
    (bytes2 - bytes1) / (depth2 - depth1).toDouble

  /** Bytes of stack already consumed before the measured recursion began.
    *
    * The intercept of the same line: `bytes1 - bytesPerFrame * depth1`.
    *
    * It is not zero, and §E asks you to say why in one line. The answer is
    * visible in a stack trace.
    */
  def fixedOverhead(bytes1: Long, depth1: Int, bytes2: Long, depth2: Int): Double =
    bytes1 - bytesPerFrame(bytes1, depth1, bytes2, depth2) * depth1

end StackProbe
