package cs.se.block1.project1

import Expr.*

/** Stage 2 — tokens to a tree. Guide, Part III.
  *
  * Note how many of these assertions are on **trees** rather than on values.
  * Guide §39 measured three placements of unary minus, two of which agree on
  * every number in the sample and disagree on the tree: a suite that only
  * checks values cannot tell them apart, and would let the wrong grammar
  * through.
  */
class Stage2ParserSpec extends Project1Harness:

  /** A direct evaluator, local to this suite.
    *
    * Stage 4 is not built yet, and this suite must be runnable the moment
    * Stage 2 compiles. Duplicating six lines here is deliberate for a second
    * reason too: a suite that checked the parser's trees through the project's
    * own evaluator would pass whenever the two agreed, including when both were
    * wrong in the same direction. This is an independent oracle, and it is
    * allowed to be a naive recursion because every input below is tiny.
    */
  private def valueOf(e: Expr): Double = e match
    case Lit(v) => v
    case Var(_) => fail("this suite evaluates no variables")
    case Neg(a) => -valueOf(a)
    case Bin(Op.Add, l, r) => valueOf(l) + valueOf(r)
    case Bin(Op.Sub, l, r) => valueOf(l) - valueOf(r)
    case Bin(Op.Mul, l, r) => valueOf(l) * valueOf(r)
    case Bin(Op.Div, l, r) => valueOf(l) / valueOf(r)
    case Bin(Op.Pow, l, r) => math.pow(valueOf(l), valueOf(r))

  private def num(s: String): Double = valueOf(ast(s))

  private def parsed(s: String): Either[ParseError, Expr] =
    Lexer.tokenize(s) match
      case Left(e) => fail(s"[$s] failed to lex, not to parse: ${e.toString}")
      case Right(ts) => Parser.parse(ts)

  test("atoms") {
    assertEquals(ast("1"), Lit(1.0))
    assertEquals(ast("3.5"), Lit(3.5))
    assertEquals(ast("x"), Var("x"))
    assertEquals(ast("(1)"), Lit(1.0), "parentheses leave no trace in the tree")
    assertEquals(ast("(((1)))"), Lit(1.0))
  }

  test("precedence: * binds tighter than +") {
    assertEquals(ast("2 + 3 * 4"), Bin(Op.Add, Lit(2.0), Bin(Op.Mul, Lit(3.0), Lit(4.0))))
    assertEquals(ast("2 * 3 + 4"), Bin(Op.Add, Bin(Op.Mul, Lit(2.0), Lit(3.0)), Lit(4.0)))
    assertEquals(ast("(2 + 3) * 4"), Bin(Op.Mul, Bin(Op.Add, Lit(2.0), Lit(3.0)), Lit(4.0)))
    // The whole point, as a number: a left-to-right fold answers 20. Guide §11.
    assertSameBits(num("2 + 3 * 4"), 14.0, "2 + 3 * 4")
    assertSameBits(num("(2 + 3) * 4"), 20.0, "(2 + 3) * 4")
  }

  test("precedence: ^ binds tighter than *") {
    assertEquals(ast("2 * 3 ^ 2"), Bin(Op.Mul, Lit(2.0), Bin(Op.Pow, Lit(3.0), Lit(2.0))))
    assertSameBits(num("2 * 3 ^ 2"), 18.0, "2 * 3 ^ 2")
  }

  test("+ - * / are left associative") {
    assertEquals(ast("10 - 3 - 2"), Bin(Op.Sub, Bin(Op.Sub, Lit(10.0), Lit(3.0)), Lit(2.0)))
    assertSameBits(num("10 - 3 - 2"), 5.0, "left associative")
    assertSameBits(num("10 - (3 - 2)"), 9.0, "the other reading, for contrast")

    assertSameBits(num("100 / 10 / 2"), 5.0, "left associative")
    assertSameBits(num("100 / (10 / 2)"), 20.0)

    // Three operands is the shortest input that can tell the two apart, and
    // subtraction and division are the only operators where it shows. A suite
    // built on + and * proves nothing about associativity at all.
    assertSameBits(num("1 + 2 + 3"), 6.0, "commutative: both readings agree")
  }

  test("^ is right associative") {
    assertEquals(ast("2 ^ 3 ^ 2"), Bin(Op.Pow, Lit(2.0), Bin(Op.Pow, Lit(3.0), Lit(2.0))))
    assertSameBits(num("2 ^ 3 ^ 2"), 512.0, "2 ^ (3 ^ 2)")
    assertSameBits(num("(2 ^ 3) ^ 2"), 64.0, "the left-associative reading")
    // And the input that cannot distinguish them, so that its absence from the
    // suite is deliberate rather than accidental:
    assertSameBits(num("2 ^ 2 ^ 2"), 16.0, "both readings give 16 — proves nothing")
  }

  test("unary minus binds looser than ^ and tighter than *") {
    assertEquals(ast("-2 ^ 2"), Neg(Bin(Op.Pow, Lit(2.0), Lit(2.0))), "-(2 ^ 2)")
    assertSameBits(num("-2 ^ 2"), -4.0, "-2 ^ 2 is -4; a spreadsheet says 4")
    assertSameBits(num("(-2) ^ 2"), 4.0)

    assertEquals(ast("-2 * 3"), Bin(Op.Mul, Neg(Lit(2.0)), Lit(3.0)), "(-2) * 3")
    assertSameBits(num("-2 * 3"), -6.0)
    // The tree above and Neg(Bin(Mul, 2, 3)) both evaluate to -6. Only the
    // structural assertion separates them. Guide §39.

    assertEquals(ast("- -3"), Neg(Neg(Lit(3.0))), "two negations, two nodes")
    assertSameBits(num("- -3"), 3.0)
    assertSameBits(num("2 ^ -1"), 0.5, "unary minus in an operand position")
    assertEquals(ast("-x * y"), Bin(Op.Mul, Neg(Var("x")), Var("y")))
  }

  test("the token list must be consumed entirely") {
    // Every one of these parses a valid PREFIX. Nothing inside the descent is
    // unhappy; the check is at the top level, and it is the difference between
    // a parser and a prefix-matcher. Guide §17.
    assertEquals(parsed("1 + 2)"), Left(ParseError.TrailingInput(Token.RParen, 3)))
    assertEquals(parsed("1 2"), Left(ParseError.TrailingInput(Token.Num(2.0), 1)))
    assertEquals(parsed("1 + 2 x"), Left(ParseError.TrailingInput(Token.Ident("x"), 3)))
  }

  test("malformed input is rejected, never guessed at") {
    assertEquals(parsed(""), Left(ParseError.UnexpectedEnd))
    assertEquals(parsed("1 + "), Left(ParseError.UnexpectedEnd))
    assertEquals(parsed("(1 + 2"), Left(ParseError.UnexpectedEnd), "unclosed bracket")
    assertEquals(parsed("1 + + 2"), Left(ParseError.UnexpectedToken(Token.Plus, 2)))
    assertEquals(parsed("()"), Left(ParseError.UnexpectedToken(Token.RParen, 1)))
    assertEquals(parsed("* 2"), Left(ParseError.UnexpectedToken(Token.Star, 0)))
    assertEquals(
      parsed("1e3"),
      Left(ParseError.TrailingInput(Token.Ident("e3"), 1)),
      "scientific notation fails here, loudly, rather than silently answering 1"
    )
  }

  test("left-associated chains parse in constant stack") {
    // The expr level never re-enters itself, so the accumulator loop of §13
    // costs one frame however long the chain is. Two million terms.
    val big = "1" + " + 1".repeat(2_000_000)
    assert(survives(ast(big)), "a left-associated chain has no parser ceiling")
    // The node count is asserted in Stage 3, where a stack-safe `size` exists.
    // Counting it here would need a recursion this suite cannot afford.
  }

  test("nesting has a ceiling, and the ceiling is a number you must record") {
    // These are DOCUMENTED limits, not defects. The assertion is on the
    // ORDERING, never on the absolute depth: the depths are facts about this
    // JVM at this -Xss and the ordering is a fact about the grammar.
    val parens = maxSurviving(1 << 18)(n => ast("(".repeat(n) + "1" + ")".repeat(n)))
    val powers = maxSurviving(1 << 18)(n => ast("2 ^ ".repeat(n) + "1"))
    val unary = maxSurviving(1 << 18)(n => ast("- ".repeat(n) + "1"))
    report("parser ceiling, nested parentheses", parens)
    report("parser ceiling, chained ^", powers)
    report("parser ceiling, chained unary minus", unary)

    // The FLOOR is the only thing asserted. The three ceilings differ by about
    // 40%, in an order that depends on how many frames your grammar rules cost
    // per nesting level — and that is a property of your implementation, not of
    // the grammar. The reference implementation measures parentheses as the
    // cheapest and chained unary minus as the dearest; a five-function
    // recursive descent reverses the first two. Guide §18: record yours, do not
    // inherit these.
    List(("nested parentheses", parens), ("chained ^", powers), ("chained unary", unary))
      .foreach { (what, depth) =>
        assert(depth > 1000, s"a parser dying at $depth levels of $what is not usable")
      }
    val spread = List(parens, powers, unary).max.toDouble / List(parens, powers, unary).min
    report("spread between the three nestings", spread)
    assert(
      spread < 10.0,
      s"a spread of $spread means one grammar rule costs an order of magnitude more " +
        "frames per level than the others, which is worth explaining before accepting"
    )
  }

end Stage2ParserSpec
