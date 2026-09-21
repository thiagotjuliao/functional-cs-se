package cs.se.block1.project1

import Expr.*

/** Stage 4 — the tree to a number, without the stack. Guide, Part V. */
class Stage4EvalSpec extends Project1Harness:

  private val env: Eval.Env = Map("x" -> 3.0, "y" -> 4.0)

  private def ev(s: String, e: Eval.Env = env): Either[EvalError, Double] =
    Eval.eval(ast(s), e)

  test("applyOp is IEEE-754 and nothing else") {
    assertSameBits(Eval.applyOp(Op.Add, 2.0, 3.0), 5.0)
    assertSameBits(Eval.applyOp(Op.Sub, 2.0, 3.0), -1.0, "a, then b — not b, then a")
    assertSameBits(Eval.applyOp(Op.Mul, 2.0, 3.0), 6.0)
    assertSameBits(Eval.applyOp(Op.Div, 3.0, 2.0), 1.5)
    assertSameBits(Eval.applyOp(Op.Pow, 2.0, 10.0), 1024.0)

    // Arithmetic does not fail. Division by zero is a value, not an error, and
    // that is why EvalError has exactly one case. Guide §29's opening.
    assertSameBits(Eval.applyOp(Op.Div, 1.0, 0.0), Double.PositiveInfinity)
    assertSameBits(Eval.applyOp(Op.Div, -1.0, 0.0), Double.NegativeInfinity)
    assert(Eval.applyOp(Op.Div, 0.0, 0.0).isNaN, "0 / 0 is NaN")
    assertSameBits(Eval.applyOp(Op.Pow, Double.NaN, 0.0), 1.0, "pow(NaN, 0) is 1.0")
  }

  test("the operand order is not commutative and the suite must say so") {
    // Reverse the two pops in Combine and every one of these flips sign or
    // inverts. Every assertion built on + and * would still pass. Guide §27.
    assertSameBits(value("10 - 3"), 7.0, "not -7")
    assertSameBits(value("10 / 4"), 2.5, "not 0.4")
    assertSameBits(value("2 ^ 10"), 1024.0, "not 100")
    assertSameBits(value("2 + 3"), 5.0, "commutative: proves nothing about order")
  }

  test("the whole grammar evaluates") {
    assertSameBits(value("2 + 3 * 4"), 14.0)
    assertSameBits(value("10 - 3 - 2"), 5.0)
    assertSameBits(value("100 / 10 / 2"), 5.0)
    assertSameBits(value("2 ^ 3 ^ 2"), 512.0)
    assertSameBits(value("-2 ^ 2"), -4.0)
    assertSameBits(value("- -3"), 3.0)
    assertSameBits(value(".5 + .25"), 0.75)
    assertSameBits(value("-x * y", env), -12.0)
    assertSameBits(value("(x + y) ^ 2", env), 49.0)
  }

  test("an unbound variable is a value, never an exception") {
    assertEquals(ev("z + 1"), Left(EvalError.Unbound("z")))
    assertEquals(ev("1 + z * 2"), Left(EvalError.Unbound("z")))
    assertEquals(ev("x", Map.empty), Left(EvalError.Unbound("x")))
    assertEquals(ev("x + y"), Right(7.0))
  }

  test("the short circuit abandons the pending work") {
    // A million nodes of work sit behind the unbound variable, and none of it
    // runs. If this times out, the Left is being collected rather than
    // returned. Guide §28.
    val e = Bin(Op.Add, Var("nope"), leftChain(1_000_000))
    assertEquals(Eval.eval(e, env), Left(EvalError.Unbound("nope")))
  }

  test("eval agrees with the naive control, bitwise, wherever the control survives") {
    // `assertEquals` on Double would pass here for the wrong reason: -0.0 == 0.0
    // is true and NaN == NaN is false, so it is blind in one direction and
    // hypersensitive in the other. Guide §38.
    val sources = List(
      "2 + 3 * 4",
      "10 - 3 - 2",
      "2 ^ 3 ^ 2",
      "-2 ^ 2",
      "1 / 0",
      "0 / 0",
      "-1 / 0",
      "0 - 0",
      "x * 0",
      "(x + y) / (x - 3)"
    )
    val envs: List[Eval.Env] = List(
      env,
      Map("x" -> -0.0, "y" -> 0.0),
      Map("x" -> Double.PositiveInfinity, "y" -> 1.0),
      Map("x" -> Double.NaN, "y" -> 1.0)
    )
    for s <- sources; e <- envs do
      (Eval.eval(ast(s), e), Eval.evalNaive(ast(s), e)) match
        case (Right(a), Right(b)) => assertSameBits(a, b, s"[$s] with ${e.toString}")
        case (a, b) => assertEquals(a, b, s"[$s] with ${e.toString}")
  }

  test("the naive control has a ceiling") {
    val left = maxSurviving(1 << 20)(n => Eval.evalNaive(leftChain(n), env))
    val right = maxSurviving(1 << 20)(n => Eval.evalNaive(rightChain(n), env))
    report("evalNaive, deepest left chain", left)
    report("evalNaive, deepest right chain", right)
    assert(left < 500_000, s"evalNaive survived $left; it is not the naive version")
    assert(right < 500_000, s"evalNaive survived $right on the mirror shape")

    // '''The two ceilings are equal, and this suite deliberately does not
    // assert it.''' Measured in isolation they agree to four digits — 24,673
    // against 24,675 — which is the prediction §E asks you to write down before
    // running this: the `flatMap`/`map` chain makes the two sides look
    // asymmetric in the source and they are not, because both recurse to full
    // depth before anything combines.
    //
    // Asserting it here would be measuring something else. sbt runs test
    // classes in PARALLEL in one JVM, so these two searches happen at different
    // moments of contention: the same pair measured 24,673/24,675 alone and
    // 3,871/17,601 in a full run. Neither ratio is a fact about the evaluator.
    // Compare them by running this suite on its own, and record the conditions
    // beside the numbers in §E. `error-patterns.md`, Pattern 13.
    report("left over right", left.toDouble / right)
  }

  test("eval has no ceiling at ten million") {
    assertEquals(Eval.eval(leftChain(1_000_000), env), Right(1_000_001.0))
    assert(survives(Eval.eval(leftChain(10_000_000), env)), "ten million nodes deep")
    assert(survives(Eval.eval(rightChain(1_000_000), env)), "and the mirror shape")
  }

  test("the machine trades heap for stack, and the trade is not free") {
    // Ratios only: the byte counts are facts about this JVM. What must hold
    // anywhere is that the machine allocates MORE than the control, because the
    // frames it removed from the stack are now cons cells and instructions on
    // the heap. Guide §29.
    val e = leftChain(10_000)
    val machine = probeBytes(Eval.eval(e, env))
    val naive = probeBytes(Eval.evalNaive(e, env))
    val nodes = Shape.size(e)
    report("eval bytes per node", machine.toDouble / nodes)
    report("evalNaive bytes per node", naive.toDouble / nodes)
    report("eval / evalNaive", machine.toDouble / naive)
    assert(
      machine > naive,
      s"the machine allocated $machine against the control's $naive — the frames " +
        "have to go somewhere, and if they did not go on the heap they are still " +
        "on the stack"
    )
  }

end Stage4EvalSpec
