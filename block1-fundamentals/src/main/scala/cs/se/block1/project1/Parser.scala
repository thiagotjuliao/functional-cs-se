package cs.se.block1.project1

import cs.se.block1.module2.MyList

/** The five binary operators, as a closed sum.
  *
  * Separate from `Token` on purpose: `Token.Minus` is one symbol serving two
  * unrelated roles (guide §15), and `Op.Sub` is only one of them. The parser is
  * where the two are told apart, and having two types makes that conversion a
  * place in the code rather than an assumption.
  */
enum Op:
  case Add, Sub, Mul, Div, Pow

/** The abstract syntax tree.
  *
  * It has no parentheses and no spaces: three different strings collapse onto
  * one `Expr`, and that collapse is the parser's real output. Guide §4.
  *
  * `Var` carries no arithmetic and adds one case to every traversal. It is here
  * because without a symbol every term is closed, constant folding reduces
  * everything to a single `Lit`, and the whole of Part VI becomes unreachable
  * code. Guide §31.
  */
enum Expr:
  case Lit(value: Double)
  case Var(name: String)
  case Neg(arg: Expr)
  case Bin(op: Op, left: Expr, right: Expr)

/** Why a token list is not a well-formed expression.
  *
  * `at` is an index into the **token list**, not the input string — a different
  * number from `LexError`'s, and a message printing it as a column would be
  * lying. Guide II.10.
  */
enum ParseError:
  case UnexpectedEnd
  case UnexpectedToken(token: Token, at: Int)
  case TrailingInput(token: Token, at: Int)

/** Stage 2 — tokens to a tree, resolving precedence and associativity.
  *
  * The hardest stage, and the one where a defect is a wrong *number* rather
  * than a crash. Read Part III of the guide in full before starting, including
  * §11, which is the wrong implementation and the reason the right one has the
  * shape it has.
  *
  * The grammar, from guide §5:
  * {{{
  * expr    ::= term   (('+' | '-') term)*
  * term    ::= factor (('*' | '/') factor)*
  * factor  ::= unary  ('^' factor)?
  * unary   ::= '-' unary | atom
  * atom    ::= number | identifier | '(' expr ')'
  * }}}
  *
  * **Two implementations satisfy this spec and both are acceptable.** One
  * function per grammar line (guide §12) reads more clearly the first time;
  * precedence climbing with the levels as data (guide §16) is what survives a
  * grammar growing to twelve levels. Choose, and defend the choice in §G.
  */
object Parser:

  /** The tree for `tokens`, or the first syntactic error in it.
    *
    * Contract:
    *   - **Precedence**: `^` over `*` `/` over `+` `-`. `2 + 3 * 4` is
    *     `Bin(Add, 2, Bin(Mul, 3, 4))` and evaluates to `14`, not `20`.
    *   - **`+ - * /` are left associative.** `10 - 3 - 2` is `5`, not `9`.
    *   - **`^` is right associative.** `2 ^ 3 ^ 2` is `512`, not `64`. There is
    *     no input below three operands where the two readings differ, so a
    *     suite that stops at `2 ^ 2` proves nothing.
    *   - **Unary minus binds looser than `^` and tighter than `*`.** `-2 ^ 2`
    *     is `Neg(Bin(Pow, 2, 2))` and evaluates to `-4`. Guide §15 and §39.
    *   - **The token list must be consumed entirely.** `"1 + 2)"` and `"1 2"`
    *     both parse a valid *prefix* and must still fail, with `TrailingInput`.
    *     This check lives at the top level; nothing inside the descent notices.
    *     Guide §17 — it is the difference between a parser and a
    *     prefix-matcher.
    *   - No `throw`, no `var`. The left-associative levels are `@tailrec`
    *     loops over an accumulator (guide §13); the `^` level is a genuine
    *     recursion and cannot be one (guide §14).
    *
    * '''Known ceiling, and it is not removed.''' This parser is a recursion,
    * so the nesting depth of the input bounds it. Measured on the reference
    * implementation at the default `-Xss`: about 12,330 nested parentheses,
    * 10,280 chained `^`, 8,810 chained unary minus — and **no** ceiling for
    * left-associated `+`, which parses two million terms in constant stack
    * because its level never re-enters itself.
    *
    * '''That ordering is a property of this implementation, not of the
    * grammar.''' It follows from how many frames each rule costs per level, and
    * a five-function recursive descent (guide §12) pays five frames for a `(`
    * where `climb` pays two — which moves parentheses from the cheapest row to
    * the dearest. Guide §18. Measure your own and record them in §E; the
    * system's real ceiling is the smallest of them.
    */
  def parse(tokens: MyList[Token]): Either[ParseError, Expr] = ???

end Parser
