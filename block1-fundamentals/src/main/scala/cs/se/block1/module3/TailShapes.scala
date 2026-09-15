package cs.se.block1.module3

/** Exercise 2 — one function, three spellings, and the ceiling that separates
  * them.
  *
  * All three compute `1 + 2 + ... + n`. All three are correct. Two of them will
  * run on any input the `Int` domain admits, and one of them has a limit at
  * roughly fourteen thousand that no type, no test over small inputs, and no
  * code review catches. Guide, Part II.5 and II.6.
  *
  * Write all three before running anything, then predict which two share a
  * compiled shape. The spec measures it, and Exercise 1's `maxDepth` is what
  * reports the answer.
  */
object TailShapes:

  /** The naive recursion: the addition happens after the call returns.
    *
    * '''Do not add `@tailrec` to this, and do not try to make it pass.''' It is
    * the experimental control, and an implementation that survives large `n` is
    * an implementation that has stopped being the control.
    *
    * Returns `0L` for `n <= 0`.
    */
  def sumNaive(n: Int): Long =
    if n <= 0 then 0L else n + sumNaive(n - 1)

  /** The same sum, with the pending addition moved into a parameter.
    *
    * Must carry `@tailrec`, and the annotation must compile — which, per the
    * guide's §7, requires the recursion to be somewhere nobody can override.
    * Decide whether that means a `private` helper, a local `def`, or something
    * else, and say in the Scaladoc which one you chose and what the other two
    * would have cost.
    *
    * The accumulator applies the additions in the opposite order to
    * `sumNaive`. For `+` that is invisible. Part VI.21 is where it stops being
    * invisible, and Exercise 6 is where the spec makes it visible.
    */
  def sumAcc(n: Int): Long =
    @scala.annotation.tailrec
    def loop(n: Int, acc: Long = 0L): Long =
      if n <= 0 then acc
      else loop(n - 1, acc + n)
    loop(n)

  /** The same sum as an imperative loop, for comparison only.
    *
    * This is the one function in `module3` permitted to use `var` and a `while`
    * loop, because it exists to be compared against — §D of the checklist
    * names it. Everything else in the module is the other side of the line.
    *
    * Predict, before disassembling: does this compile to the same bytecode
    * shape as `sumNaive`, as `sumAcc`, or as neither? Guide §6 answers it, and
    * §G asks you to defend the answer.
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
