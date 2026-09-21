package cs.se.block1.project1

import cs.se.block1.module1.AllocationProbe
import cs.se.block1.module2.MyList
import scala.concurrent.duration.*

/** Shared harness for the Mini-Project 1 suites — the algebraic expression
  * engine.
  *
  * The five suites carry four kinds of assertion, and the kind decides how
  * tight each one is allowed to be:
  *
  *   - **Value** assertions compare numbers. They are exact, they must pass
  *     anywhere, and they compare with [[sameBits]] rather than `==` wherever a
  *     zero or a `NaN` can reach them. Guide §38: `-0.0 == 0.0` is `true` and
  *     `NaN == NaN` is `false`, so `==` is blind in one direction and
  *     hypersensitive in the other, and an `assertEquals` on `Double` can pass
  *     for the wrong reason.
  *   - **Structure** assertions compare trees. Exact, and they are the only
  *     assertions that can distinguish two grammars that agree on every value
  *     in a sample — guide §39's table is three placements of unary minus, two
  *     of which produce identical numbers and different trees.
  *   - **Ceiling** assertions claim that one implementation survives an input
  *     another cannot. These are experiments and they may **never** assert an
  *     absolute depth: the guide measured a 3x spread across grammar rules on
  *     one machine at one `-Xss`. Assert the *separation*, and let [[report]]
  *     print the number for §E.
  *   - **Cost** assertions compare allocation. Ratios only, with a tolerance,
  *     and never a byte count — the count is a fact about this JVM.
  *
  * One suite per stage. `Stage1LexerSpec` needs nothing but its own stage;
  * every later suite needs the ones before it, which is why the stages are
  * built in order.
  */
abstract class Project1Harness extends munit.FunSuite:

  override val munitTimeout: Duration = 600.seconds

  /** Whether `body` completes without exhausting the stack. */
  protected def survives[A](body: => A): Boolean =
    try
      val _ = body; true
    catch case _: StackOverflowError => false

  /** Bitwise identity — the only equality on `Double` that sees the sign of a
    * zero and reports `NaN` as equal to itself.
    *
    * Guide §34 and §38. Every value assertion that a zero, an infinity or a
    * `NaN` can reach goes through this rather than `assertEquals`.
    *
    * '''`doubleToLongBits`, never `doubleToRawLongBits`.''' The raw variant is
    * too strict: measured on this machine, the literal `Double.NaN` is
    * `0x7ff8000000000000` while every `NaN` the hardware produces —
    * `0.0 / 0.0`, `Infinity * 0.0`, `Infinity - Infinity`, `sqrt(-1)` — is
    * `0xfff8000000000000`, x86's *real indefinite*, with the sign bit set. The
    * raw comparison therefore reports two correctly-computed `NaN`s as
    * different, and it fails on the assertion that needs it most.
    * `doubleToLongBits` canonicalises every `NaN` to one payload and still
    * separates `-0.0` from `+0.0`, which is exactly the discrimination this
    * project needs. Guide §38.
    */
  protected def sameBits(a: Double, b: Double): Boolean =
    java.lang.Double.doubleToLongBits(a) == java.lang.Double.doubleToLongBits(b)

  protected def assertSameBits(actual: Double, expected: Double, clue: String = ""): Unit =
    assert(
      sameBits(actual, expected),
      s"$clue: expected ${show(expected)}, got ${show(actual)} (bitwise)"
    )

  /** A rendering that distinguishes the two zeros, for failure messages. */
  protected def show(d: Double): String =
    if d.isNaN then "NaN"
    else if d == 0.0 && (1 / d) < 0 then "-0.0"
    else if d == 0.0 then "+0.0"
    else d.toString

  /** The seven values every soundness claim in Stage 5 is checked against.
    *
    * Both zeros, both infinities, `NaN`, and one ordinary value of each sign.
    * These are exactly where IEEE-754 stops behaving like the reals, and a rule
    * set validated only on `1.0` and `2.0` is validated on the inputs that
    * cannot fail. Guide §34.
    */
  protected val probeValues: List[(String, Double)] = List(
    "+0.0" -> 0.0,
    "-0.0" -> -0.0,
    "1.0" -> 1.0,
    "-1.0" -> -1.0,
    "+Inf" -> Double.PositiveInfinity,
    "-Inf" -> Double.NegativeInfinity,
    "NaN" -> Double.NaN
  )

  private val WarmDepth = 128
  private val WarmRounds = 200

  /** The largest `n` in `[0, limit)` for which `f(n)` survives, by binary
    * search, assuming survival is monotone in `n`.
    *
    * '''The warm-up is not an optimisation and removing it changes the
    * answer.''' A compiled frame is smaller than an interpreted one, so a
    * search entered cold converges while `f` is still migrating between tiers
    * and returns a number that was true for part of the search and false for
    * the rest. This guide's own first draft reported four ceilings of exactly
    * `2^k - 1` for that reason; they are not in the published tables.
    * `error-patterns.md`, Pattern 13.
    */
  protected def maxSurviving(limit: Int)(f: Int => Any): Int =
    (0 until WarmRounds).foreach(_ => survives(f(WarmDepth)): Unit)

    @annotation.tailrec
    def loop(lo: Int, hi: Int): Int =
      if lo >= hi - 1 then lo
      else
        val mid = (lo + hi) >>> 1
        if survives(f(mid)) then loop(mid, hi) else loop(lo, mid)
    loop(0, limit)

  /** Bytes allocated while evaluating `body`, after warm-up. */
  protected def probeBytes[A](body: => A): Long =
    (0 until 20).foreach(_ => body)
    AllocationProbe.measure(body)._2

  /** `((1 + 1) + 1) + ... + 1`, with `n` additions: `2n + 1` nodes, depth
    * `n + 1`, and every node but the leaves on the left spine.
    *
    * Built iteratively, because a builder that recursed would impose its own
    * ceiling on every ceiling this suite tries to measure.
    */
  protected def leftChain(n: Int): Expr =
    @annotation.tailrec
    def loop(i: Int, acc: Expr): Expr =
      if i == 0 then acc else loop(i - 1, Expr.Bin(Op.Add, acc, Expr.Lit(1.0)))
    loop(n, Expr.Lit(1.0))

  /** `1 + (1 + (1 + ... ))`, the mirror of [[leftChain]]. */
  protected def rightChain(n: Int): Expr =
    @annotation.tailrec
    def loop(i: Int, acc: Expr): Expr =
      if i == 0 then acc else loop(i - 1, Expr.Bin(Op.Add, Expr.Lit(1.0), acc))
    loop(n, Expr.Lit(1.0))

  /** Lex and parse, or fail the test with the error. For inputs the suite
    * asserts are well-formed.
    */
  protected def ast(input: String): Expr =
    // Written as nested matches rather than `tokenize(..).flatMap(parse)`,
    // because the two error types are unrelated and the flatMap would infer
    // `Either[LexError | ParseError, Expr]`. `Engine.compile` is where that
    // union is resolved properly, by translating each side into `EngineError`.
    Lexer.tokenize(input) match
      case Left(err) => fail(s"[$input] did not lex: ${err.toString}")
      case Right(ts) =>
        Parser.parse(ts) match
          case Left(err) => fail(s"[$input] did not parse: ${err.toString}")
          case Right(e) => e

  /** Lex, parse and evaluate against `env`, or fail the test. */
  protected def value(input: String, env: Eval.Env = Map.empty): Double =
    Eval.eval(ast(input), env) match
      case Right(v) => v
      case Left(err) => fail(s"[$input] did not evaluate: ${err.toString}")

  protected def tokens(input: String): MyList[Token] =
    Lexer.tokenize(input) match
      case Right(ts) => ts
      case Left(err) => fail(s"[$input] did not tokenise: ${err.toString}")

  protected def report(label: String, value: Any): Unit =
    println(s"  [observation] $label = ${value.toString}")

  /** Assert that `actual` is within `tolerance` (as a fraction) of `expected`. */
  protected def assertRatio(
      actual: Double,
      expected: Double,
      tolerance: Double,
      clue: String
  ): Unit =
    val low = expected * (1.0 - tolerance)
    val high = expected * (1.0 + tolerance)
    assert(
      actual >= low && actual <= high,
      s"$clue: expected about $expected (within ${tolerance * 100}%), got $actual"
    )

end Project1Harness
