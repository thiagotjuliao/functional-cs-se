# Mini-Project 1 — Algebraic Expression Engine & AST

Modules 1, 2 and 3 each removed one limit. This project is the first time all
three limits are removed *at once*, in one system, where a string comes in at
the top and a number comes out at the bottom and nothing in between is allowed
to mutate, throw, or run out of stack.

It is also the first time the code has to be *right about the real world*. A
persistent list is right when it satisfies its own laws. An expression engine is
right when it agrees with arithmetic — and arithmetic on a JVM is IEEE-754
`Double`, which is not the arithmetic you were taught. Part VII is where that
bill comes due.

Every number below was executed on this machine before it was written, through
this project's own test harness and the reference implementation it ships
against: JDK 21.0.9 HotSpot, 64-bit, compressed oops on, forked with `-Xmx2g
-XX:+UseG1GC`, warm, at the default `-Xss`, **with the suite running alone.**

Ceilings are reported as three independent searches rather than one, for two
reasons that both bit this guide's drafts. A single search taken while the JIT
is still promoting the method under test returns a number that was true for part
of the search and false for the rest — the first draft printed four ceilings of
exactly `2^k - 1` that way, and none of them survived a second look. And a
search taken while sbt runs other test classes in parallel in the same JVM
measures the contention, not the method: `§41` has the pair that differed by a
factor of six. Module 3's Pattern 13, twice.

---

## How To Read This Guide

Each Part depends only on the Parts before it. Sections are numbered
continuously, so any point can be cited as `Part V.26`.

| Part | What it covers | Unlocks |
| :--- | :--- | :--- |
| **I** | What an expression is once it stops being a string | — |
| **II** | The lexer: characters to tokens, in one tail recursion | S1 |
| **III** | The parser: precedence, associativity, and the two roles of `-` | S2 |
| **IV** | Traversing a tree when the stack is not available | S3 |
| **V** | Evaluation: the machine, its two stacks, and what it costs | S4 |
| **VI** | Algebraic simplification, and what makes a rewrite legal | S5 |
| **VII** | Six traps, five of which compile and pass a green suite | — |
| **VIII** | Where this lives: `scalac`, SQL planners, `-ffast-math` | — |
| **IX** | Scala 3: `enum`, `Either`, `@tailrec`, and pattern guards | — |

**Start with Part I even if you have written a parser before.** Part I is not
about parsing. It is about the one question the rest of the project answers
mechanically: *what is the tree, and why is there more than one candidate?*

The five stages are built in order and each consumes the type the one before it
produced. S1 has no dependencies; S5 needs all four.

---

# Part I — An Expression, Once It Stops Being A String

## 1. The Question This Project Exists To Answer

Here is a string:

```text
2 + 3 * 4
```

Here is a program that computes a number from it, left to right, one operator at
a time — the way you would with a pocket calculator:

```text
start with 2
then + 3   ->   5
then * 4   ->   20
```

And here is what the string means:

```text
2 + 3 * 4  =  14
```

Both were executed. The left-to-right fold returns `20.0`; the engine this
project builds returns `14.0`. Neither program has a bug in its arithmetic:
`2 + 3` really is `5` and `5 * 4` really is `20`. What the first program is
missing is not a calculation. It is a **structure** — the fact that `3 * 4` is a
single thing that `2` is added to, and that this fact is nowhere in the string.

**Everything in Parts I to III exists to recover that structure, and everything
in Parts IV to VI exists to use it without running out of stack.**

## 2. A String Is Not An Expression

`"2 + 3 * 4"` is nine characters:

```text
index   0   1   2   3   4   5   6   7   8
char   '2' ' ' '+' ' ' '3' ' ' '*' ' ' '4'
```

Nothing in that array says `*` binds tighter than `+`. Nothing says the spaces
are irrelevant. Nothing says `2` is one number rather than two things. A `String`
is a sequence of characters and a sequence has exactly one structure — the
order — which is the structure we already have and the one that is not enough.

The project therefore moves through three representations, each one carrying
strictly more information than the last:

```text
String          "2 + 3 * 4"                     order only
MyList[Token]   Num(2) Plus Num(3) Star Num(4)  order + what each piece IS
Expr            Bin(Add, Lit(2),                order + kind + STRUCTURE
                     Bin(Mul, Lit(3), Lit(4)))
```

The token list was executed and is exactly five tokens long: the four spaces are
gone, and `2` has become one `Num(2.0)` rather than a `Char`.

## 3. Two Trees For One String

Structure is what precedence *is*. `2 + 3 * 4` admits two trees, and the whole
of Part III exists to choose between them:

```text
   the tree we want                the tree the calculator built

        +                                   *
       / \                                 / \
      2   *                               +   4
         / \                             / \
        3   4                           2   3

   2 + (3 * 4) = 14                  (2 + 3) * 4 = 20
```

Both are well-formed. Both evaluate without error. They disagree by six. The
string does not choose; the **grammar** chooses, and the parser is the program
that applies the grammar.

Note what has already happened here: the parentheses in `2 + (3 * 4)` are a
feature of the *drawing*, not of the tree. The tree has no parentheses. It does
not need them, because a tree cannot be ambiguous — the shape *is* the grouping.
That is §4.

## 4. Concrete Syntax And Abstract Syntax

Two terms that are worth keeping straight for the rest of your career:

* **Concrete syntax** is what the user types. It has spaces, parentheses,
  and more than one spelling for the same meaning.
* **Abstract syntax** is what the compiler keeps. It has neither spaces nor
  parentheses, and each meaning has exactly one spelling.

```text
concrete                      abstract
-------------------------     ----------------------------------
"2 + 3 * 4"                   Bin(Add, Lit(2), Bin(Mul, Lit(3), Lit(4)))
"2+(3*4)"                     Bin(Add, Lit(2), Bin(Mul, Lit(3), Lit(4)))
"  2  +  3  *  4  "           Bin(Add, Lit(2), Bin(Mul, Lit(3), Lit(4)))
```

Three strings, one tree. That collapse is the parser's real output, and it is
why the type is called an **A**bstract **S**yntax **T**ree.

The collapse is lossy and deliberately so. Given only the tree you cannot
recover which of the three strings produced it — which is exactly why §40's
renderer is a genuine design problem rather than a formatting exercise.

## 5. The Grammar, Written Down

The grammar is five lines, and the parser in Part III is a transcription of it:

```text
expr    ::= term   (('+' | '-') term)*
term    ::= factor (('*' | '/') factor)*
factor  ::= unary  ('^' factor)?
unary   ::= '-' unary | atom
atom    ::= number | identifier | '(' expr ')'
```

Read it as a set of definitions, outermost first. An `expr` is one `term`,
followed by any number of `+`/`-` and another `term`. A `term` is one `factor`
followed by any number of `*`/`/`. And so on down to `atom`, which is the only
line that consumes a number.

Three properties are encoded in that shape, and all three are the point of Part
III:

* **Precedence** is *nesting depth*. `expr` is defined in terms of `term`, which
  is defined in terms of `factor`. The deeper a level sits, the tighter it binds,
  because it is resolved before its enclosing level ever sees a result.
* **Left associativity** is the `*` (repetition) on the `expr` and `term` lines.
  Repetition consumes operands one after another into an accumulator — a loop.
* **Right associativity** is the `('^' factor)?` on the `factor` line: the
  right-hand side is `factor` *again*, so `^` re-enters itself. That is a
  recursion, not a loop, and §14 is why the distinction is not cosmetic.

The grammar this project implements accepts:

```text
numbers       1, 42, 3.5, .5
identifiers   x, y, rate, x1   (letter or _, then letters, digits or _)
operators     + - * / ^
grouping      ( )
unary minus   -x, -2, - -3
```

and nothing else. No function calls; no comparison operators; no assignment.
The omissions are deliberate — every one of them adds a grammar rule without
adding a new *kind* of difficulty, and this project is sized by the second.

Four edge cases of that grammar, executed, because they are the ones a spec
forgets to pin:

```text
input    accepted?   parses to    why
------   ---------   ----------   -----------------------------------------
".5"     yes         0.5          a leading dot is a legal number
"1."     yes         1            so is a trailing one -- toDouble takes it
"_a"     yes         _a           an identifier may start with an underscore
"1e3"    NO          TrailingInput(Ident(e3), 1)
                                  scientific notation is NOT in this grammar.
                                  The number munch stops at 'e', which then
                                  lexes as an identifier, and §17's
                                  full-consumption check rejects the pair.
```

The last row is a limitation rather than a defect, and it is the kind that is
only a limitation once it is written down. What makes it safe is that it fails
loudly: the alternative lexer, the one that stops munching at `e` and says
nothing, returns `1` for `"1e3"`.

---

# Part II — The Lexer

> Builds **S1**. Everything in Part II is one function.

## 6. Why A Separate Pass

The parser could read characters directly. It would then be doing two jobs at
once: deciding that `'3'` `'.'` `'5'` is one number, *and* deciding that `3.5`
binds tighter than the `+` beside it. Those are different questions, and keeping
them apart is the single cheapest structural decision in the project.

The split has a name in every compiler textbook — **lexical analysis** then
**syntactic analysis** — and one concrete payoff you will feel immediately:
after the lexer runs, the parser never looks at a `Char` again. It matches on an
`enum` with seven cases, and the compiler checks its matches are exhaustive.
Match a `Char`, and it cannot.

## 7. Tokens As An ADT

A token is *what a piece of the string is*, with the spelling discarded:

```scala
enum Token:
  case Num(value: Double)
  case Ident(name: String)
  case Plus, Minus, Star, Slash, Caret, LParen, RParen
```

Executed, `"2 + 3 * 4"` produces exactly:

```text
Num(2.0)  Plus  Num(3.0)  Star  Num(4.0)
```

Five tokens from nine characters. Two things were thrown away and the difference
between them matters:

* **The spaces are gone forever.** Nothing downstream needs them, so discarding
  them is free.
* **The positions are gone too**, and that is *not* free. §10 is about what it
  costs and why this project pays it anyway.

Note that `Plus` carries no payload while `Num` carries a `Double`. That is an
ADT doing its job: the cases that differ in content have content, the cases that
differ only in identity have none, and a `Plus` cannot accidentally be compared
against a number because the compiler will not let the two meet.

## 8. Maximal Munch

When the scanner is sitting on a digit, how many characters does the number get?

The rule every real lexer uses is **maximal munch**: take the longest run of
characters that could still be part of this token. On `"12+3"` at index 0, the
scanner takes `1`, then `2`, then stops at `+` because `+` cannot continue a
number.

The obvious wrong first attempt is to take *one* character and let the parser
join them up later. Follow it through:

```text
"12 + 3"   one char at a time   ->  Num(1) Num(2) Plus Num(3)
```

and the parser now has to know that two adjacent `Num`s mean concatenation of
digits — which is the lexer's job, moved into the parser, where it collides with
the rule that two adjacent operands are a syntax error (§17). Maximal munch is
not an optimisation; it is what keeps the two passes separable at all.

Maximal munch also has a failure mode, and it is worth seeing now rather than in
Part VII. The scanner does not validate while it munches — it takes every
character that *could* belong, and asks afterwards whether what it took is a
number:

```text
input     munched    parses as Double?
-------   --------   -----------------
"1.5"     "1.5"      yes -> Num(1.5)
"1.2.3"   "1.2.3"    NO  -> MalformedNumber("1.2.3", 0)
```

Both were executed. The second is a lexical error, reported with the whole
munched run and the index it started at, and *not* silently truncated to `1.2`
with a stray `.3` left behind. §36 is the version of this that does not announce
itself.

## 9. The Scan As A Tail Recursion Over An Index

The lexer is the project's first contact with Module 3's rule: no `while`, no
`var`, and an input that may be a megabyte long.

The shape is the one from Module 3 §8 and §9 — an index and an accumulator, both
parameters, with the recursive call in tail position:

```scala
@tailrec
def loop(i: Int, acc: MyList[Token]): Either[LexError, MyList[Token]] =
  if i >= input.length then Right(acc.reverse)
  else ...  // classify input.charAt(i), then loop(i + k, token :: acc)
```

Two details decide whether this is correct rather than merely tail recursive:

* **`acc` is built in reverse and reversed once at the end.** Prepending is
  `O(1)` and appending to a cons list is `O(n)`, so appending would make the
  lexer quadratic — 10,000 tokens would cost 50 million cell traversals. Module
  2 §11 costs the append, and §17 measures the quadratic it hides.
* **`i` advances by more than one.** A number consumes as many characters as it
  munched. Advancing by a constant `1` after a multi-character token re-reads the
  digits as separate numbers, and the resulting token list is wrong without ever
  failing.

Executed: a 1,000,000-term input (`"1 + 1 + ... + 1"`, just over four million
characters) tokenises without exhausting the stack. That is the whole return on
writing it this way.

## 10. Errors As Values, And Where The Position Goes

`throw` is forbidden by §D of the checklist, so a lexer that meets an
unrecognised character must return one:

```scala
enum LexError:
  case UnexpectedChar(ch: Char, at: Int)
  case MalformedNumber(text: String, at: Int)
```

Executed, on inputs chosen to hit each case:

```text
input     result
-------   ------------------------------
"1 $ 2"   UnexpectedChar($, 2)
"1.2.3"   MalformedNumber(1.2.3, 0)
```

The `at` is the index in the *original string*, and it is the only place in the
whole pipeline where a character offset is available. Once `tokenize` returns,
the offsets are gone: the parser sees `MyList[Token]` and can report at best a
*token* index.

```text
input      error                        the index means
--------   --------------------------   ---------------------
"1 $ 2"    UnexpectedChar($, 2)         character 2 of the input
"1 + + 2"  UnexpectedToken(Plus, 2)     token 2 of the token list
```

Both executed. The two `2`s are not the same `2`, and a message that printed
`"column 2"` for the second would be lying — token 2 of `"1 + + 2"` starts at
character 4.

**This is a real design debt and the project takes it on purpose.** Carrying a
position on every token would fix the message and would put a field into the one
type the parser pattern-matches on thousands of times, making every pattern in
Part III noisier for a benefit that only surfaces in error text. §G asks you to
defend the trade, not to assume it.

---

# Part III — The Parser

> Builds **S2**. This is the hardest Part, and §11 is why it is hard at all.

## 11. The Obvious Wrong First Attempt

Before precedence, try the thing that obviously works: walk the token list left
to right, keeping a running value.

```scala
@tailrec def fold(acc: Double, ts: MyList[Token]): Double = ts match
  case Cons(op, Cons(Token.Num(b), rest)) => fold(applyOp(op, acc, b), rest)
  case _                                  => acc
```

It is short, it is tail recursive, it allocates nothing, and it was executed:

```text
"2 + 3 * 4"   fold left to right   ->  20.0
"2 + 3 * 4"   what it means        ->  14.0
```

It is wrong, and the reason it is wrong is worth stating precisely, because the
correct parser is built out of the repair. The fold commits to combining `2` and
`3` **before it has looked at `*`**. By the time the `*` is in hand, the `2 + 3`
has already happened and cannot be taken back.

A parser therefore cannot be a left fold over the tokens. It has to be able to
say: *I am holding a `+`, but I will not apply it yet, because what follows may
bind tighter.* Every technique in the rest of Part III is a way of saying that.

## 12. One Function Per Precedence Level

The first correct technique is a direct transcription of the grammar in §5: one
function per line, each calling the next one down.

```scala
def expr(ts):   parse a term, then while the next token is + or -, parse another
def term(ts):   parse a factor, then while the next token is * or /, parse another
def factor(ts): parse a unary, then if the next token is ^, parse another factor
def unary(ts):  if the next token is -, parse a unary; otherwise parse an atom
def atom(ts):   a number, an identifier, or ( expr )
```

Why this produces the right tree for `2 + 3 * 4`, step by step:

```text
expr      calls term for the left operand
  term    calls factor -> 2 ; next token is + , not * or / , so term RETURNS 2
expr      sees + , calls term for the right operand
  term    calls factor -> 3 ; next token is * , so term keeps going
  term    calls factor -> 4 ; next token is end , term RETURNS Bin(Mul, 3, 4)
expr      builds Bin(Add, 2, Bin(Mul, 3, 4))
```

The `*` is consumed by `term`, which sits *below* `expr` and therefore finishes
first. Precedence is nesting depth, exactly as §5 said. Nothing else is doing
any work.

This technique is called **recursive descent**, and it is what `scalac`,
`javac`, `gcc` and the Go compiler all use for their expression grammars. It is
not a teaching toy.

## 13. Left Associativity Is A Loop

`10 - 3 - 2` has two readings and only one is arithmetic:

```text
(10 - 3) - 2  =  5      left associative   <- what - means
10 - (3 - 2)  =  9      right associative
```

Both executed. Now look at where each one comes from in the code. Inside `expr`,
after parsing the first `term`, the natural-looking implementation is:

```scala
// WRONG for a left-associative operator
def expr(ts) =
  val (lhs, rest) = term(ts)
  if next(rest) is + or - then
    val (rhs, rest2) = expr(drop1(rest))   // <- the whole rest, recursively
    (Bin(op, lhs, rhs), rest2)
  else (lhs, rest)
```

That builds `10 - (3 - 2)`, because `rhs` is everything to the right. The fix is
not a fix to the arithmetic; it is a change of *control shape*:

```scala
// RIGHT: an accumulator, consumed one operand at a time
@tailrec def more(lhs, ts) =
  if next(ts) is + or - then
    val (rhs, rest) = term(drop1(ts))       // <- one term, not the rest
    more(Bin(op, lhs, rhs), rest)           // <- the tree so far becomes the new lhs
  else (lhs, ts)
```

The accumulator `lhs` is the partially built tree, and each iteration wraps it
one level deeper on the **left**. This is Module 3 §9's `while`-to-`@tailrec`
transformation arriving in a place where it changes the answer rather than the
stack profile: the loop *is* left associativity.

And it buys the stack profile too. Executed, parsing `1 + 1 + ... + 1`:

```text
terms          parses?
-----------    -------
200,000        yes
2,097,151      yes  -- no ceiling found below the search limit
```

Two million left-associated additions parse in constant stack, because the
`expr` level never re-enters itself. Compare §18.

## 14. Right Associativity Is The Recursion

`^` is the other way round, and it is the reason the grammar's `factor` line
looks different from the other two:

```text
2 ^ 3 ^ 2  =  2 ^ (3 ^ 2)  =  2 ^ 9   =  512      right associative  <- what ^ means
              (2 ^ 3) ^ 2  =  8 ^ 2   =  64       left associative
```

Both executed. The difference is a factor of eight, and there is no test over
small inputs that catches it: `2 ^ 2 ^ 2` is `16` under both readings.

In code, right associativity is the *absence* of the accumulator loop:

```scala
def factor(ts) =
  val (lhs, rest) = unary(ts)
  if next(rest) is ^ then
    val (rhs, rest2) = factor(drop1(rest))   // <- factor calls ITSELF
    (Bin(Pow, lhs, rhs), rest2)
  else (lhs, rest)
```

`factor` re-enters `factor` for the right-hand side, so the innermost `^` is the
one that finishes first and ends up deepest in the tree — which is what
right-nesting means. The recursion is not incidental here; it is the mechanism,
and it cannot be made tail recursive without changing the answer, for the same
reason Module 3 §13's `foldRight` cannot.

That is a real cost, paid in stack. §18 measures it.

## 15. Unary Minus: One Symbol, Two Roles

`-` appears in the token list as a single `Token.Minus` and means two unrelated
things:

```text
"10 - 3"    infix    a binary operator with two operands
"-3"        prefix   a unary operator with one operand
"2 ^ -1"    prefix   again, and in a position where infix is impossible
```

The parser tells them apart **by position, not by the token**. A `-` appearing
where an operand was expected is prefix; a `-` appearing where an operator was
expected is infix. That is why `unary` sits in the grammar between `factor` and
`atom`: `atom` is the operand position, and `unary` is the only rule that reads a
`-` there.

Then the question that has an answer most people get wrong:

```text
-2 ^ 2   =  ?
```

Executed, under the grammar of §5: `-4.0`. The tree is `Neg(Bin(Pow, 2, 2))` —
`-(2^2)`, not `(-2)^2`. Written out:

```text
spelling     tree                       value
----------   ------------------------   -----
-2 ^ 2       Neg(Pow(Lit 2, Lit 2))      -4
(-2) ^ 2     Pow(Neg(Lit 2), Lit 2)       4
```

Both executed, and both are four characters apart. The convention that unary
minus binds **looser than `^` and tighter than `*`** is not this project's
invention: it is what mathematical notation means, what Python means
(`-2**2 == -4`), and what a spreadsheet does *not* mean (Excel returns `4`,
famously). §39 is where that costs someone money.

The grammar encodes it in exactly one place — `unary` sits above `atom` and
below `factor` — which is why a single line moved changes every `-x ^ y` in the
system.

`- -3` is `3.0`, executed. Two `Neg` nodes, and §33's rule set collapses them.

## 16. Precedence Climbing: The Levels As Data

Five functions for five levels works, and it stops working the moment there are
twelve levels, which is what a real language has. The standard refactor turns
the *number* of levels from code into data:

```scala
def prec(t: Token): Int = t match
  case Plus | Minus => 1
  case Star | Slash => 2
  case Caret        => 3
  case _            => -1

def rightAssoc(t: Token): Boolean = t == Caret
```

and then one loop replaces `expr`/`term`/`factor`:

```scala
@tailrec def climb(lhs: Expr, ts: MyList[Token], minPrec: Int) = ts match
  case Cons(t, rest) if prec(t) >= minPrec =>
    val nextMin = if rightAssoc(t) then prec(t) else prec(t) + 1
    val (rhs, r2) = parseAt(rest, nextMin)       // recursion, at a higher floor
    climb(Bin(opOf(t), lhs, rhs), r2, minPrec)   // loop, at the same floor
  case _ => (lhs, ts)
```

The `+ 1` is the entire associativity mechanism, and it is worth staring at:

```text
operator   prec   nextMin   effect on a second operator of the SAME precedence
--------   ----   -------   -------------------------------------------------
+          1      2         prec(+) = 1 < 2, so the inner parse STOPS.
                            The outer loop takes it -> left associative.
^          3      3         prec(^) = 3 >= 3, so the inner parse CONTINUES.
                            It takes it itself -> right associative.
```

One integer decides which of §13 and §14 you get. This is the technique Pratt
published in 1973 and it is what `rust-analyzer`, `clang` and most modern
hand-written parsers use.

**Both designs are acceptable for S2.** Five functions is clearer to read the
first time; `climb` is what survives a grammar growing. Choose, and defend the
choice in §G.

## 17. What The Parser Must Reject

A parser that only accepts is half a parser. These were executed:

```text
input       result
---------   --------------------------------
""          UnexpectedEnd
"1 + "      UnexpectedEnd
"1 + + 2"   UnexpectedToken(Plus, 2)
"()"        UnexpectedToken(RParen, 1)
"(1 + 2"    UnexpectedEnd
"1 + 2)"    TrailingInput(RParen, 3)
"1 2"       TrailingInput(Num(2.0), 1)
```

Two of these are subtler than they look:

* **`"1 + 2)"` is not an error the recursive descent notices.** `expr` parses
  `1 + 2` perfectly, returns, and leaves a `)` unconsumed. Nothing inside the
  parser is unhappy. The error exists only at the **top level**, where the
  contract is that the token list must be *fully* consumed. A parser that forgets
  that check accepts `"1 + 2)"`, `"1 2"` and `"1 + 2 rubbish"` and returns the
  tree for the prefix.
* **`"1 2"` is the same defect wearing different clothes**, and it is the one
  that catches people, because there is no stray bracket to notice. Two adjacent
  operands. `TrailingInput` is what separates a parser from a prefix-matcher.

## 18. The Parser's Own Stack Ceiling

The parser is a recursion, so it has a ceiling, and the ceiling depends on which
grammar rule is doing the nesting. Measured on the reference implementation with
this project's own test harness, three searches per row, two JVM launches:

```text
input shape                  max depth that parses
--------------------------   -----------------------------------------
1 + 1 + ... + 1              no ceiling below 2,000,000
( ( ( ... 1 ... ) ) )        12,331  12,327  12,329   |  12,338  12,337  12,337
2 ^ 2 ^ ... ^ 1              10,275  10,275  10,275   |  10,282  10,282  10,282
- - - ... - 1                 8,808   8,808   8,808   |   8,813   8,813   8,813
```

Read the first row first, and note that it is not a large number — it is the
*absence* of one. Left-associated addition never re-enters its own level (§13),
so two million terms cost one frame.

The other three re-enter, and the ranking follows one thing only: **how many
frames a grammar rule costs per level of nesting.** In the reference
implementation, which uses §16's `climb`:

```text
nesting        the call chain per level                  frames   ceiling
------------   ---------------------------------------   ------   -------
( expr )       atom -> expr                                 2      12,330
2 ^ _          climb -> expr -> atom                     2 to 3    10,278
- _            atom -> unary -> atom -> climb            3 to 4     8,810
```

The spread is only 40% — 12,330 / 8,810 = 1.40 — and the ordering is a fact
about *that* implementation, not about the grammar. A five-function recursive
descent (§12) pays `expr`→`term`→`factor`→`unary`→`atom` for every `(`, five
frames instead of two, and moves parentheses from the cheapest row to the
dearest. **Two correct parsers for one grammar, with the ceilings in the
opposite order.** Which is why the spec asserts a floor and reports the numbers,
rather than asserting the ranking.

**This ceiling is a documented limitation of the project and is not removed.**
Nothing in Parts IV to VI helps: those Parts make traversals of an
already-built tree stack-safe, and this is the *construction*. Making the parser
itself stack-safe means rewriting it as an explicit machine over a state stack,
which is what production parsers do and what §G asks you to describe rather than
build.

What *is* required is that you know the number. A system whose parser dies at
twelve thousand nested parentheses and whose evaluator survives ten million
nodes has one ceiling, not none, and §E is where you write it down.

---

# Part IV — Traversing A Tree Without The Stack

> Builds **S3**. The technique in this Part is the project's central idea, and
> it is learned here on `size`, where it is easy, so that §26 can use it on
> `eval`, where it is not.

## 19. Why `size` Is The Right Place To Learn This

`size` counts the nodes of a tree. It is four lines:

```scala
def sizeNaive(e: Expr): Int = e match
  case Lit(_) | Var(_)  => 1
  case Neg(a)           => 1 + sizeNaive(a)
  case Bin(_, l, r)     => 1 + sizeNaive(l) + sizeNaive(r)
```

It is obviously correct, it cannot be wrong about arithmetic, and it has exactly
the same fatal property as the evaluator:

```text
Shape.sizeNaive, deepest tree it survives   30,843   30,845   30,845
```

Measured, three searches. Above about thirty thousand nodes of depth it
throws `StackOverflowError` — no wrong answer, no warning, no type error. Module
3 §1 and §3's ceiling, on a tree instead of a list.

Learn the repair here, where the only thing that can go wrong is a count.

## 20. The Shape Of The Problem

Module 3's answer to a stack ceiling was `@tailrec`, and it does not apply. Put
the annotation on `sizeNaive` and the compiler rejects it, correctly:

```text
1 + sizeNaive(l) + sizeNaive(r)
    ^^^^^^^^^^^^   ^^^^^^^^^^^^
    two calls, and an addition pending after both
```

A tail call is a call whose value is handed straight back (Module 3 §5). Here
there are **two** recursive calls and the `+` is waiting on both. Even one of
them could not be in tail position, because the other one has to run afterwards.

This is not a limitation of the annotation. It is the structure of the problem:
a tree has two children and a function can only return once. Something has to
remember *"when you come back, there is still a right child to do."* In
`sizeNaive`, that something is the JVM frame. The repair is to make it a value
instead.

## 21. The Explicit Stack, First Version

`size` is the easy case because the results combine with `+`, which is
associative and commutative, so the order the nodes are visited does not matter.
That means the pending work can be a plain list of subtrees:

```scala
@tailrec
def loop(stack: MyList[Expr], acc: Int): Int = stack match
  case Nil                     => acc
  case Cons(Lit(_) | Var(_), r)=> loop(r, acc + 1)
  case Cons(Neg(a), r)         => loop(Cons(a, r), acc + 1)
  case Cons(Bin(_, l, rr), r)  => loop(Cons(l, Cons(rr, r)), acc + 1)
```

Worked, on `Bin(Add, Lit(1), Bin(Mul, Lit(2), Lit(3)))` — five nodes:

```text
step  stack                                acc
----  -----------------------------------  ---
0     [ Bin(Add, 1, Bin(Mul, 2, 3)) ]        0
1     [ Lit(1), Bin(Mul, 2, 3) ]             1
2     [ Bin(Mul, 2, 3) ]                     2
3     [ Lit(2), Lit(3) ]                     3
4     [ Lit(3) ]                             4
5     [ ]                                    5
```

The JVM frame stack is now one frame deep for the whole traversal, and the
*heap* holds the pending work. That is the trade in one sentence: **a recursion
that cannot be a loop becomes a loop by moving its frames onto the heap.**

Executed: this version walks a 1,000,000-deep tree — 2,000,001 nodes — and
returns `2000001`.

## 22. When A Traversal Must Come Back

`size` got away with a list of subtrees because addition does not care about
order. `depth` does:

```text
depth(Bin(op, l, r)) = 1 + max(depth(l), depth(r))
```

The `max` needs *both* children's answers, at the same time, and it needs to
know which node they belong to. A flat stack of subtrees loses that: by the time
the right child is popped, nothing records that its result must be combined with
a particular left child's.

Two repairs exist, and knowing which one a problem needs is most of the skill:

* **Carry the extra information alongside the node.** For `depth`, push
  `(Expr, Int)` — the subtree and the depth at which it sits — and keep a
  running maximum. No combining step is needed at all, because the depth of a
  node depends only on where it *is*, not on what its children return.
* **Push an instruction that says what to do on the way back up.** This is the
  general answer, it is what §26 needs, and it is §23.

`depth` is deliberately the first kind: it is solvable without instructions, and
finding that out is the exercise.

Executed: `depth` of the 1,000,000-deep left chain is `1000001`.

## 23. Instructions: The General Technique

When results genuinely must be combined after both children return, the pending
stack stops holding subtrees and starts holding a small instruction set:

```scala
private enum Instr:
  case Push(e: Expr)      // "visit this subtree"
  case Combine(op: Op)    // "two results are ready; apply op to them"
  case Negate             // "one result is ready; flip its sign"
```

`Push(Bin(op, l, r))` expands into three instructions — visit `l`, visit `r`,
then combine — and the `Combine` is the frame that the JVM used to hold,
written down as a value.

This is the same move Module 3 §14 made when it replaced `foldRight`'s frames
with an explicit reversal, and the same one Block 3 will make at scale when
`IO`'s interpreter replaces a call chain with a heap-allocated record of what to
do next. The name for it there is **trampolining**; the technique is this one.

## 24. The Four Ceilings, Measured

Every traversal in this project is a tree recursion and therefore starts with a
ceiling. Measured before the repair, three searches each:

```text
traversal              deepest tree it survives          after the repair
--------------------   -------------------------------   ----------------
sizeNaive              30,843  30,845  30,845            no ceiling at 10^6
evalNaive              24,673  24,675  24,675            see Part V
parse, nested parens   12,331  12,327  12,329            not removed (§18)
render  (recursive)     2,047  12,339  12,339            not required
```

Four observations, and the reason this table is in the guide rather than only in
the checklist is the second:

* **The ceilings spread by a factor of 2.5** across functions that all "recurse
  once per node" — 30,843 for `sizeNaive` down to 12,339 for `render`, with
  `evalNaive` 25% below `sizeNaive` in between. Frame size is a property of the
  method — its locals, its parameters, its intermediate values (Module 3 §3) —
  so a traversal that builds strings does not cost what one that adds integers
  costs, and neither number can be predicted from the other.
* **Look at `render`'s first column.** `2,047`, then `12,339` twice, in one run,
  reproducibly, on the same method over the same inputs — a factor of six
  between the first search and the second. The harness warms its subject 200
  times at depth 128 before searching, and for `render` that is not enough: the
  first search's early probes are half a million levels deep and exercise code
  paths — string building, and the allocation that comes with it — that a
  128-deep warm-up never reaches. **The first number of the three is the one to
  distrust**, and it is in the table rather than quietly dropped because that is
  what Module 3's Pattern 13 looks like when you catch it.
* **None of them is a round number**, and none of them is stable across
  machines. Every number here is a fact about this JVM at this `-Xss`, which is
  why §E asks for *your* numbers and why the specs assert **separation** rather
  than absolute depth.
* **`render` is left recursive on purpose.** It is not on the list of things S3
  must make stack-safe, because a 12,000-deep expression has no readable string
  form anyway. Knowing which ceilings to remove and which to document is §G's
  question.

---

# Part V — Evaluation

> Builds **S4**.

## 25. The Naive Evaluator, And Its Ceiling

```scala
def evalNaive(e: Expr, env: Env): Either[EvalError, Double] = e match
  case Lit(v)       => Right(v)
  case Var(n)       => env.get(n).toRight(EvalError.Unbound(n))
  case Neg(a)       => evalNaive(a, env).map(v => -v)
  case Bin(o, l, r) => evalNaive(l, env).flatMap(a => evalNaive(r, env).map(b => applyOp(o, a, b)))
```

Correct on everything it survives, and measured at `24,673 / 24,675 / 24,675`
nodes of depth.

**Keep this function.** It is the experimental control: the machine in §26 is
only demonstrably equivalent because there is something to compare it against,
and every correctness property in `S4Spec` is asserted on *both*, on every input
small enough for this one.

One measured surprise worth carrying into §G. A left-leaning chain and a
right-leaning chain of the same depth give the **same** ceiling: `24,673` and
`24,675`, agreeing to four digits.
The `flatMap`/`map` chain makes the two sides look asymmetric in the source, and
they are not: both sides recurse to full depth before anything combines.

## 26. The Evaluation Machine

Two stacks, both on the heap, one tail-recursive loop:

```scala
@tailrec
def loop(work: MyList[Instr], vals: MyList[Double]): Either[EvalError, Double] =
  work match
    case Nil                            => Right(vals.head)
    case Cons(Push(Lit(v)), w)          => loop(w, Cons(v, vals))
    case Cons(Push(Var(n)), w)          => env.get(n) match
                                             case Some(v) => loop(w, Cons(v, vals))
                                             case None    => Left(Unbound(n))
    case Cons(Push(Neg(a)), w)          => loop(Cons(Push(a), Cons(Negate, w)), vals)
    case Cons(Push(Bin(o, l, r)), w)    => loop(Cons(Push(l), Cons(Push(r), Cons(Combine(o), w))), vals)
    case Cons(Negate, w)                => loop(w, Cons(-vals.head, vals.tail))
    case Cons(Combine(o), w)            => // see §27
```

Worked, on `2 + 3 * 4`:

```text
step  work                                    vals
----  --------------------------------------  ----------
0     [Push(2 + 3*4)]                         []
1     [Push(2), Push(3*4), Combine(Add)]      []
2     [Push(3*4), Combine(Add)]               [2]
3     [Push(3), Push(4), Combine(Mul),        [2]
       Combine(Add)]
4     [Push(4), Combine(Mul), Combine(Add)]   [3, 2]
5     [Combine(Mul), Combine(Add)]            [4, 3, 2]
6     [Combine(Add)]                          [12, 2]
7     []                                      [14]
```

`14.0`, and the JVM stack never went past one frame. This is a **stack machine**,
and the `work` list is a program: steps 1 to 7 are, precisely, the bytecode a
JVM would run for the same expression.

Executed: `1,000,000` and `10,000,000` deep both evaluate. The million-deep left
chain returns `Right(1000001.0)`, which is the right answer — it is
`1 + 1 + ... + 1` with 1,000,001 ones.

## 27. The Order The Operands Come Off

`vals` is a stack, so the **second** operand is on top:

```text
Combine(o):
  b = vals.head        <- the RIGHT operand, pushed last
  a = vals.tail.head   <- the LEFT operand
  result = applyOp(o, a, b)
```

Reverse those two and `10 - 3 - 2` returns a wrong number instead of an error.
Worked, on `10 - 3`:

```text
correct   a=10, b=3   ->  10 - 3  =  7
reversed  a=3,  b=10  ->   3 - 10 = -7
```

The defect survives every test whose operators are commutative. `2 + 3` is `5`
either way; `2 * 3` is `6` either way. It takes a `-`, a `/` or a `^` to see it,
which is why `S4Spec` asserts on all three and why §40's error-pattern entry
exists.

Note also which order the children were *pushed*: `Push(l)` then `Push(r)`, so
`l` is popped and evaluated **first**, and its value sits underneath. Push them
the other way round and the two reversals cancel out — which is worse than
either defect alone, because the suite goes green and the evaluation order is
now right-to-left, observable the moment Block 3 makes evaluation effectful.

## 28. Errors Inside A Tail Recursion

`Left(Unbound(n))` returns *from inside the loop*, abandoning whatever is still
on `work`. That is correct — an unbound variable cannot be recovered from — and
it is worth noticing that the short-circuit costs nothing structurally: the
method still satisfies `@tailrec`, because `Left(...)` is a return, not a call.

Executed, end to end:

```text
input     env = {x -> 3}        result
-------   -------------------   ------------------------
"x + 1"   x bound               OK: x + 1 = 4.0
"z + 1"   z not bound           parsed, then Unbound(z)
```

The second line is the shape of the whole pipeline: three passes each of which
can fail with a *different* error type, composed so that the first failure wins
and the rest never runs. That is `Either`'s `flatMap`, and it is the entire
error-handling design — no exceptions, no `null`, no sentinel `NaN`.

## 29. What The Machine Costs

The machine is not free, and a guide that showed only the ceiling would be
selling something. Measured on the same 20,001-node tree, warm, with the
allocation counter from Module 1's `AllocationProbe`:

```text
evaluator            bytes allocated   per node
------------------   ---------------   --------
evalNaive (Either)           800,040      40.00
machine (MyList)           2,160,128     108.00
                                         ------
                                         2.70x
```

And for scale, the tree being evaluated:

```text
the AST itself, 200,001 nodes            4,800,024      24.00
```

Three readings of that table:

* **An AST node costs exactly 24 bytes** — 12-byte object header, plus either a
  `Double` field (8 bytes) or three compressed references (12 bytes), aligned to
  8. Module 1 §4's layout rules, applied to `enum` cases.
* **The machine allocates 4.5x the tree, transiently**, every evaluation: one
  `Instr` and one cons cell per visit, plus a boxed `Double` per value pushed.
  All of it dies immediately, so it is G1 young-generation churn of exactly the
  kind Module 1 measured — cheap, but not zero, and it scales with the input.
* **The naive evaluator is not free either.** Its 40 bytes per node is the
  `Right` wrapper `Either` allocates at every level. A version returning a bare
  `Double` allocates nothing at all and cannot report an unbound variable; that
  is the trade §G asks about.

The honest summary: **the machine trades heap for stack.** It does not make
evaluation cheaper, it makes it *possible* on inputs the naive version cannot
reach at all. Choosing it for a three-node expression is a pessimisation, and a
production engine dispatches on depth — which nothing in this project does, and
§G asks why not.

---

# Part VI — Algebraic Simplification

> Builds **S5**. This Part is where the project stops being about trees and
> starts being about *arithmetic*, which is where it gets dangerous.

## 30. Constant Folding

If both operands of a node are literals, the node can be replaced by its value
before evaluation ever runs:

```scala
case Bin(o, Lit(a), Lit(b)) => Lit(applyOp(o, a, b))
```

Executed:

```text
"2 * 3 + 4 * 5"   ->  26        AST size 7 -> 1
```

Seven nodes become one. This is the oldest optimisation in compilers and it is
also, by itself, **not a simplifier** — which is §31.

## 31. Why Folding Alone Is Not Simplification

Fold a *closed* term — one with no variables — and you have simply evaluated it.
`(3 * 1 + 0) * 2` folds to `6`, and so does calling `eval` on it. The simplifier
and the evaluator become the same function with different return types, and every
identity rule in §32 is unreachable code.

Simplification only means anything over an **open** term, one containing a
symbol whose value is not known yet:

```text
source        (x * 1 + -0.0) * (2 ^ 1) + (4 - 2) * 1
              AST size 16, depth 5

simplified    x * 2 + 2
              AST size 5, depth 3
```

Executed: the value is preserved (`8.0` at `x = 3`), the rewrite is idempotent,
and the tree is 69% smaller. The `x` cannot be folded — it has no value at
rewrite time — so every reduction in that example came from an identity rule
acting *around* an unknown.

**This is why `Var` is in the AST at all.** It carries no arithmetic and adds one
line to each traversal, and without it Part VI is empty.

## 32. The Identity Rules As Rewrites

Each rule is one pattern-match case, and each is a claim of the form *"this tree
and that tree compute the same number for every possible value of `x`."*

```text
rule                     reads as
----------------------   --------------------------------------
Neg(Lit(v))   -> Lit(-v)  fold a negation of a literal
Neg(Neg(e))   -> e        two negations cancel
Bin(o,Lit,Lit)-> Lit      constant folding (§30)
x * 1         -> x        1 is the multiplicative identity
1 * x         -> x
x / 1         -> x
x ^ 1         -> x
x ^ 0         -> 1
x - 0.0       -> x
x + (-0.0)    -> x        NOT x + 0.0 -- see §36
```

Nine of those ten are the rules you would write from memory. The tenth is
spelled with a **negative** zero and is the subject of Part VII.

## 33. One Bottom-Up Pass Reaches The Fixed Point

A rewriter that applies rules top-down has to iterate: rewriting a node can
expose a new opportunity at its parent, so you re-run until nothing changes, and
then you have to argue that the loop terminates.

Applying them **bottom-up** avoids the whole question. When a node is rewritten,
its children have already been rewritten and are already in normal form. Every
rule's output is either a child (already normal) or a fresh `Lit` (trivially
normal), so the output of one pass is a fixed point.

Worked, on `(1 + 0.0) * 1`, bottom-up:

```text
visit Lit(1)        -> Lit(1)
visit Lit(0.0)      -> Lit(0.0)
rebuild 1 + 0.0     -> Lit(1)      by constant folding
visit Lit(1)        -> Lit(1)
rebuild Lit(1) * 1  -> Lit(1)      by x * 1 -> x
```

One traversal, no iteration. Executed: `simplify(simplify(e)) == simplify(e)` on
the §31 example, and asserting exactly that is how `S5Spec` checks you did not
accidentally write a rule whose output re-enables itself.

The traversal must be stack-safe, for the reason Part IV gave: a rewriter that
dies at thirty thousand nodes has moved the ceiling, not removed it. It needs
the instruction technique of §23 in its "rebuild" form. Executed: a 1,000,000-
deep tree simplifies without overflow.

## 34. What Makes A Rewrite Legal

A rewrite is legal when the two trees compute the same value **for every input**,
including the inputs nobody tests with. The definition is unremarkable; what is
remarkable is how many familiar rules fail it.

The check is mechanical. Take a representative set of `Double` values — one that
must include both zeros, both infinities and `NaN`, because those are exactly
where IEEE-754 stops behaving like the reals — and compare the two sides
**bitwise**, not with `==`:

```scala
def same(a: Double, b: Double): Boolean =
  java.lang.Double.doubleToRawLongBits(a) == java.lang.Double.doubleToRawLongBits(b)
```

`==` is the wrong instrument here and §38 is why. Run that check over the ten
rules of §32 and every one passes. Run it over the four rules that are *not* in
§32 and none of them does. That is Part VII.

## 35. The Sound Set And The Fast Set

Real compilers face exactly this and answer it with a flag. `gcc -ffast-math`
and `clang -Ofast` enable rewrites that are true in the reals and false in
IEEE-754, on the grounds that most programs do not care and the speed is worth
it. Programs that *do* care — anything doing interval arithmetic, or summing
signed quantities, or checking for `NaN` — break silently.

This project ships both, named and separated:

```scala
def simplify(e: Expr): Expr        // the sound set of §32, always safe
def simplifyFast(e: Expr): Expr    // the sound set plus §37's four rewrites
```

Executed, the difference on one term:

```text
"x + 0.0"   under the sound set  ->  x + 0
"x + 0.0"   under the fast set   ->  x
```

The sound simplifier leaves `+ 0` in the tree. That looks like a failure and is
the correct behaviour, and §36 is the proof.

---

# Part VII — Traps

> Six. The first three are the same trap seen from three angles, and together
> they are the most valuable thing in this project.

## 36. The Additive Identity Of `Double` Is `-0.0`

`x + 0 = x` is the first algebraic identity anyone learns. It is false for
`Double`. Measured, over seven representative values, comparing bitwise:

```text
x        x + 0.0     x + (-0.0)    x - 0.0      x - (-0.0)
------   ---------   -----------   ----------   ----------
+0.0     +0.0        +0.0          +0.0         +0.0
-0.0     +0.0    <-- -0.0          -0.0         +0.0    <--
1.0      1.0         1.0           1.0          1.0
-1.0     -1.0        -1.0          -1.0         -1.0
+Inf     Infinity    Infinity      Infinity     Infinity
-Inf     -Infinity   -Infinity     -Infinity    -Infinity
NaN      NaN         NaN           NaN          NaN
```

The two marked cells are the whole finding. `(-0.0) + 0.0` is `+0.0`, because
IEEE-754 round-to-nearest says a sum of two zeros of opposite sign is `+0.0`.
So rewriting `x + 0.0` to `x` changes the answer when `x` is `-0.0`.

The verdicts, computed rather than reasoned:

```text
x + 0.0    -> x        unsound at -0.0
0.0 + x    -> x        unsound at -0.0
x + (-0.0) -> x        SOUND
(-0.0) + x -> x        SOUND
x - 0.0    -> x        SOUND
x - (-0.0) -> x        unsound at -0.0
```

**The additive identity of `Double` is `-0.0`.** Adding negative zero preserves
every value including both zeros; adding positive zero does not. The rule you
would have written from memory is the wrong one of the pair, and the right one
looks like a typo.

Consequences for the code, both of which are `S5Spec` assertions:

* `Lit(0.0)` and `Lit(-0.0)` are **different literals** and the rewriter must
  tell them apart. `v == 0.0` is `true` for both (§38), so the guard has to read
  the sign bit: `doubleToRawLongBits(v) == doubleToRawLongBits(-0.0)`.
* The renderer must print them differently, or a round trip through text loses
  the distinction (§40).

## 37. `x * 0` Is Not `0`

The same check over the other three rules everyone writes from memory:

```text
x * 0.0    -> 0.0      unsound at -0.0, -1.0, +Inf, -Inf, NaN
x - x      -> 0.0      unsound at +Inf, -Inf, NaN
0.0 - x    -> -x       unsound at +0.0, NaN
x / x      -> 1.0      unsound at +0.0, -0.0, +Inf, -Inf, NaN
```

`x * 0 -> 0` fails on **five of seven** representative values. Executed, end to
end through the engine:

```text
x = Infinity    "x * 0.0"  evaluated       ->  NaN
                after the x * 0 -> 0 rewrite ->  0.0
```

A rewrite that turns `NaN` into `0.0` is not an optimisation, it is a change of
answer, and in a system that checks `if result.isNaN then reject` it is the
difference between rejecting bad input and accepting it.

And one rule that *is* sound despite looking like a sibling of the others:

```text
x ^ 0.0    -> 1.0      SOUND
```

Because `Math.pow` specifies `pow(x, ±0.0) == 1.0` for **every** `x`, `NaN`
included — verified: `pow(NaN, 0.0)` is `1.0`. Two rules that look equally
suspicious, and exactly one of them is safe. Guessing is not a method.

## 38. `==` Cannot See The Divergence

This is the trap that makes the previous two dangerous rather than merely
interesting. Executed:

```text
expression                                     result
--------------------------------------------   --------
(-0.0 + 0.0) == -0.0                           true
1 / (-0.0 + 0.0)                               Infinity
1 / -0.0                                       -Infinity
```

The two values compare **equal** and behave **differently**. IEEE-754 defines
`-0.0 == 0.0` as `true`, so every equality-based assertion in the suite is blind
to §36's divergence:

```text
Eval of "x + 0.0" at x = -0.0            Right(0.0)
Eval after the unsound rewrite           Right(-0.0)
the two agree under ==                   true       <-- the suite goes GREEN
1 / each of them                         Infinity vs -Infinity
```

Measured, all four lines. **A spec that asserts `assertEquals(before, after)`
passes for the wrong reason**, and passing for the wrong reason is worse than
failing, because it is evidence in the wrong direction. `S5Spec` therefore
asserts on `doubleToRawLongBits`.

`NaN` is the same trap with the sign reversed: `NaN == NaN` is `false`, so an
`assertEquals` on two correctly-computed `NaN`s *fails* while the code is right.
Both directions of the blindness, in one operator.

### And the obvious repair overshoots

If `==` sees too little, compare the bits. There are two ways to do that and
only one of them is right:

```scala
java.lang.Double.doubleToRawLongBits(d)   // every bit, exactly as stored
java.lang.Double.doubleToLongBits(d)      // NaN collapsed to one payload
```

Executed on this machine:

```text
value                  doubleToRawLongBits    doubleToLongBits
--------------------   --------------------   --------------------
Double.NaN             0x7ff8000000000000     0x7ff8000000000000
0.0 / 0.0              0xfff8000000000000     0x7ff8000000000000
Infinity * 0.0         0xfff8000000000000     0x7ff8000000000000
Infinity - Infinity    0xfff8000000000000     0x7ff8000000000000
math.sqrt(-1.0)        0xfff8000000000000     0x7ff8000000000000
-0.0                   0x8000000000000000     0x8000000000000000
+0.0                   0x0000000000000000     0x0000000000000000
```

**The literal `Double.NaN` and every `NaN` the hardware produces are different
bit patterns.** IEEE-754 leaves the payload of a generated `NaN` to the
implementation, and x86's invalid-operation result is the *real indefinite*
`0xfff8000000000000` — the same quiet `NaN`, with the sign bit set. The Java
literal has it clear.

So `doubleToRawLongBits` reports two correct `NaN`s as unequal, and it does so on
precisely the assertion that needed a bit comparison in the first place. This
guide's own spec failed that way:

```scala
assertSameBits(Double.PositiveInfinity * 0.0, Double.NaN)
// expected NaN, got NaN (bitwise)
```

`doubleToLongBits` canonicalises every `NaN` to one payload **and still separates
`-0.0` from `+0.0`**, which is exactly the discrimination this project needs and
no more. That is the instrument the harness uses.

The general shape, and it is worth carrying past this project: **an equality
that is too strict fails as silently as one that is too loose.** `==` let a
wrong rewrite through; the raw bits rejected a right one. Neither failure looks
like a bug in the instrument when you read the test output.

## 39. `-2 ^ 2`, And Where Unary Minus Binds

Covered in §15 and repeated here because it is the trap most likely to reach
production. The grammar of §5 gives `-4.0`. Excel gives `4`. Both are defensible
as conventions and they differ on an expression a user can type in four
characters.

The general form of the trap: **unary operators need a precedence and it is
almost never the one you would guess.** Give unary minus a precedence tighter
than `^` and `-2 ^ 2` becomes `4`; give it one looser than `*` and `-2 * 3`
becomes `-(2 * 3)`, which is the same number by luck and a different tree.

```text
placement of unary in the grammar    -2 ^ 2    -2 * 3    tree for -2 * 3
----------------------------------   ------    ------    ----------------
between factor and atom (this one)   -4        -6        Bin(Mul, Neg(2), 3)
tighter than ^                        4        -6        Bin(Mul, Neg(2), 3)
looser than *                        -4        -6        Neg(Bin(Mul, 2, 3))
```

Executed for the chosen placement. Note the third column: two of the three
placements agree on every *value* in the row and disagree on the *tree*, and a
suite that only checks values cannot distinguish them at all.

## 40. The Renderer That Loses A Tree

The renderer turns an `Expr` back into a string, and the obvious implementation
— parenthesise everything — is correct and unreadable. The good implementation
emits parentheses only where precedence requires them, which means the renderer
has to know the grammar too.

Executed, ten round trips `parse -> render -> parse`, all stable:

```text
source          rendered        stable?
-------------   -------------   -------
1 + 2 * 3       1 + 2 * 3       yes
(1 + 2) * 3     (1 + 2) * 3     yes
2 ^ 3 ^ 2       2 ^ 3 ^ 2       yes
(2 ^ 3) ^ 2     (2 ^ 3) ^ 2     yes
10 - 3 - 2      10 - 3 - 2      yes
10 - (3 - 2)    10 - (3 - 2)    yes
-2 ^ 2          -2 ^ 2          yes
-(2 ^ 2)        -2 ^ 2          yes
-x * y          -x * y          yes
-(x * y)        -(x * y)        yes
```

Row 8 is the one to look at: `-(2 ^ 2)` renders *without* its parentheses,
because they were redundant — §15 already said `-2 ^ 2` means `-(2 ^ 2)`. The
string changed and the tree did not, which is exactly the property the round trip
is testing. `render` is not required to be the identity on strings; it is
required to be the identity on **trees**.

The trap is the right-associative case. Render `2 ^ 3 ^ 2`'s children with the
same rule you use for `+` and you emit `2 ^ (3 ^ 2)` — still correct, but now the
renderer is inserting parentheses that mean nothing, and every nested `^`
compounds them. Right associativity has to be handled on the *other* side:

```text
operator        left child rendered at    right child rendered at
-------------   -----------------------   -----------------------
left assoc      prec                      prec + 1
right assoc     prec + 1                  prec
```

And one place where the round trip genuinely does not close:

```text
Lit(-0.0)  renders as   "-0.0"
"-0.0"     parses to    Neg(Lit(0.0))
equal as ASTs?          false
equal after simplify?   true
```

Measured. The parser cannot produce a negative literal — there is no such token,
only a `Neg` applied to `0.0` — so the round trip closes **modulo
simplification**, and the law `parse(render(e)) == e` has to be stated as
`simplify(parse(render(e))) == simplify(e)` or it is false on a value the
simplifier itself creates.

## 41. The Ceiling That Moved Instead Of Leaving

The last trap is structural, and it is the one that makes a system *look*
finished when it is not.

Make `eval` stack-safe and stop. The suite passes at a million nodes. Then a
user types a deeply nested expression and the system dies in `simplify`, or in
`size`, or in `render` — traversals nobody thought of as recursions because they
are four lines long and obviously correct.

```text
function     stack-safe?   deepest tree it survives
----------   -----------   ---------------------------------------
eval         yes           10,000,000 tested, no ceiling found
size         yes           1,000,000 tested
depth        yes           1,000,000 tested
simplify     yes           1,000,000 tested
render       NO            12,339
parse        NO             8,808  (chained unary minus, the worst)
```

Measured. Two of the six are not stack-safe, both **deliberately**, and the
difference between a deliberate limit and a forgotten one is whether the number
is written down. §E is where you write yours. The system's real ceiling is the
minimum of that column — `8,808`, not the parenthesis figure everyone quotes —
and a claim of "zero stack overflow risk" that does not mention it is false.

**And the number moves.** Every figure in this guide was taken with the suite
running alone. The same measurements taken during a full `sbt test`, where sbt
runs test classes in parallel inside one JVM, came out at `17,601` for
`evalNaive` and `1,540` for `render`, and once at `3,871` for an `evalNaive`
search that had measured `24,673` a minute earlier. Nothing about the code
changed. A ceiling measured while four other suites share the machine is a
measurement of the machine, and §E asks for the **conditions** beside the
numbers for exactly this reason.

---

# Part VIII — Where This Lives

## 42. `scalac` Does Exactly This

The pipeline you are building is the front end of every compiler, with the same
names:

```text
this project        scalac
----------------    -------------------------------------------
Lexer.tokenize      dotty.tools.dotc.parsing.Scanners
Parser.parse        dotty.tools.dotc.parsing.Parsers -> untyped Tree
Expr                dotty.tools.dotc.ast.Trees
Simplify            dotty.tools.dotc.transform.Constants (and ~40 more phases)
Eval                the JVM backend, emitting a stack machine (§26)
```

The correspondence at §26 is not an analogy. JVM bytecode *is* a stack machine
program, and the `work`/`vals` pair in the evaluator is the operand stack.
Compiled and disassembled with `javap -c`, `a + b * c` over three `Int`
parameters is:

```text
0: iload_1      push a
1: iload_2      push b
2: iload_3      push c
3: imul         pop two, push b * c
4: iadd         pop two, push a + (b * c)
5: ireturn
```

Five instructions, in exactly the order of steps 1 to 7 of §26's table, with
`iload` where the machine has `Push` and `imul`/`iadd` where it has `Combine`.
The evaluator you are about to write is a JVM interpreter for a five-operator
language.

The same method with literals instead of parameters, `2 + 3 * 4`, disassembles
to two instructions:

```text
0: bipush 14
2: ireturn
```

The compiler already ran §30 on it. Constant folding is not an idea this
project invented in order to have something to simplify; it is a pass that had
already happened before the class file existed.

## 43. SQL Planners And The Rewrite Rule

Every query planner is §33 with a bigger rule set. `WHERE 1 = 1 AND x > 5`
becomes `WHERE x > 5` by constant folding; `SELECT * FROM t WHERE false` becomes
an empty scan; predicate pushdown moves a filter below a join because the two
trees produce the same rows. Same bottom-up traversal, same fixed-point
argument, same requirement that each rewrite be *provably* value-preserving —
and the same failure mode, since a planner rule that is wrong on `NULL` is §37
with a different bottom value.

Spark's Catalyst optimiser names them exactly as this project does:
`ConstantFolding`, `NullPropagation`, `SimplifyBinaryComparison`.

## 44. `-ffast-math` Is Literally §35

The sound/fast split is not a pedagogical invention. `gcc -ffast-math` enables,
among others, the assumption that floating-point addition is associative — which
§4's measurement shows is false:

```text
(0.1 + 0.2) + 0.3   =   0.6000000000000001
0.1 + (0.2 + 0.3)   =   0.6
```

Executed. Compilers vectorise reductions by re-associating them, so a summation
loop compiled with `-ffast-math` gives a different total than the same loop
compiled without it, and neither total is "wrong" — they are sums in different
orders. The flag exists because the speed is usually worth it and the
documentation exists because sometimes it is not.

Java refuses the trade at the language level: floating-point evaluation order in
Java is specified and the JIT may not re-associate it. That is why §26's
evaluation order matters and why `strictfp` became redundant in Java 17.

## 45. Explicit Stacks In Production Interpreters

CPython evaluates with a stack machine over a bytecode array for the same reason
§26 does: Python recursion limits are about 1,000 frames and expressions nest
deeper than that in generated code. V8, the CLR and the BEAM all do the same.

Closer to home, `scala.collection.immutable.List#foldRight` is implemented as a
`reverse` followed by a `foldLeft` — Module 3 §14's trick — for precisely this
reason, and `scala.concurrent.Future`'s callback chain is the instruction-stack
technique of §23 applied to asynchronous work. Block 3 rebuilds that from
scratch and calls it a trampoline.

---

# Part IX — Scala 3 For This Project

## 46. `enum` And Exhaustivity

Three of the project's types are closed sums, and closing them is what makes the
compiler a participant:

```scala
enum Expr:
  case Lit(value: Double)
  case Var(name: String)
  case Neg(arg: Expr)
  case Bin(op: Op, left: Expr, right: Expr)
```

Add a fifth case later — `Call(fn, args)`, say — and **every** `match` over
`Expr` that does not handle it becomes a warning, and under this repository's
`-Wall -Werror` (see `build.sbt`), a compile error. With `Compile / scalacOptions`
set as it is, forgetting to teach the simplifier about a new node is impossible.

That guarantee is why `case _ => ...` is a defect in this codebase rather than a
style preference: a catch-all silences the exhaustivity checker on the one edit
where it was about to earn its keep. §32's rule function ends with
`case other => other` — which *is* a catch-all, deliberately, because there the
fall-through is the semantics — and that single exception is worth being able to
justify.

## 47. `Either` As The Error Channel

Three failure types, three passes, one composition:

```scala
def evaluate(input: String, env: Env): Either[EngineError, Double] =
  Lexer.tokenize(input)
    .left.map(EngineError.Lexical.apply)
    .flatMap(ts => Parser.parse(ts).left.map(EngineError.Syntactic.apply))
    .flatMap(ast => Eval.eval(Simplify.simplify(ast), env).left.map(EngineError.Semantic.apply))
```

`flatMap` gives short-circuiting for free: the first `Left` is returned and no
later pass runs. `left.map` is what keeps the three error types from leaking —
each pass has its own vocabulary and the boundary translates rather than
unions.

Whether `EngineError` should wrap the three or flatten them into one enum with
seven cases is a genuine design question, and it is the first of §G's.

## 48. `@tailrec` On A Machine Loop

Every loop in this project carries `@scala.annotation.tailrec`, and the reason is
Module 3 §7: the annotation does not *make* anything tail recursive, it makes the
compiler **reject** a function that is not. A machine loop that accidentally
grows a non-tail call — an easy mistake when adding a case — fails to compile
instead of failing at a million nodes in production.

For that rejection to be available, the method must have a target nobody can
override: a local `def`, a `private` helper, or a member of an `object`. All
three appear in this project; Module 3's `TailShapes` documents the trade between
them.

## 49. Pattern Guards, And Why The Zero Rules Need Them

Scala pattern matching on a literal uses `==`, and §38 established that `==`
cannot see the difference between `0.0` and `-0.0`. So this is broken:

```scala
case Bin(Op.Add, x, Lit(0.0)) => x        // ALSO matches Lit(-0.0)
```

The pattern `Lit(0.0)` matches a node holding `-0.0`, because `-0.0 == 0.0`. The
rule intended for the sound case fires on the unsound one, and the guide's whole
§36 argument evaporates in one line of pattern syntax.

The repair is a guard that reads the bits:

```scala
private def isNegZero(e: Expr): Boolean = e match
  case Lit(v) => java.lang.Double.doubleToRawLongBits(v) == java.lang.Double.doubleToRawLongBits(-0.0)
  case _      => false

case Bin(Op.Add, x, z) if isNegZero(z) => x
```

This is the single most likely silent defect in S5, and it is silent in both
directions: the sound rule fires when it should not, and the `+ 0.0` the
simplifier was supposed to leave alone disappears.

---

## Where To Go Next

| Topic | Source | What to read |
| :--- | :--- | :--- |
| Recursive descent, done properly | Crafting Interpreters, Nystrom | Ch. 6 "Parsing Expressions" — the same grammar, in full |
| Precedence climbing | Pratt, *Top Down Operator Precedence* (1973) | The original paper; nine pages |
| Compiler front ends | the Dragon Book, Aho et al. | Ch. 2 and 4 — lexing and the grammar formalism |
| IEEE-754, properly | Goldberg, *What Every Computer Scientist Should Know About Floating-Point Arithmetic* | §1–2; the signed-zero discussion is the source of §36 |
| Floating-point identities | Kahan, *How Java's Floating-Point Hurts Everyone Everywhere* | Why `-ffast-math`-style rewrites are contested |
| Stack machines | the JVM Specification, §2.6 and §3.2 | The operand stack; §26 is a re-derivation of it |
| Rewrite systems | Baader & Nipkow, *Term Rewriting and All That* | Ch. 1–2: normal forms, confluence, termination — §33 formalised |
| This project's own precursors | `module2_structures.md` §9 and §15, `module3_stack.md` §1, §13, §14 | Structural sharing, the ceiling, and the explicit reversal |
