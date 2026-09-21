package cs.se.block1.project1

/** Why an otherwise well-formed tree could not be reduced to a number.
  *
  * There is exactly one case, and the list is short for a reason worth stating:
  * **arithmetic itself never fails here.** `1 / 0` is `Infinity` and `0 / 0` is
  * `NaN` — both are ordinary `Double` values under IEEE-754, not errors — so
  * division by zero is not on this list. Guide §37 and Part VII.
  */
enum EvalError:
  case Unbound(name: String)

/** Stage 4 — the tree to a number, without the stack.
  *
  * Guide, Part V (§25 to §29).
  */
object Eval:

  /** A variable binding. Immutable, and total only over the names it holds.
    */
  type Env = Map[String, Double]

  /** Apply one operator to two already-computed operands.
    *
    * `Pow` is `math.pow`. Note that this is the only place in the project where
    * IEEE-754 semantics are produced rather than merely respected, which makes
    * it the reference every rewrite in Stage 5 is checked against.
    */
  def applyOp(op: Op, a: Double, b: Double): Double = ???

  /** Evaluation by direct recursion. **The experimental control.**
    *
    * Correct on everything it survives, and measured at
    * `24,673 / 24,675 / 24,675` nodes of depth on the reference implementation,
    * with the suite running alone.
    *
    * **Keep it.** `eval` below is only demonstrably equivalent because there is
    * something to compare it against, and every correctness property in the
    * spec is asserted on both, on every input small enough for this one.
    *
    * One measured result worth predicting before you run it: a left-leaning
    * chain and a right-leaning chain of the same depth give the **same**
    * ceiling. Guide §25 — write your prediction down first.
    */
  def evalNaive(e: Expr, env: Env): Either[EvalError, Double] = ???

  /** Evaluation in `O(1)` stack.
    *
    * Contract:
    *   - agrees with `evalNaive` on every tree `evalNaive` survives, compared
    *     **bitwise** rather than with `==` — guide §38 is why that matters and
    *     why an `assertEquals` here can pass for the wrong reason;
    *   - survives a tree 1,000,000 deep, and 10,000,000. The million-deep left
    *     chain of ones evaluates to `1000001.0`;
    *   - `@tailrec`;
    *   - `Left(Unbound(n))` short-circuits: the rest of the pending work is
    *     abandoned, and the method still satisfies `@tailrec` because a return
    *     is not a call. Guide §28.
    *
    * '''The shape.''' Two stacks on the heap — pending work, and computed
    * values — and a small instruction type that records what to do on the way
    * back up. Guide §23 gives the instruction set and §26 the worked trace of
    * `2 + 3 * 4` through all seven steps.
    *
    * '''The defect this stage exists to make you meet.''' The value stack pops
    * the **right** operand first, because it was pushed last. Reverse the two
    * and `10 - 3` returns `-7`: the suite stays green for every commutative
    * operator and fails only on `-`, `/` and `^`. Guide §27, which also covers
    * the worse version, where the push order is reversed *as well* and the two
    * defects cancel into a silent right-to-left evaluation order.
    *
    * '''What it costs.''' Measured: 108 bytes per node, against 40 for
    * `evalNaive` and 24 for an AST node itself — the machine allocates 4.5x the
    * tree, transiently, on every evaluation. It does not make evaluation
    * cheaper; it makes it possible on inputs the control cannot reach. Guide
    * §29, and §G asks why the engine does not dispatch on depth.
    */
  def eval(e: Expr, env: Env): Either[EvalError, Double] = ???

end Eval
