package cs.se.block1.module2

/** Exercises 2, 3 and 4 — the list, from two constructors up.
  *
  * The type is given because the specs must compile against it; every behaviour
  * below is yours.
  *
  * Three things are load-bearing in this declaration, and the guide's Part VII
  * explains each:
  *
  *   - it is **closed**, so a `match` over both cases is provably exhaustive and
  *     a third case would break every incomplete match in the codebase;
  *   - it is **recursive**, which is what makes it arbitrarily long from two
  *     lines;
  *   - `Nil` takes no parameters, so it compiles to a **singleton value**. Every
  *     empty list in the program is the same object.
  *
  * The `+A` is covariance, and it is what lets that one singleton serve as an
  * empty `MyList[Int]`, `MyList[String]` and `MyList[A]` at once — `Nil` has
  * type `MyList[Nothing]`, and `Nothing` is a subtype of everything.
  *
  * '''Run the experiment before you implement anything.''' Delete the `+`,
  * compile, and record which line the compiler rejects first and what it says.
  * That error is the whole content of Part VII.26, and it is asked again in §G.
  */
enum MyList[+A]:
  case Nil
  case Cons(head: A, tail: MyList[A])

object MyList:

  /** Build a list from varargs, for tests and for your own sanity.
    *
    * `MyList(1, 2, 3)` must produce `Cons(1, Cons(2, Cons(3, Nil)))` — that is,
    * the first argument ends up at the front. Note that the obvious fold builds
    * it backwards; fixing that is the first appearance of Part V.17 in your own
    * code, and the fix must not be quadratic.
    */
  def apply[A](items: A*): MyList[A] = ???

  extension [A](xs: MyList[A])

    /** True for `Nil` only.
      *
      * Constraint: match on the shape. Do not write `length == 0` — that walks
      * the whole list to answer a question the first cell already settles, and
      * it is the defect Part V.19 is about.
      */
    def isEmpty: Boolean = ???

    /** Number of cells.
      *
      * `O(n)` in time, and it must be `O(1)` in stack: annotate the recursion
      * `@tailrec` or the suite will find the limit for you at a million
      * elements.
      */
    def length: Int = ???

    /** The first element, if there is one.
      *
      * `Nil.headOption` is `None`. This is the total sibling of the partial
      * `head` below, and providing both is deliberate — the exercise is to say
      * when each is the right tool, not to pick one and pretend the other has no
      * use.
      */
    def headOption: Option[A] = ???

    /** The first element.
      *
      * `Nil.head` has no answer, and what you do about that is a decision you
      * must make and document here rather than inherit. Three defensible
      * choices, with what each costs:
      *
      *   - throw: loud and immediate, but it leaves the purity gate, and §D
      *     forbids `throw` in this module;
      *   - return `Option[A]`: total, but then this method is `headOption` and
      *     every caller handles a case that is sometimes impossible;
      *   - narrow the domain: document that the caller must have established
      *     non-emptiness, exactly as `Escape.sumNorms` documents equal lengths
      *     in Module 1.
      *
      * Pick one, write the reason into this Scaladoc, and defend it in §G.
      * Guide, Part V.21.
      */
    def head: A = ???

    /** Everything after the first cell. `Nil.tail` faces the same question as
      * `head`, and must be answered the same way.
      */
    def tail: MyList[A] = ???

    /** A new list with `f` applied to every element.
      *
      * Allocates `n` cells and shares every element `f` returns unchanged —
      * Exercise 1 predicted the number, Exercise 8 measures it.
      */
    def map[B](f: A => B): MyList[B] = ???

    /** The elements satisfying `p`, in their original order. */
    def filter(p: A => Boolean): MyList[A] = ???

    /** The same elements, in the opposite order.
      *
      * Allocates `n` cells and shares every element. It must be `@tailrec`, and
      * it is the operation that makes `byPrepend` in Exercise 5 linear rather
      * than quadratic.
      */
    def reverse: MyList[A] = ???

    /** Left fold: `f(f(f(z, a1), a2), a3)`.
      *
      * Must be `@tailrec`. The accumulator is complete before the recursive
      * call, so the call is the last thing that happens — which is precisely
      * what `foldRight` cannot arrange. Guide, Part V.18.
      */
    def foldLeft[B](z: B)(f: (B, A) => B): B = ???

    /** Right fold: `f(a1, f(a2, f(a3, z)))`.
      *
      * This one is **not** tail-recursive and cannot be made so directly: it
      * must reach the end of the list before it can combine anything, so every
      * element's frame waits on the stack.
      *
      * Implement it in the natural recursive way and leave it that way. The
      * spec asserts that it overflows on a large enough input, because a
      * `foldRight` that survives a million elements is a `foldRight` that
      * secretly reversed the list, and hiding the cost is worse than paying it.
      * Module 3 is about removing this limit properly.
      */
    def foldRight[B](z: B)(f: (A, B) => B): B = ???

    /** `xs` followed by `ys`.
      *
      * Allocates one cell per element of `xs` and shares the whole of `ys` — so
      * the cost is `xs.length`, not `xs.length + ys.length`. Convince yourself
      * of that from Part III.11 before implementing, because it is the single
      * most useful instance of the path rule.
      */
    def concat(ys: MyList[A]): MyList[A] = ???

    /** `xs` with `x` appended at the back.
      *
      * `O(n)`, unavoidably, and the exercise is to know that rather than to
      * avoid it. This is the operation Part V.17 warns about inside a fold.
      */
    def appended(x: A): MyList[A] = ???

    /** Conversion to the standard library, for test assertions only.
      *
      * This is the one permitted crossing in §D: `scala.List` may appear here
      * and nowhere else in this module's implementation.
      */
    def toScalaList: List[A] = ???

  end extension

end MyList
