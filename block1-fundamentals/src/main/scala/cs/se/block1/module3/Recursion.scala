package cs.se.block1.module3

import cs.se.block1.module2.MyList
import MyList.*

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
        m: Long,
        acc: MyList[Int] = MyList()
    ): MyList[Int] =
      if m == 0 then acc
      else loop(m / 10, acc.prepended((m % 10).toInt))
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
    * Total over every `Int`, and correct over `[0, 20]`.
    *
    * Below that range the result is the empty product: `factorial(0) == 1`, and
    * so is every negative `n`, because `2 to n` is empty there and the loop
    * this was translated from returns its seed untouched.
    *
    * Above it the result is arithmetic modulo 2^64 and not a factorial.
    * `20! = 2,432,902,008,176,640,000` is the largest a `Long` holds; `21!`
    * overshoots `Long.MaxValue` by a factor of 5.54 and wraps, with no
    * exception raised and no flag left to read afterwards. The caller cannot
    * detect the wrap from the returned value, which is why the limit is stated
    * here as a domain rather than mentioned as a caveat, and why
    * `Exercise4LoopsSpec` pins both sides of it.
    */
  def factorial(n: Int): Long =
    @scala.annotation.tailrec
    def loop(m: Int = 2, acc: Long = 1L): Long =
      if m > n then acc
      else loop(m + 1, acc * m)
    loop()

  /** The `n`-th Fibonacci number, `fibonacci(0) == 0`, `fibonacci(1) == 1`.
    *
    * {{{
    * var a = 0L; var b = 1L; var i = 0
    * while i < n do { val t = a + b; a = b; b = t; i += 1 }
    * a
    * }}}
    *
    * Correct over `[0, 92]`. `F(92) = 7,540,113,804,746,346,429` is the largest
    * that fits a `Long`; `F(93)` wraps silently, exactly as `factorial` does
    * above 20. For `n <= 0` the result is `0`, the seed, as in the loop.
    *
    * '''Why the translation carries no `t`.''' The loop needs one because its
    * assignments are sequential: `a = b` destroys the old `a` before
    * `b = a + b` can read it, so `t` preserves a value the next statement is
    * about to condemn. The recursion assigns nothing. `loop(i + 1, b, a + b)`
    * evaluates every argument before a single binding is made, so the state
    * where `a` is already new and `b` is still old never exists — and a
    * temporary exists only to carry a value across such a state.
    *
    * The temporary does not leave the machine. The JVM has no simultaneous
    * assignment, so the compiled tail call stores both new values into fresh
    * slots and only then overwrites the parameters (guide §6). What changes is
    * who is answerable for it: forgetting `t` in the loop compiles, runs, and
    * returns powers of two, while in the recursion the mistake has nowhere to
    * live. That is what the transformation buys beyond stack safety.
    */
  def fibonacci(n: Int): Long =
    @scala.annotation.tailrec
    def loop(i: Int = 0, a: Long = 0L, b: Long = 1L): Long =
      if i >= n then a
      else loop(i + 1, b, a + b)
    loop()

  /** `n` with its decimal digits reversed. `reverseDigits(1024) == 4201`.
    *
    * {{{
    * var acc = 0; var m = n
    * while m != 0 do { acc = acc * 10 + m % 10; m /= 10 }
    * acc
    * }}}
    *
    * Total over every `Int`, and faithful over every `Int` — including where
    * the original is wrong. Three behaviours are inherited rather than chosen,
    * and all three follow from the loop's own arithmetic:
    *
    *   - Leading zeros vanish: `reverseDigits(100) == 1`, because `acc` never
    *     records the trailing zeros it multiplies past.
    *   - The sign is preserved: `reverseDigits(-1024) == -4201`, because `%`
    *     and `/` both truncate toward zero in Scala, so every digit arrives
    *     negative.
    *   - The result wraps when the reversal does not fit:
    *     `reverseDigits(1_999_999_999)` returns `1,410,065,399` rather than
    *     `9,999,999,991`.
    *
    * The overflow is inherited, not introduced, and it is deliberately left in
    * place. Widening the accumulator would make the translation unverifiable
    * against its original — and agreeing with that original on every input,
    * the wrong ones included, is the only property this exercise can check.
    */
  def reverseDigits(n: Int): Int =
    @scala.annotation.tailrec
    def loop(m: Int = n, acc: Int = 0): Int =
      if m == 0 then acc
      else loop(m / 10, acc * 10 + m % 10)
    loop()

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

  /** The index of the first element equal to `x`, or `-1` when no element is.
    *
    * `indexOf(MyList(3, 1, 4, 1), 1) == 1` — the '''first''' occurrence and not any
    * occurrence. `indexOf(MyList(), x) == -1` for every `x`.
    *
    * The walk stops at the element that decides the answer; nothing after the
    * first match is visited. Over 100,000 elements with no match at all it
    * allocates nothing — measured, 0 bytes.
    *
    * '''Two objections are recorded here rather than repaired.'''
    *
    * The first is the sentinel. `-1` is in-band signalling in the same `Int`
    * domain that carries a legitimate index: the absence of a result is encoded
    * as a value of the result type, and a caller who does not know the
    * convention cannot read it off the type. `Option[Int]` would carry the
    * distinction where the compiler can see it. Module 1 recorded the same
    * objection against `Bench.medianNanos`; the convention is kept for symmetry
    * with the standard library, which is a reason and not a defence.
    *
    * The second is the equality, and it is the sharper one. `x == h` compares
    * two values of the same type parameter `A`, so the unrelated-types check has
    * nothing to compare — it is not evaded, it is inapplicable by construction.
    * `MyList` is covariant, so a call site widens `A` instead of being rejected,
    * and universal equality is cooperative across the numeric tower:
    *
    * {{{
    * indexOf(MyList(1, 2, 3), 1L)  ==  0   // A inferred as Int | Long
    * indexOf(MyList(1, 2, 3), 1.0) ==  0   // A inferred as Int | Double
    * indexOf(MyList(1, 2, 3), "3") == -1   // A inferred as Int | String
    * }}}
    *
    * Written directly, `1 == "3"` is a hard error under `-source:future`. Routed
    * through this signature it is warning E225 — a type argument inferred to be
    * a union — and therefore an error in `Compile`, where `-Werror` is set, and
    * a warning only in `Test`, where the build drops `-Werror` deliberately. The
    * repair is a `using CanEqual[A, A]` context bound, which hands the proof
    * back to the caller; that is Block 2 machinery and is not used here.
    */
  def indexOf[A](xs: MyList[A], x: A): Int =
    @scala.annotation.tailrec
    def loop(ls: MyList[A], i: Int = 0): Int =
      ls match
        case Nil => -1
        case Cons(h, _) if x == h => i
        case Cons(_, t) => loop(t, i + 1)
    loop(xs)

  /** Whether `p` holds for every element, and `true` for the empty list.
    *
    * `true` is not a convention: it is the identity of `&&`, which is what the
    * empty conjunction is worth.
    *
    * '''Stopping is part of the contract, not an optimisation.''' `p` is applied
    * exactly once to each element visited, and no element after the one that
    * decides the answer is visited at all. `Exercise5EarlyExitSpec` counts the
    * applications with an `AtomicInteger` and asserts 4 over a list of
    * 1,000,000 whose fourth element is the first to fail — the one assertion in
    * the suite that a whole-list implementation would not also pass.
    *
    * '''The `&&` spelling is the same function.''' `p(h) && forall(t, p)` short
    * circuits, is accepted by `@tailrec`, and compiles to the same `goto`: `&&`
    * takes its right operand by name, but on `Boolean` it is an intrinsic that
    * becomes a branch rather than a call, so the operand is in tail position and
    * no frame is left pending. What is genuinely a different function is the
    * fold — `xs.foldLeft(true)((b, a) => b && p(a))` — which visits every
    * element regardless of `&&`, because `foldLeft` has no way to stop. The
    * short circuit belongs to the operator; the early exit belongs to the
    * recursion.
    *
    * `@tailrec` sits on a public method, which guide §26 would normally refuse:
    * the annotation needs a statically known target and an overridable method
    * has none. It is admissible because `EarlyExit` is an `object` and every
    * member of an `object` is final by construction — the same argument that
    * admits `Arithmetic.gcd`.
    *
    * Allocates nothing: 0 bytes over 100,000 elements, measured.
    */
  @scala.annotation.tailrec
  def forall[A](xs: MyList[A], p: A => Boolean): Boolean =
    xs match
      case Nil => true
      case Cons(h, t) if p(h) => forall(t, p)
      case _: Cons[A] => false

  /** Whether `p` holds for at least one element, and `false` for the empty list
    * — the identity of `||`, for the reason `forall` returns `true`.
    *
    * Stops at the first element that succeeds, under the same contract: one
    * application of `p` per element visited, and nothing visited after the
    * decision. With no match present there is nothing to stop at and the whole
    * list is walked, which the spec pins at 1,000,000 applications.
    *
    * '''The identity.''' `forall(xs, p) == !exists(xs, a => !p(a))` — De Morgan,
    * and Block 2 will name it again as the duality between the two monoids
    * `Boolean` carries, `(&&, true)` and `(||, false)`.
    *
    * It is not used as the implementation. The derived spelling would preserve
    * the early exit, since negating a predicate does not move the element where
    * it first decides, but it allocates one closure per call to carry
    * `a => !p(a)` — against zero for the direct spelling.
    */
  @scala.annotation.tailrec
  def exists[A](xs: MyList[A], p: A => Boolean): Boolean =
    xs match
      case Nil => false
      case Cons(h, t) if !p(h) => exists(t, p)
      case _: Cons[A] => true

  /** The longest prefix whose elements all satisfy `p`.
    *
    * `takeWhile(MyList(1, 2, 3, 1), _ < 3) == MyList(1, 2)`.
    *
    * '''It stops, it does not filter.''' Everything after the first failure is
    * dropped, including elements that satisfy `p`:
    * `takeWhile(MyList(1, 2, 9, 1, 2), _ < 5) == MyList(1, 2)`, and the later
    * `1` and `2` are gone. The returned values alone do not reveal the
    * difference between the two behaviours, which is why the spec asserts this
    * input specifically.
    *
    * `p` is applied once per element visited, and the element that fails is the
    * last one visited.
    *
    * '''The accumulator is a structure, and it is built backwards''' — the trade
    * `digits` makes in Exercise 3, for the same reason. Prepending is the only
    * constant-time insertion a singly-linked cell offers, so the order that
    * makes the recursion tail is the reverse of the order wanted.
    *
    * One `reverse` of the '''prefix''' is the price, and that is not a figure of
    * speech. Rebuilding the prefix costs two `Cons` cells per element kept —
    * 48 bytes against the 24 a non-tail spelling would allocate, measured at
    * 4,800,000 bytes for 100,000 elements. Stack safety is bought here with
    * exactly one extra copy of the result, and Exercise 6 makes that exchange
    * the subject rather than a side effect.
    *
    * What is never paid is a second traversal of the '''input'''.
    */
  def takeWhile[A](xs: MyList[A], p: A => Boolean): MyList[A] =
    @scala.annotation.tailrec
    def loop(ls: MyList[A], acc: MyList[A] = Nil): MyList[A] =
      ls match
        case Nil => xs
        case Cons(h, t) if p(h) => loop(t, acc.prepended(h))
        case _: Cons[A] => acc.reverse
    loop(xs)

end EarlyExit
