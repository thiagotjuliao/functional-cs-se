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
  def apply[A](items: A*): MyList[A] =
    items.reverse.foldLeft(Nil):
      case (acc, a) => Cons(a, acc)

  extension [A](xs: MyList[A])

    /** True for `Nil` only.
      *
      * Constraint: match on the shape. Do not write `length == 0` — that walks
      * the whole list to answer a question the first cell already settles, and
      * it is the defect Part V.19 is about.
      */
    def isEmpty: Boolean = xs match
      case Nil => true
      case _ => false

    /** Number of cells.
      *
      * `O(n)` in time, and it must be `O(1)` in stack: annotate the recursion
      * `@tailrec` or the suite will find the limit for you at a million
      * elements.
      */
    def length: Int =
      @scala.annotation.tailrec
      def loop(as: MyList[A], acc: Int = 0): Int =
        as match
          case Nil => acc
          case Cons(_, t) => loop(t, acc + 1)
      loop(xs)

    /** The first element, if there is one.
      *
      * `Nil.headOption` is `None`. This is the total sibling of the partial
      * `head` below, and providing both is deliberate — the exercise is to say
      * when each is the right tool, not to pick one and pretend the other has no
      * use.
      */
    def headOption: Option[A] = xs match
      case Nil => None
      case Cons(h, _) => Some(h)

    /** The first element. **Defined only on a non-empty list.**
      *
      * `Nil.head` has no answer, and three choices were open. Returning
      * `Option[A]` is total, but that method already exists above as
      * `headOption`, and making this one a second copy of it forces every
      * caller to handle a case that is sometimes impossible. Throwing is loud
      * and immediate. The domain was narrowed instead: the contract is that
      * the caller has already established non-emptiness, exactly as
      * `Escape.sumNorms` documents equal lengths in Module 1.
      *
      * `require` states that narrowed domain at the boundary, and the match is
      * marked `@unchecked` because the `Nil` branch it would otherwise demand
      * is already excluded by the line above it — an exhaustivity warning here
      * would be the compiler asking about a case the precondition has ruled
      * out.
      *
      * Note what that costs, since §D of the checklist forbids `throw` in this
      * module: `require` raises `IllegalArgumentException`, so the narrowing is
      * enforced by a throw wearing a different name. Whether that is a
      * violation of the gate or the one mechanism the gate must admit is the
      * question §G asks.
      *
      * Guide, Part V.21.
      */
    def head: A =
      require(
        requirement = !xs.isEmpty,
        message = "`head` can only be called on non empty lists."
      )
      (xs: @unchecked) match
        case Cons(h, _) => h

    /** Everything after the first cell. **Defined only on a non-empty list.**
      *
      * `Nil.tail` faces the same question as `head` and is answered the same
      * way: the domain is narrowed, `require` states it, and the match is
      * `@unchecked` because the precondition has already excluded `Nil`. See
      * `head` for the reasoning and for what the choice costs against §D.
      */
    def tail: MyList[A] =
      require(
        !xs.isEmpty,
        "`tail` can only be called on non empty lists."
      )
      (xs: @unchecked) match
        case Cons(_, t) => t

    /** `xs` with `x` prepended.
      * This operation takes constant time `O(1)`.
      */
    def prepended(x: A): MyList[A] =
      Cons(x, xs)

    /** A new list with `f` applied to every element.
      *
      * `Sharing.mapCells(n)` predicts `n`, and `n` is the number of cells in
      * the *result* — the floor any implementation must pay. A `@tailrec`
      * implementation pays it twice. The accumulator builds the list backwards,
      * so a second pass is needed to restore the order, and one of the two
      * spines is garbage before the method returns. Measured at n = 100,000,
      * with `AllocationProbe` on the forked JVM:
      *
      * {{{
      * reverse   2,400,000 bytes   1.00 n cells   the model exactly
      * map       6,397,952 bytes   2.67 n cells   two spines, plus one box
      * filter            2.00 n cells             two spines, and no box
      * }}}
      *
      * Both figures are asserted by `Exercise8SharingProofSpec`, against
      * `Sharing.reverseCells` and `Sharing.mapCells`. `filter` carries no
      * absolute because nothing measures it: the cell count is derived, and an
      * unanchored byte count is pattern 12 of `error-patterns.md`.
      *
      * The 0.67 above `filter` is not spine, and the comparison between the two
      * is the whole lesson. Both return the same elements in the same order,
      * and only `map` pays:
      *
      * {{{
      * two spines        2 x 100,000 x 24   4,800,000
      * one Integer each  (100,000 - 128) x 16   1,597,952
      *                                      ---------
      *                                        6,397,952   measured, to the byte
      * }}}
      *
      * `filter`'s `p(h)` returns a primitive and the element is re-prepended as
      * the *same reference*. `map`'s `f(h)` returns a `B`: `Function1` is
      * specialised for `Int`, so `identity` unboxes its argument and the `int`
      * that comes back must be boxed again to enter the cell. A fresh
      * `java.lang.Integer` per element, minus the 128 the `Integer` cache
      * shares — Module 1, §19 — and `identity` allocated none of them.
      *
      * That is why the model counts cells and not bytes.
      *
      * Paying `2n` is the right trade here rather than a defect. §D of the
      * checklist requires `MyList` recursion to be `@tailrec`, and an
      * implementation allocating exactly `n` must reach the end of the list
      * before it can build its first cell: the shape of `foldRight`, with the
      * same stack. Scala's own `List.map` escapes the dilemma by mutating the
      * tail pointer of the cell it just built, inside a `while` loop — which
      * §D forbids in this module and CLAUDE.md permits only inside a
      * micro-library's engine. The discarded spine is cheap to collect: it
      * dies in the nursery, and a copying collector charges only for survivors.
      */
    def map[B](f: A => B): MyList[B] =
      @scala.annotation.tailrec
      def loop(as: MyList[A], acc: MyList[B] = Nil): MyList[B] =
        as match
          case Nil => acc
          case Cons(h, t) => loop(t, acc.prepended(f(h)))
      loop(xs.reverse)

    /** The elements satisfying `p`, in their original order.
      *
      * Two spines for the same reason as `map`, measured at exactly `2.00 n`
      * cells when every element is kept. See `map` for why that is the correct
      * trade under §D rather than a defect.
      */
    def filter(p: A => Boolean): MyList[A] =
      @scala.annotation.tailrec
      def loop(as: MyList[A], acc: MyList[A] = Nil): MyList[A] =
        as match
          case Nil => acc
          case Cons(h, t) if p(h) => loop(t, acc.prepended(h))
          case Cons(_, t) => loop(t, acc)
      loop(xs).reverse

    /** The same elements, in the opposite order.
      *
      * Allocates `n` cells and shares every element. It must be `@tailrec`, and
      * it is the operation that makes `byPrepend` in Exercise 5 linear rather
      * than quadratic.
      */
    def reverse: MyList[A] =
      @scala.annotation.tailrec
      def loop(as: MyList[A], acc: MyList[A] = Nil): MyList[A] =
        as match
          case Nil => acc
          case Cons(h, t) => loop(t, acc.prepended(h))
      loop(xs)

    /** Left fold: `f(f(f(z, a1), a2), a3)`.
      *
      * Must be `@tailrec`. The accumulator is complete before the recursive
      * call, so the call is the last thing that happens — which is precisely
      * what `foldRight` cannot arrange. Guide, Part V.18.
      */
    def foldLeft[B](z: B)(f: (B, A) => B): B =
      @scala.annotation.tailrec
      def loop(as: MyList[A], acc: B = z): B =
        as match
          case Nil => acc
          case Cons(h, t) => loop(t, f(acc, h))
      loop(xs)

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
    def foldRight[B](z: B)(f: (A, B) => B): B =
      xs match
        case Nil => z
        case Cons(h, t) => f(h, t.foldRight(z)(f))

    /** `xs` followed by `ys`.
      *
      * **Retains** one cell per element of `xs` and shares the whole of `ys`, so
      * what the result keeps is `xs.length` cells and not
      * `xs.length + ys.length`. That is Part III.11's path rule, and it is the
      * single most useful instance of it.
      *
      * **Allocates** twice that: `2 * xs.length`. Staying `@tailrec` forces a
      * walk forwards and an emission backwards, so `loop(xs.reverse)`
      * materialises a whole spine and drops it before returning. The verb
      * matters here more than the number — an earlier draft of this sentence
      * said *allocates* where it meant *retains*, and no test could contradict
      * it. `Sharing` keeps the two apart on purpose, in `appendCells` against
      * `appendAllocatedCells`; this is the function where the difference is
      * actually created.
      *
      * `appended` adds one more cell on top, for `x` itself: `2n + 1`, which is
      * the 200,001 cells Exercise 8 measures at `n = 100,000`.
      */
    def concat(ys: MyList[A]): MyList[A] =
      @scala.annotation.tailrec
      def loop(as: MyList[A], acc: MyList[A] = ys): MyList[A] =
        as match
          case Nil => acc
          case Cons(h, t) => loop(t, acc.prepended(h))
      loop(xs.reverse)

    /** `xs` with `x` appended at the back.
      *
      * `O(n)`, unavoidably, and the exercise is to know that rather than to
      * avoid it. This is the operation Part V.17 warns about inside a fold.
      */
    def appended(x: A): MyList[A] =
      xs.concat(Cons(x, Nil))

    /** Conversion to the standard library, for test assertions only.
      *
      * This is the one permitted crossing in §D: `scala.List` may appear here
      * and nowhere else in this module's implementation.
      */
    def toScalaList: List[A] =
      xs.foldLeft(scala.List.empty[A]):
        case (ls, a) => a :: ls
      .reverse

  end extension

end MyList
