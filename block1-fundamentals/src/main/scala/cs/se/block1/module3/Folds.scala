package cs.se.block1.module3

import cs.se.block1.module2.MyList
import MyList.*

/** Exercise 6 — paying heap to buy stack.
  *
  * `MyList.foldRight` is the debt Module 2 left open. Its Scaladoc says so:
  * *"Module 3 is about removing this limit properly."* It overflows just above
  * fifteen thousand elements, and it cannot be made tail recursive directly,
  * because the recursive call is an argument to `f` and `f` must consume it.
  * Guide, Part IV.13.
  *
  * This exercise buys the fix and measures the bill. Nothing here is free, and
  * the point of the exercise is to know the exact price rather than to feel
  * good about the technique.
  */
object SafeFold:

  /** A `foldRight` with no stack ceiling.
    *
    * Same contract as `MyList.foldRight`: `f(a1, f(a2, f(a3, z)))`. It must
    * agree with `MyList.foldRight` on every input that `foldRight` survives,
    * and survive inputs that it does not.
    *
    * '''The argument order is the trap and it is not hypothetical.''' The fix
    * runs `foldLeft` over the reversed list, `foldLeft` hands you
    * `(accumulator, element)`, and `f` wants `(element, accumulator)`. Swap
    * them wrongly and the code compiles whenever `A` and `B` are the same type
    * — which is most test fixtures — and computes something else. Guide §21,
    * and the spec folds with subtraction for precisely this reason.
    *
    * Costs one extra spine, `n` cells. Measured over `n = 100,000`: `foldLeft`
    * allocates 2,399,640 bytes and this allocates 4,800,040, a delta of
    * 2,400,400 = 100,016 cells of 24 bytes.
    *
    * The exchange rate: '''one cell of heap, permanently, for every frame of
    * stack avoided.''' The stack was free and bounded; the heap is charged and
    * unbounded, which is exactly why the trade is worth making.
    *
    * It is also the version that actually removes the ceiling. Searched to a
    * limit of 2,097,152 it never overflows, while `foldRightComposed` — which
    * costs twice as much per element — overflows at 30,816. Challenge 42.
    */
  def foldRightSafe[A, B](xs: MyList[A], z: B)(f: (A, B) => B): B =
    xs.reverse.foldLeft(z)((acc, a) => f(a, acc))

  /** `foldRightSafe` expressed the other way round, for comparison.
    *
    * Implement it as a `foldLeft` that builds a '''function''' rather than a
    * value: each step composes a new closure, and the whole composition is
    * applied to `z` at the end.
    *
    * '''It relocates the problem.''' It does not fix it.
    *
    * The chain costs 48.02 bytes per element, two objects rather than one:
    *
    * {{{
    * closure   b => g(f(a, b))     24 bytes    allocated while building
    * box       of f's result       24 bytes    allocated while applying
    *                               --------
    *                               48 bytes per element
    * }}}
    *
    * The second is the accumulator being boxed — `f: (A, B) => B` is generic,
    * `B` erases to `Object`. Switching `B` from `Long` to `Int` moves the figure
    * to 40.02, which is the 8-byte difference between the two boxes and could
    * not happen if the object were a list cell.
    *
    * And the ceiling, searched to a limit of 2,097,152:
    *
    * {{{
    * build the chain, never apply it    2,097,151   never overflows
    * build and apply                       30,816
    * foldRightSafe                      2,097,151   never overflows
    * }}}
    *
    * Building is a `foldLeft` and has no ceiling. The whole ceiling is in the
    * application: each closure is `b => g(f(a, b))`, so applying the last calls
    * `g`, which calls its own `g`, down the entire chain — the original
    * recursion, deferred from build time to apply time. The cost moves twice,
    * stack to heap and heap back to stack. Challenge 42.
    *
    * This is the first sighting of the structure Block 3 builds properly.
    * Guide §16.
    */
  def foldRightComposed[A, B](xs: MyList[A], z: B)(f: (A, B) => B): B =
    xs.foldLeft(identity[B])((g, a) => b => g(f(a, b)))(z)

end SafeFold

/** Exercise 7 — the ceiling that laziness removes, and the one it does not.
  *
  * `foldRightSafe` pays `O(n)` heap to make every `foldRight` safe. There is a
  * cheaper fix that works for a smaller set of operations and costs nothing at
  * all: if `f` never looks at its second argument, the recursion underneath it
  * never happens. Guide, Part IV.15.
  *
  * The mechanism is the by-name parameter, `=> B`. Scala evaluates it at the
  * point of use rather than the point of call, so an `f` that ignores it has
  * stopped the recursion without knowing that it did.
  */
object LazyFold:

  /** `foldRight` whose accumulator arrives by name.
    *
    * Not `@tailrec`, and it cannot be: this is the same shape as
    * `MyList.foldRight`. What changes is that the recursive call is now
    * '''suspended''' inside the by-name argument rather than evaluated before
    * `f` is entered.
    *
    * For an `f` that always forces its second argument, this does '''not''' have
    * the same ceiling as `MyList.foldRight`. It has roughly a '''quarter''' of
    * it — 7,751 against 30,862 in one suite run, 3.98 frames per element.
    *
    * The stack captured at the deepest point shows why: four frames per level
    * where the strict fold uses one.
    *
    * {{{
    * LazyFold$.foldRightLazy$$anonfun$1    the thunk, a Function0
    * LazyFold$.foldRightLazy               the recursion, inside the thunk
    * $anonfun$adapted$1                    the boxing adapter, B erased
    * <the caller's f>                      f itself
    * }}}
    *
    * The cause is an inversion of order. Strict `foldRight` evaluates the
    * recursion '''before''' entering `f`, so `f`'s frames are born on the way
    * back up, one at a time. Here the recursion happens '''inside''' `f`, when
    * the argument is forced, so `f`, the adapter and the thunk stay live at
    * every level below. Challenge 43.
    *
    * What the by-name parameter buys is not stack. It is the option not to
    * descend at all, which is `existsLazy` below.
    */
  def foldRightLazy[A, B](xs: MyList[A], z: => B)(f: (A, => B) => B): B =
    xs match
      case Nil => z
      case Cons(h, t) => f(h, foldRightLazy(t, z)(f))

  /** `exists`, expressed through `foldRightLazy` and nothing else.
    *
    * Write it as a single call: the `f` you pass is what decides whether the
    * rest of the list is ever visited.
    *
    * The spec counts how many elements `p` is applied to. On a list of a
    * million whose match is at index 3, the answer must be 4 and not 1,000,000
    * — and `existsLazy` must not overflow, while a strict `foldRight` over the
    * same list does.
    *
    * '''It removes the ceiling on work and not the one on stack.'''
    *
    * {{{
    *                              match at index 3      no match
    * existsLazy    over 1e6       survives              StackOverflowError
    * existsStrict  over 1e6       StackOverflowError    StackOverflowError
    *
    * largest n with no match, existsLazy      3,199
    * largest n with no match, existsStrict   18,431
    * }}}
    *
    * Where the predicate decides early the cost is the prefix up to the match —
    * 4 elements and 112 bytes out of a million — and neither ceiling is reached.
    * The top-left cell is not stack relief: it survived because `p(a) || acc` is
    * `true` at the fourth element, `||` short-circuits, `acc` is never forced
    * and the remaining 999,996 levels never exist.
    *
    * Where the predicate does not decide — any list with no match, or whose
    * match lies past index ~3,199 — the recursion descends in full at the four
    * frames per level documented above, and overflows '''earlier''' than the
    * strict version, by a factor of 5.76.
    *
    * Laziness makes the descent avoidable, never cheaper. Challenge 44.
    */
  def existsLazy[A](xs: MyList[A], p: A => Boolean): Boolean =
    foldRightLazy(xs, false)((a, acc) => p(a) || acc)

end LazyFold
