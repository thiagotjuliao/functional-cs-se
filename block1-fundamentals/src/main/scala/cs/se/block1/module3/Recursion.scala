package cs.se.block1.module3

import cs.se.block1.module2.MyList

/** Exercise 3 — tail recursion where the accumulator is not a running total.
  *
  * Every function in Exercise 2 accumulated a sum, which makes the accumulator
  * look like a synonym for "the answer so far". It is not. An accumulator is
  * '''whatever the frames were remembering''', and these four remember four
  * different things. Guide, Part II.8.
  *
  * All four must be `@tailrec` and all four must survive inputs that would
  * overflow a naive version.
  */
object Arithmetic:

  /** Greatest common divisor by the Euclidean algorithm.
    *
    * `gcd(a, 0) == a` and `gcd(a, b) == gcd(b, a % b)`.
    *
    * This one is already tail recursive if you write it the obvious way, which
    * makes it the interesting case: nothing was accumulated at all. Say in the
    * Scaladoc what plays the accumulator's role here, and why the answer is
    * "the parameters themselves".
    *
    * Total for every `Int` pair? Decide, document, and defend the boundary.
    * `Int.MinValue` is where this kind of function usually breaks; Module 1's
    * pattern 6 is the entry to re-read before claiming totality.
    */
  @scala.annotation.tailrec
  def gcd(a: Int, b: Int): Int =
    if b == 0 then a
    else gcd(b, a % b)

  /** `base` raised to `exp`, by repeated squaring.
    *
    * `power(2, 10) == 1024`, `power(x, 0) == 1`.
    *
    * The naive version multiplies `exp` times and recurses `exp` deep. Squaring
    * makes the recursion `log2(exp)` deep, which means this function would be
    * safe even '''without''' `@tailrec` — and the exercise is to annotate it
    * anyway and say why the annotation still earns its place. Part VI.22 is the
    * argument.
    *
    * Returns `1L` for `exp <= 0`.
    */
  def power(base: Long, exp: Int): Long = powerAcc(base, exp)

  @scala.annotation.tailrec
  private def powerAcc(base: Long, exp: Int, acc: Long = 1L): Long =
    if exp <= 0 then acc
    else if exp % 2 == 0 then powerAcc(base * base, exp / 2, acc)
    else powerAcc(base * base, exp / 2, acc * base)

  /** The decimal digits of `n`, most significant first.
    *
    * `digits(1024) == MyList(1, 0, 2, 4)`, `digits(0) == MyList(0)`.
    *
    * Here the accumulator is a '''structure''', not a number, and it is built
    * in the order that makes the recursion tail — which is not the order the
    * answer is wanted in. Resolve that without a second traversal if you can,
    * and if you cannot, say what the second traversal costs. Module 2, §12.
    *
    * Negative `n`: the sign is discarded and the digits of `|n|` are returned,
    * so the function is total over every `Int`. The law is therefore
    * `digits(n)` reassembles to `|n|`, '''not''' to `n`.
    *
    * The absolute value is taken '''after''' the widening — `Math.abs(n.toLong)`
    * and never `Math.abs(n)` — because two's complement is asymmetric.
    * `|Int.MinValue|` is `2^31` and the largest `Int` is `2^31 - 1`, so
    * `Math.abs(Int): Int` has nowhere to put the answer and returns its argument
    * unchanged. The 32-bit spelling hands the loop a negative `n`, the guard
    * `d > n` fires before the first iteration, and the result is an empty list:
    * a wrong answer with no exception and no warning. `Long` carries the same
    * asymmetry at `2^63`, which is `2^32` times beyond the largest `Int`, so one
    * widening closes the whole domain rather than moving the edge.
    */
  def digits(n: Int): MyList[Int] =
    @scala.annotation.tailrec
    def loop(
        n: Long,
        q: Long = 10,
        r: Long = 0,
        d: Long = 1,
        acc: MyList[Int] = MyList()
    ): MyList[Int] =
      if d > n then acc
      else
        val n_ = n - r
        val r_ = (n_ % q) / d
        val d_ = d * 10
        val q_ = q * 10
        loop(n_, q_, r_, d_, acc.prepended(r_.toInt))
    if n == 0 then MyList(0) else loop(Math.abs(n.toLong))

  /** The number of Collatz steps from `n` down to 1, counting the last step.
    *
    * `collatzLength(1) == 0`, `collatzLength(6) == 8`.
    *
    * The accumulator is a counter and the recursion has '''two''' recursive
    * branches with different arguments, which is the first shape in this module
    * where the `while` translation of §9 needs thought rather than
    * transcription.
    *
    * Nobody knows whether this terminates for every `n`. Your Scaladoc should
    * not claim that it does.
    */
  def collatzLength(n: Long): Int =
    @scala.annotation.tailrec
    def loop(n: Long, acc: Int = 0): Int =
      if n <= 1 then acc
      else if n % 2 == 0 then loop(n / 2, acc + 1)
      else loop(3 * n + 1, acc + 1)
    loop(n)

end Arithmetic

/** Exercise 4 — `while` loops, transcribed.
  *
  * Each of these is specified as an imperative loop in its Scaladoc. The
  * exercise is the mechanical translation of the guide's §9, applied without
  * improvisation: the `var`s become parameters, the condition becomes the `if`,
  * the updates become the arguments, the trailing value becomes the base case.
  *
  * Resist improving the algorithm while translating. A translation you can
  * check line against line is worth more here than a cleverer version you
  * cannot.
  */
object Loops:

  /** `n!` as a `Long`.
    *
    * {{{
    * var acc = 1L; var i = 2
    * while i <= n do { acc *= i; i += 1 }
    * acc
    * }}}
    *
    * `factorial(0) == 1`. Above `n = 20` a `Long` silently wraps; the domain is
    * yours to document, and "silently" is the word that decides whether a
    * comment is enough.
    */
  def factorial(n: Int): Long = ???

  /** The `n`-th Fibonacci number, `fibonacci(0) == 0`, `fibonacci(1) == 1`.
    *
    * {{{
    * var a = 0L; var b = 1L; var i = 0
    * while i < n do { val t = a + b; a = b; b = t; i += 1 }
    * a
    * }}}
    *
    * Note the temporary `t` in the loop, and note that your translation will
    * not need one. Guide §10 says why; your Scaladoc should say it too, because
    * it is the clearest single argument in this module for what the
    * transformation buys beyond stack safety.
    */
  def fibonacci(n: Int): Long = ???

  /** `n` with its decimal digits reversed. `reverseDigits(1024) == 4201`.
    *
    * {{{
    * var acc = 0; var m = n
    * while m != 0 do { acc = acc * 10 + m % 10; m /= 10 }
    * acc
    * }}}
    *
    * The loop overflows silently for inputs whose reversal exceeds `Int`, and
    * so will your translation. That is not a defect introduced by the
    * transformation, which is exactly why it is worth recording: a faithful
    * translation preserves the bugs too.
    */
  def reverseDigits(n: Int): Int = ???

end Loops

/** Exercise 5 — the branch that returns instead of recursing.
  *
  * Early exit needs no mechanism. In a loop, `break` decides which statements
  * run; in a recursion, the same decision is made by returning rather than
  * calling. Guide, Part III.10 and III.11.
  *
  * All four walk a `MyList` and all four must be `@tailrec`. Module 2 built the
  * structure; this exercise is about the shape of the walk.
  */
object EarlyExit:

  /** The index of the first element equal to `x`, or `-1`.
    *
    * `-1` is in-band signalling in the same `Int` domain as a legitimate index,
    * which Module 1 recorded as a live objection against `Bench.medianNanos`.
    * Use it anyway, for symmetry with the standard library, and record the
    * objection in the Scaladoc rather than pretending it is absent.
    */
  def indexOf[A](xs: MyList[A], x: A): Int = ???

  /** Whether `p` holds for every element. `true` for the empty list.
    *
    * Must stop at the first element that fails. A version that visits the whole
    * list and combines with `&&` returns the right answer and is a different
    * function; the spec counts calls to `p`.
    */
  def forall[A](xs: MyList[A], p: A => Boolean): Boolean = ???

  /** Whether `p` holds for at least one element. `false` for the empty list.
    *
    * Must stop at the first element that succeeds, for the same reason.
    *
    * State the relationship between this and `forall` in one line. There is
    * exactly one, it is an identity, and Block 2 will call it by name.
    */
  def exists[A](xs: MyList[A], p: A => Boolean): Boolean = ???

  /** The longest prefix whose elements all satisfy `p`.
    *
    * `takeWhile(MyList(1,2,3,1), _ < 3) == MyList(1, 2)`.
    *
    * This one cannot simply return at the first failure: it has a structure to
    * build. The accumulator is that structure, and it is built in the wrong
    * order for the same reason `digits` was. One `reverse` is the accepted
    * price; two traversals of the '''input''' is not.
    */
  def takeWhile[A](xs: MyList[A], p: A => Boolean): MyList[A] = ???

end EarlyExit
