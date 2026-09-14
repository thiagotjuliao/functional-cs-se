package cs.se.block1.module3

import cs.se.block1.module2.MyList

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
    * Costs one extra spine, `n` cells. §E asks for the measured number and for
    * one line naming the exchange rate you just paid.
    */
  def foldRightSafe[A, B](xs: MyList[A], z: B)(f: (A, B) => B): B = ???

  /** `foldRightSafe` expressed the other way round, for comparison.
    *
    * Implement it as a `foldLeft` that builds a '''function''' rather than a
    * value: each step composes a new closure, and the whole composition is
    * applied to `z` at the end.
    *
    * It works, and it is instructive because the cost lands somewhere else. The
    * spine is gone and a chain of closures has taken its place — and when that
    * chain is finally applied, it calls itself down the whole length. Measure
    * both its allocation and its ceiling before writing the Scaladoc, and say
    * plainly whether this version fixes the problem or relocates it.
    *
    * This is the first sighting of the structure Block 3 builds properly.
    * Guide §16.
    */
  def foldRightComposed[A, B](xs: MyList[A], z: B)(f: (A, B) => B): B = ???

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
    * For an `f` that always forces its second argument, this has exactly the
    * same ceiling as `MyList.foldRight`. Verify that rather than assuming it —
    * the spec asks for the number, and a fix that silently fixed nothing would
    * look identical from the outside.
    */
  def foldRightLazy[A, B](xs: MyList[A], z: => B)(f: (A, => B) => B): B = ???

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
    * State in the Scaladoc which of the two ceilings this removes and which it
    * leaves standing. §E asks the same question and will not accept "it is
    * faster".
    */
  def existsLazy[A](xs: MyList[A], p: A => Boolean): Boolean = ???

end LazyFold
