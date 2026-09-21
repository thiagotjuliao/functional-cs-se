package cs.se.block1.project1

import Expr.*

/** Stage 5 — algebraic rewriting and the pipeline. Guide, Parts VI and VII.
  *
  * The central assertion of this suite is not that `simplify` makes trees
  * smaller. It is that `simplify` makes trees smaller **without changing what
  * they compute, for any input at all** — and "any input at all" means both
  * zeros, both infinities and `NaN`, which is where four familiar identities
  * stop being true.
  */
class Stage5EngineSpec extends Project1Harness:

  private val x = Var("x")

  /** Evaluate `e` with `x` bound to each of the seven probe values and return
    * the results, so two trees can be compared over the whole domain at once.
    */
  private def spectrum(e: Expr): List[(String, Double)] =
    probeValues.map { (name, v) =>
      name -> (Eval.eval(e, Map("x" -> v)) match
        case Right(d) => d
        case Left(err) => fail(s"${e.toString} did not evaluate: ${err.toString}"))
    }

  /** Assert that rewriting `e` into `rewritten` changed no value anywhere. */
  private def assertValuePreserving(e: Expr, rewritten: Expr, clue: String): Unit =
    spectrum(e).zip(spectrum(rewritten)).foreach { case ((n, before), (_, after)) =>
      assert(
        sameBits(before, after),
        s"$clue: at x = $n the tree computed ${show(before)} and the rewrite " +
          s"computes ${show(after)}"
      )
    }

  // ---------------------------------------------------------------- folding

  test("constant folding collapses a closed term") {
    assertEquals(Simplify.simplify(ast("2 * 3 + 4 * 5")), Lit(26.0))
    assertEquals(Shape.size(ast("2 * 3 + 4 * 5")), 7)
    assertEquals(Shape.size(Simplify.simplify(ast("2 * 3 + 4 * 5"))), 1)
    assertEquals(Simplify.simplify(ast("- -3")), Lit(3.0), "two negations")
    assertEquals(Simplify.simplify(ast("-2 ^ 2")), Lit(-4.0))
  }

  test("simplification means something only over an open term") {
    // Every reduction here happens AROUND an unknown. Fold a closed term and
    // you have merely evaluated it; the identity rules are unreachable. Guide
    // §31, and the reason `Var` is in the AST at all.
    val source = ast("(x * 1 + -0.0) * (2 ^ 1) + (4 - 2) * 1")
    assertEquals(Shape.size(source), 16)
    assertEquals(Shape.depth(source), 5)

    val simplified = Simplify.simplify(source)
    assertEquals(Shape.size(simplified), 5)
    assertEquals(Shape.depth(simplified), 3)
    assertEquals(Shape.render(simplified), "x * 2 + 2")
    assertValuePreserving(source, simplified, "the guide's §31 example")
  }

  test("one bottom-up pass reaches the fixed point") {
    val sources = List(
      "(x * 1 + -0.0) * (2 ^ 1) + (4 - 2) * 1",
      "x * 1 * 1 * 1 * 1",
      "- - - - x",
      "((x + -0.0) - 0.0) ^ 1",
      "2 * 3 + 4 * 5"
    )
    sources.foreach { s =>
      val once = Simplify.simplify(ast(s))
      assertEquals(Simplify.simplify(once), once, s"[$s] is not a fixed point after one pass")
    }
  }

  // ------------------------------------------------------------- soundness

  test("every rule in the sound set preserves the value at all seven probes") {
    // The mechanical check of guide §34. Each pair is (tree, what it rewrites
    // to); the assertion is that the two agree BITWISE at +0.0, -0.0, ±1.0,
    // ±Infinity and NaN.
    val rules = List(
      "x * 1" -> x,
      "1 * x" -> x,
      "x / 1" -> x,
      "x ^ 1" -> x,
      "x ^ 0" -> Lit(1.0),
      "x - 0.0" -> x,
      "x + -0.0" -> x,
      "-0.0 + x" -> x,
      "- -x" -> x
    )
    rules.foreach { (source, expected) =>
      val tree = ast(source)
      assertValuePreserving(tree, expected, s"[$source] -> [${Shape.render(expected)}]")
      assertEquals(
        Simplify.simplify(tree),
        Simplify.simplify(expected),
        s"[$source] was not rewritten"
      )
    }
  }

  test("the additive identity of Double is -0.0, and the sound set knows it") {
    // x + 0.0 -> x is UNSOUND at x = -0.0, because (-0.0) + 0.0 is +0.0.
    // x + (-0.0) -> x is sound everywhere. The rule you would write from
    // memory is the wrong one of the pair. Guide §36.
    assertSameBits(-0.0 + 0.0, 0.0, "the fact the whole rule rests on")
    assertSameBits(-0.0 + -0.0, -0.0)

    val plusPosZero = ast("x + 0.0")
    assertEquals(
      Simplify.simplify(plusPosZero),
      plusPosZero,
      "the sound set must LEAVE x + 0.0 alone; removing it changes -0.0 into +0.0"
    )
    assertValuePreserving(plusPosZero, Simplify.simplify(plusPosZero), "x + 0.0")

    // And the pattern trap: `case Bin(Add, e, Lit(0.0))` also matches a node
    // holding -0.0, because pattern matching on a literal uses == and
    // -0.0 == 0.0. The guard has to read the sign bit. Guide §49.
    assertEquals(
      Simplify.simplify(ast("x + -0.0")),
      x,
      "x + (-0.0) must be rewritten — if it is not, the guard is inverted"
    )
  }

  test("the sound set refuses x * 0 and the three rules like it") {
    // Unsound at five of the seven probe values. Guide §37.
    assertSameBits(Double.PositiveInfinity * 0.0, Double.NaN, "the fact")
    List("x * 0.0", "0.0 * x", "x - x", "x / x").foreach { s =>
      val tree = ast(s)
      assertValuePreserving(tree, Simplify.simplify(tree), s"[$s] under the sound set")
    }
    assertEquals(Simplify.simplify(ast("x * 0.0")), ast("x * 0.0"), "left untouched")

    // The one that looks like a sibling and is safe: pow(x, ±0.0) is 1.0 for
    // every x, NaN included, by the Math.pow specification.
    assertSameBits(math.pow(Double.NaN, 0.0), 1.0)
    assertEquals(Simplify.simplify(ast("x ^ 0.0")), Lit(1.0))
  }

  test("simplify preserves the value of arbitrary terms over the whole domain") {
    val sources = List(
      "x + 0.0",
      "x * 0.0",
      "x * 1 + 0.0",
      "(x - 0.0) * 1",
      "x ^ 1 - x",
      "- -x + -0.0",
      "(x + 1) * 1 - 0.0",
      "x / 1 / 1",
      "(x * 0.0) + (x ^ 0)"
    )
    sources.foreach { s =>
      val tree = ast(s)
      assertValuePreserving(tree, Simplify.simplify(tree), s"[$s]")
    }
  }

  // ------------------------------------------------------------- fast math

  test("the fast set must diverge, at exactly the two documented inputs") {
    // A simplifyFast that agrees with simplify everywhere has not implemented
    // the extra rules, and this test exists to fail it for that. Guide §35.
    val plusZero = ast("x + 0.0")
    assertEquals(Simplify.simplifyFast(plusZero), x, "x + 0.0 -> x under fast math")

    val negZero: Eval.Env = Map("x" -> -0.0)
    val before = Eval.eval(plusZero, negZero)
    val after = Eval.eval(Simplify.simplifyFast(plusZero), negZero)
    assertEquals(before, Right(0.0))
    assertEquals(after, Right(-0.0))
    assertEquals(before, after, "and `==` cannot see it — this is the trap, not a bug")
    assert(
      !sameBits(before.toOption.get, after.toOption.get),
      "the two must differ bitwise; if they do not, the rewrite did not happen"
    )
    assertSameBits(1 / before.toOption.get, Double.PositiveInfinity)
    assertSameBits(1 / after.toOption.get, Double.NegativeInfinity)

    val timesZero = ast("x * 0.0")
    val infinite: Eval.Env = Map("x" -> Double.PositiveInfinity)
    assert(Eval.eval(timesZero, infinite).toOption.get.isNaN, "Infinity * 0 is NaN")
    assertEquals(
      Eval.eval(Simplify.simplifyFast(timesZero), infinite),
      Right(0.0),
      "and the fast rewrite turns that NaN into a zero"
    )
  }

  test("the fast set agrees with the sound one wherever no positive zero appears") {
    val sources = List("x * 1", "x ^ 0", "2 * 3 + 4 * 5", "- -x", "x + -0.0", "x - 0.0")
    sources.foreach(s => assertEquals(Simplify.simplifyFast(ast(s)), Simplify.simplify(ast(s)), s))
  }

  // ----------------------------------------------------------- stack safety

  test("simplify has no ceiling at a million") {
    // A rewriter that dies at thirty thousand nodes has MOVED the ceiling, not
    // removed it, and the system is no safer than its most fragile traversal.
    // Guide §33 and §41.
    val big = leftChain(1_000_000)
    assert(survives(Simplify.simplify(big)), "1,000,000 deep")
    assertEquals(Simplify.simplify(big), Lit(1_000_001.0), "and it folds the whole chain")
  }

  // -------------------------------------------------------------- pipeline

  test("the pipeline runs end to end") {
    assertEquals(Engine.evaluate("2 + 3 * 4", Map.empty), Right(14.0))
    assertEquals(Engine.evaluate("x + 1", Map("x" -> 3.0)), Right(4.0))
    assertEquals(Engine.compile("2 * 3 + 4 * 5"), Right(Lit(26.0)))
  }

  test("each stage's failure surfaces as its own case, and the first one wins") {
    assertEquals(
      Engine.evaluate("1 $ 2", Map.empty),
      Left(EngineError.Lexical(LexError.UnexpectedChar('$', 2)))
    )
    assertEquals(
      Engine.evaluate("1 + + 2", Map.empty),
      Left(EngineError.Syntactic(ParseError.UnexpectedToken(Token.Plus, 2)))
    )
    assertEquals(
      Engine.evaluate("z + 1", Map("x" -> 3.0)),
      Left(EngineError.Semantic(EvalError.Unbound("z"))),
      "it parses, and fails afterwards"
    )
    // "1 $ )" is both a lexical and a syntactic error. The lexical one wins,
    // because no later pass runs. That is flatMap, not a special case.
    assertEquals(
      Engine.evaluate("1 $ )", Map.empty),
      Left(EngineError.Lexical(LexError.UnexpectedChar('$', 2)))
    )
  }

  test("simplification must not change the pipeline's answer") {
    // If simplifyFast were wired into `evaluate`, this is the assertion that
    // would find it — and only because the environments below include the
    // values where the two rule sets differ.
    val sources = List("x + 0.0", "x * 0.0", "x * 1 + 0.0", "(x - 0.0) ^ 1", "x / 1")
    val envs: List[Eval.Env] = probeValues.map { case (_, v) => Map("x" -> v) }
    for s <- sources; e <- envs do
      val viaEngine = Engine.evaluate(s, e)
      val unsimplified = Eval.eval(ast(s), e)
      (viaEngine, unsimplified) match
        case (Right(a), Right(b)) => assertSameBits(a, b, s"[$s] with ${e.toString}")
        case (a, b) =>
          fail(s"[$s] with ${e.toString}: engine gave ${a.toString}, direct gave ${b.toString}")
  }

  test("the system's ceiling is the parser's, and it is written down") {
    // eval survives ten million; parse dies in the low thousands. A claim of
    // "zero stack overflow risk" that does not name the smaller number is
    // false. Guide §41.
    val evalCeiling = survives(Engine.evaluate("1" + " + 1".repeat(1_000_000), Map.empty))
    assert(evalCeiling, "a flat million-term input must survive the whole pipeline")

    val nested = maxSurviving(1 << 18) { n =>
      Engine.evaluate("(".repeat(n) + "1" + ")".repeat(n), Map.empty) match
        case Right(_) => ()
        case Left(e) => fail(s"nesting $n failed with ${e.toString}")
    }
    report("pipeline ceiling, nested parentheses", nested)
    assert(nested > 1000, s"the pipeline died at $nested nested parentheses")
  }

end Stage5EngineSpec
