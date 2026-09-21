package cs.se.block1.project1

import cs.se.block1.module2.MyList.*

/** Stage 1 — characters to tokens. Guide, Part II. */
class Stage1LexerSpec extends Project1Harness:

  private def toks(s: String): List[Token] = tokens(s).toScalaList

  test("the guide's own example lexes to exactly five tokens") {
    assertEquals(
      toks("2 + 3 * 4"),
      List(Token.Num(2.0), Token.Plus, Token.Num(3.0), Token.Star, Token.Num(4.0))
    )
  }

  test("whitespace is a separator and leaves no trace") {
    val spelled = List("2+3*4", "2 + 3 * 4", "  2  +  3  *  4  ", "2\t+\t3*4")
    val expected = toks("2+3*4")
    spelled.foreach(s => assertEquals(toks(s), expected, s"[$s]"))
    assertEquals(toks(""), List.empty[Token], "an empty input is an empty token list, not an error")
    assertEquals(toks("   "), List.empty[Token], "and so is one made only of separators")
  }

  test("every operator and bracket has its own token") {
    assertEquals(
      toks("+-*/^()"),
      List(
        Token.Plus,
        Token.Minus,
        Token.Star,
        Token.Slash,
        Token.Caret,
        Token.LParen,
        Token.RParen
      )
    )
  }

  test("numbers are scanned by maximal munch") {
    assertEquals(toks("12"), List(Token.Num(12.0)), "not Num(1), Num(2)")
    assertEquals(toks("1024"), List(Token.Num(1024.0)))
    assertEquals(toks("3.5"), List(Token.Num(3.5)))
    assertEquals(toks(".5"), List(Token.Num(0.5)), "a leading dot is a legal number")
    assertEquals(toks("1."), List(Token.Num(1.0)), "and so is a trailing one")
    assertEquals(
      toks("12+3"),
      List(Token.Num(12.0), Token.Plus, Token.Num(3.0)),
      "the munch stops at a character that cannot continue a number"
    )
  }

  test("identifiers are scanned by maximal munch too") {
    assertEquals(toks("x"), List(Token.Ident("x")))
    assertEquals(toks("rate"), List(Token.Ident("rate")), "not four Idents")
    assertEquals(toks("x1"), List(Token.Ident("x1")), "a digit may continue an identifier")
    assertEquals(toks("_a"), List(Token.Ident("_a")), "and an underscore may start one")
    assertEquals(
      toks("x+y"),
      List(Token.Ident("x"), Token.Plus, Token.Ident("y"))
    )
  }

  test("a number is never silently truncated") {
    // Maximal munch takes the whole run of digits and dots and THEN asks
    // whether it is a Double. Guide §8. A lexer that stops at the second dot
    // returns Num(1.2) and leaves ".3" behind, which then lexes as Num(0.3) —
    // two valid tokens from an invalid number, and no error anywhere.
    assertEquals(Lexer.tokenize("1.2.3"), Left(LexError.MalformedNumber("1.2.3", 0)))
    assertEquals(Lexer.tokenize("1 + 1.2.3"), Left(LexError.MalformedNumber("1.2.3", 4)))
    assertEquals(Lexer.tokenize("..."), Left(LexError.MalformedNumber("...", 0)))
  }

  test("an unrecognised character is an error carrying its index") {
    assertEquals(Lexer.tokenize("1 $ 2"), Left(LexError.UnexpectedChar('$', 2)))
    assertEquals(Lexer.tokenize("#"), Left(LexError.UnexpectedChar('#', 0)))
    assertEquals(Lexer.tokenize("1 + 2 @"), Left(LexError.UnexpectedChar('@', 6)))
    // The index is into the ORIGINAL string, spaces included. It is the only
    // character offset the whole pipeline ever has. Guide §10.
  }

  test("scientific notation is not in this grammar, and says so") {
    // The number munch stops at 'e', which then lexes as an identifier. The
    // lexer is content; Stage 2 is what rejects the pair. A lexer that returned
    // Num(1.0) and dropped the rest would answer 1 for "1e3". Guide §5.
    assertEquals(toks("1e3"), List(Token.Num(1.0), Token.Ident("e3")))
  }

  test("the first error wins and nothing after it is reported") {
    assertEquals(Lexer.tokenize("$ #"), Left(LexError.UnexpectedChar('$', 0)))
  }

  test("the scan is tail recursive") {
    // Four million characters. A scan that recurses per character, or that
    // appends to the accumulator instead of prepending, fails here — the first
    // with StackOverflowError, the second by taking quadratic time and timing
    // out. Guide §9.
    val big = "1" + " + 1".repeat(1_000_000)
    assertEquals(big.length, 4_000_001)
    assert(survives(Lexer.tokenize(big)), "1,000,000 terms must tokenise")
    assertEquals(tokens(big).length, 2_000_001, "n terms and n-1 operators")
  }

  test("tokenising is linear, not quadratic") {
    // Module 2 §7's doubling test, with a budget rather than a ratio.
    //
    // A ratio is the natural form and it is not reproducible here: sbt runs
    // test classes in PARALLEL in one JVM, so a wall-clock measurement shares
    // its machine with four other suites. The same ratio measured 2.12 with
    // this suite alone and 8.86 in a full run, and neither number says anything
    // about the lexer. A budget survives that, because the defect it is looking
    // for is not 2x slower — it is 500x slower.
    //
    // 1,000,000 terms is 2,000,001 tokens. Prepending is 2 million cell
    // constructions; appending is 2 * 10^12 cell traversals, which does not
    // finish today. Anything under a minute is linear; nothing quadratic
    // reaches the assertion at all.
    val src = "1" + " + 1".repeat(1_000_000)
    val start = System.nanoTime()
    val result = Lexer.tokenize(src)
    val elapsedMs = (System.nanoTime() - start) / 1_000_000
    report("lex 1,000,000 terms, ms", elapsedMs)
    assert(result.isRight, "it must also be correct")
    assert(
      elapsedMs < 60_000,
      s"tokenising 1,000,000 terms took ${elapsedMs}ms; the accumulator is being appended to"
    )
  }

end Stage1LexerSpec
