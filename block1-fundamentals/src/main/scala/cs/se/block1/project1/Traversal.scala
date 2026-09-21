package cs.se.block1.project1

/** Stage 3 — three traversals of a tree, two of which must not use the stack.
  *
  * '''This stage exists so that the technique is learned where it is cheap.'''
  * `size` counts nodes: it is four lines, it cannot be wrong about arithmetic,
  * and it has exactly the fatal property the evaluator has. Learn the repair
  * here, where the only thing that can break is a count, and Stage 4 becomes a
  * transcription.
  *
  * Guide, Part IV (§19 to §24).
  */
object Shape:

  /** The node count, by direct recursion. **The experimental control.**
    *
    * Four lines, obviously correct, and measured on the reference
    * implementation at `30,843 / 30,845 / 30,845` nodes of depth before
    * `StackOverflowError`. Three searches, because one search taken while the
    * JIT is still promoting the method returns a number that was true for part
    * of it — see `error-patterns.md`, Pattern 13.
    *
    * **Keep it.** `size` is only demonstrably correct because there is
    * something to compare it against, and the spec asserts the two agree on
    * every tree shallow enough for this one.
    *
    * `@tailrec` does not apply here and the compiler will tell you so: there
    * are two recursive calls and an addition pending after both. Guide §20.
    */
  def sizeNaive(e: Expr): Int = ???

  /** The node count, in `O(1)` stack.
    *
    * Contract:
    *   - agrees with `sizeNaive` on every tree `sizeNaive` survives;
    *   - survives a tree 1,000,000 deep — the spec asserts it, and the answer
    *     for the left chain of that depth is `2,000,001`;
    *   - `@tailrec`, and the annotation is not decoration: it is what makes the
    *     compiler reject a future edit that reintroduces a non-tail call.
    *
    * '''`size` is the easy case and the reason is worth naming.'''
    * Results combine with `+`, which is associative and commutative, so the
    * order nodes are visited in does not matter and the pending work can be a
    * plain `MyList[Expr]` of subtrees. Guide §21 has the trace.
    */
  def size(e: Expr): Int = ???

  /** The length of the longest root-to-leaf path, in `O(1)` stack.
    *
    * A `Lit` or a `Var` alone has depth `1`.
    *
    * '''Deliberately harder than `size`, and deliberately not hard enough to
    * need instructions.''' `1 + max(depth(l), depth(r))` needs both children's
    * answers at once, so the flat stack of §21 loses the information about
    * which node they belong to. Guide §22 gives two repairs and says which one
    * this needs: depth is a property of *where a node sits*, not of what its
    * children return, so carrying the extra information alongside the node is
    * enough and no combining step is required.
    *
    * Finding that out is the exercise. Reaching for §23's instruction stack
    * here works and is more machinery than the problem has.
    *
    * Measured: the 1,000,000-deep left chain has depth `1,000,001`.
    */
  def depth(e: Expr): Int = ???

  /** The tree as a string, with parentheses only where precedence requires
    * them.
    *
    * Contract — the law, and it is about trees rather than strings:
    * {{{
    * parse(tokenize(render(e))) == e
    * }}}
    * `render` is **not** required to be the identity on strings. `-(2 ^ 2)`
    * renders as `-2 ^ 2`, because §15 already makes those the same tree, and
    * the round trip still closes. Guide §40 has the ten cases the spec asserts.
    *
    * Two things decide whether it is correct:
    *   - **Associativity is handled on opposite sides.** A left-associative
    *     operator renders its left child at its own precedence and its right
    *     child one higher; a right-associative one is the mirror. Get it wrong
    *     and `2 ^ 3 ^ 2` comes out as `2 ^ (3 ^ 2)` — still correct, and now
    *     compounding a redundant bracket per nesting level.
    *   - **`Lit(-0.0)` must not print as `0`.** `v.toLong` erases the sign, and
    *     the round trip then loses a value the simplifier itself creates. Guide
    *     §36 and §40.
    *
    * '''This one is allowed to keep its stack ceiling, and that is a decision
    * rather than an omission.''' It is a direct recursion, measured at `12,339`
    * on the reference implementation, and a 12,000-deep expression has no
    * readable string form anyway. Record the number in §E; §G asks you to
    * justify which ceilings you removed and which you documented.
    *
    * '''Distrust your first measurement of this one.''' Three searches in a row
    * gave `2,047`, `12,339`, `12,339` — reproducibly, in that order. The
    * harness's warm-up runs at depth 128 and does not reach the string building
    * that a search half a million levels deep exercises. Guide §24.
    */
  def render(e: Expr): String = ???

end Shape
