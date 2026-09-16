package cs.se.block1.module3

/** Exercise 2 — one function, three spellings, and the ceiling that separates
  * them.
  *
  * All three compute `1 + 2 + ... + n`. All three are correct. Two of them will
  * run on any input the `Int` domain admits, and one of them has a limit at
  * roughly fourteen thousand that no type, no test over small inputs, and no
  * code review catches. Guide, Part II.5 and II.6.
  *
  * Two of the three share a compiled shape and the third does not. The spec
  * measures the separation and Exercise 1's `maxDepth` reports it; each
  * function's Scaladoc below carries the listing that settles which is which.
  */
object TailShapes:

  /** The naive recursion: the addition happens after the call returns.
    *
    * `sumNaive(n) == 1 + 2 + ... + n`, and `0L` for `n <= 0`.
    *
    * '''The experimental control, and deliberately not `@tailrec`.''' One
    * instruction is the whole difference:
    *
    * {{{
    * 13: invokevirtual sumNaive
    * 16: ladd            <- work pending AFTER the return
    * 17: lreturn
    * }}}
    *
    * The `ladd` is why the frame cannot be discarded at the call: something in
    * it still has to run when the value comes back. Every frame therefore
    * survives until the base case, and the ceiling that follows is the
    * measurement this module exists to make — 14,999 cold on a 1 MiB thread,
    * and at least 39,999 once C2 has shrunk the frame.
    *
    * An implementation that survives large `n` has stopped being the control.
    */
  def sumNaive(n: Int): Long =
    if n <= 0 then 0L else n + sumNaive(n - 1)

  /** The same sum, with the pending addition moved into a parameter.
    *
    * Agrees with `sumNaive` wherever both survive, and survives everything:
    * one frame is reused for the whole computation, so the limit is the `Int`
    * domain of the parameter rather than `-Xss`.
    *
    * '''Written as a local `def`, and what the alternatives would have cost.'''
    * Three placements satisfy `@tailrec`, which needs a target nobody can
    * override (guide §7):
    *
    *   - a '''local `def`''' — chosen. The accumulator's seed is invisible to
    *     callers, so `sumAcc(n)` is the only spelling that exists and there is
    *     no second entry point to document or to call by mistake.
    *   - a '''`private` helper''' in the object — also correct, and what
    *     `Arithmetic.power` uses, because there the seed is worth naming once
    *     for a wrapper that does real work. The cost is a name in the object's
    *     scope and a second signature every reader must check is not the one to
    *     call.
    *   - '''the public method itself''', as `Arithmetic.gcd` does. Legal only
    *     because members of an `object` are final by construction; the cost is
    *     that `acc` joins the public signature, with a default a caller may
    *     override.
    *
    * The local `def` compiles to `loop$1(int, long)` plus a synthetic
    * `loop$default$2$1()` whose entire job is to produce the `0L`.
    *
    * '''The accumulator applies the additions in the opposite order to
    * `sumNaive`''' — `n` downward here, `1` upward there. For `+` that is
    * invisible, because addition is associative and commutative. Part VI.21 is
    * where it stops being invisible, and Exercise 6 is where the spec makes it
    * visible with an operator that is neither.
    */
  def sumAcc(n: Int): Long =
    @scala.annotation.tailrec
    def loop(n: Int, acc: Long = 0L): Long =
      if n <= 0 then acc
      else loop(n - 1, acc + n)
    loop(n)

  /** The same sum as an imperative loop, for comparison only.
    *
    * The one function in `module3` permitted `var` and `while`, because it
    * exists to be compared against — §D of the checklist names it. Everything
    * else in the module is the other side of the line.
    *
    * '''It compiles to the shape of `sumAcc`, not of `sumNaive`, and the `goto`
    * decides.''' Both reuse a single frame; `sumNaive` cannot, because of the
    * `ladd` waiting after its call.
    *
    * What survives between the two is the data movement:
    *
    * {{{
    * sumLoop                        sumAcc (loop$1)
    * 13: lstore_3    acc in place   10: istore 4    n-1 into a FRESH slot
    * 14: iinc 2, -1  m in place     16: lstore 5    acc+n into a FRESH slot
    * 17: goto 4                     18: iload 4
    *                                20: istore_1    only now n = ...
    *                                21: lload 5
    *                                23: lstore_2    only now acc = ...
    *                                24: goto 0
    * }}}
    *
    * The `while` overwrites in place — `iinc` is one instruction that decrements
    * a local where it sits. The compiled tail call writes both new values into
    * fresh slots and only then overwrites the parameters: four extra slot
    * operations per iteration, because the language promises simultaneous
    * evaluation of arguments on a machine that has no simultaneous assignment.
    * `Loops.fibonacci` is where that promise pays for itself, by making an
    * ordering bug unrepresentable rather than merely avoided.
    *
    * So the pure spelling is not free at the '''bytecode''' level. It is free at
    * the '''register''' level, after C2 allocates, and that is what makes the
    * trade worth taking rather than merely tolerating. Guide §6.
    */
  def sumLoop(n: Int): Long =
    var m = n
    var acc = 0L

    while m > 0
    do
      acc += m
      m -= 1
    acc

end TailShapes
