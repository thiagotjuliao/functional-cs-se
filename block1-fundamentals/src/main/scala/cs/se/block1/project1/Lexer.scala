package cs.se.block1.project1

import cs.se.block1.module2.MyList

/** What a piece of the input string *is*, with its spelling discarded.
  *
  * Guide, Part II.7. Two cases carry a payload because they differ in content;
  * the other seven carry none because they differ only in identity. A `Plus`
  * can never be compared against a number, because the compiler will not let
  * the two meet.
  *
  * Positions are deliberately **not** carried here. Guide II.10 states what
  * that costs and why the project pays it; §G asks you to defend the trade.
  */
enum Token:
  case Num(value: Double)
  case Ident(name: String)
  case Plus, Minus, Star, Slash, Caret, LParen, RParen

/** Why an input could not be turned into tokens.
  *
  * `at` is an index into the **original string**, and this is the only stage
  * where a character offset exists at all. Guide II.10.
  */
enum LexError:
  case UnexpectedChar(ch: Char, at: Int)
  case MalformedNumber(text: String, at: Int)

/** Stage 1 — characters to tokens, in one tail recursion.
  *
  * Build this first. Every later stage consumes its output, and none of them
  * can be run until it exists.
  *
  * Guide, Part II (§6 to §10).
  */
object Lexer:

  /** The token list for `input`, or the first lexical error in it.
    *
    * Contract:
    *   - **Spaces and tabs are separators and are discarded.** They may appear
    *     anywhere, including inside no token.
    *   - **Numbers are scanned by maximal munch** (guide §8): take the longest
    *     run of digits and dots, *then* ask whether that run is a `Double`. A
    *     run that is not — `"1.2.3"` — is a `MalformedNumber` carrying the
    *     whole run and the index it started at, never a silent truncation to
    *     `1.2` with a stray `.3` left behind.
    *   - **Identifiers** start with a letter or `_` and continue with letters,
    *     digits or `_`.
    *   - **No `throw`, no `var`, no `while`.** The scan is a `@tailrec` loop
    *     over an index and an accumulator (guide §9), and it must survive an
    *     input of four million characters — the spec asserts exactly that.
    *   - **The accumulator is built in reverse and reversed once at the end.**
    *     Appending to a cons list is `O(n)` and would make this quadratic:
    *     Module 2 §11 and §17.
    *   - The index advances by the length of the token consumed, not by `1`.
    *     A constant step re-reads the digits of a multi-character number as
    *     separate numbers, and produces a wrong token list without failing.
    *
    * Executed, from the guide:
    * {{{
    * tokenize("2 + 3 * 4") == Right(Num(2.0) :: Plus :: Num(3.0) :: Star :: Num(4.0))
    * tokenize("1 $ 2")     == Left(UnexpectedChar('$', 2))
    * tokenize("1.2.3")     == Left(MalformedNumber("1.2.3", 0))
    * }}}
    *
    * Scientific notation is **not** in this grammar: `"1e3"` lexes as
    * `Num(1.0)` followed by `Ident("e3")`, and Stage 2 rejects the pair.
    * Guide §5's edge-case table.
    */
  def tokenize(input: String): Either[LexError, MyList[Token]] = ???

end Lexer
