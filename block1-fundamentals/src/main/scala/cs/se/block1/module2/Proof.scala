package cs.se.block1.module2

import cs.se.block1.module1.AllocationProbe

/** Exercises 8 and 9 — confronting the model with the machine.
  *
  * Exercise 1 made predictions from arithmetic alone. This is where they are
  * checked against `AllocationProbe`, the instrument you built in Module 1,
  * Exercise 3.
  *
  * Three of that instrument's limits apply here and are documented on
  * `cs.se.block1.module1.AllocationProbe.bean`: it requires a forked JVM, it
  * counts only the calling thread, and its floor depends on the return type of
  * what is measured. The last one matters most here — a body returning a
  * `MyList` returns an object that already exists, so the floor is zero, but a
  * body returning an `Int` or a `Long` is boxed on the way out and costs 16 or
  * 24 bytes. Subtract deliberately, and say which you subtracted.
  *
  * '''A measurement that agrees with the prediction proves the model. A
  * measurement that disagrees is not a failure — it is the interesting case, and
  * it belongs in `challenge-log.md` with the account of where the gap came
  * from.'''
  */
object SharingProof:

  /** Bytes allocated while evaluating `body`, with the instrument's own floor
    * already removed.
    *
    * Implement by delegating to `cs.se.block1.module1.AllocationProbe.measure`,
    * then subtracting the floor for a reference-valued body. State in the
    * Scaladoc which floor you subtracted and how you established it — the
    * evidence is three measurements away, and Module 1's challenge log entry 4
    * has them.
    *
    * While the body's return type is a reference not a boxed primitive
    * we'll consider 0 as the floor for our reference-valued body.
    *
    * Warm the body before measuring, exactly as `Exercise4BoxingSpec` does.
    * A cold measurement measures the interpreter.
    */
  def bytesOf[A](body: => A): Long =
    (0 until 20).foreach(_ => body)
    AllocationProbe.measure(body)._2

  /** Measured bytes allocated by `x :: xs` on a list of `n` cells.
    *
    * Must agree with `Sharing.cellBytes(Sharing.prependCells(n))`, and the
    * agreement must not depend on `n`.
    */
  def measurePrepend(n: Int): Long =
    val ls = MyList((0 until n)*)
    bytesOf(ls.prepended(n + 1))

  /** Measured bytes allocated by `xs.appended(x)` on a list of `n` cells. */
  def measureAppend(n: Int): Long =
    val ls = MyList((0 until n)*)
    bytesOf(ls.appended(n + 1))

  /** Measured bytes allocated by `xs.reverse` on a list of `n` cells. */
  def measureReverse(n: Int): Long =
    val ls = MyList((0 until n)*)
    bytesOf(ls.reverse)

  /** Measured bytes allocated by `xs.map(identity)` on a list of `n` cells.
    *
    * The one prediction Exercise 1 makes that no measurement here confronts.
    * `Sharing.mapCells` says this is the exercise that does it — ''a
    * measurement of `map(identity)` is the experiment that separates the two''
    * — and the two it separates are the **spine**, which the model counts, and
    * the **elements**, which it explicitly does not.
    *
    * `identity` is what makes the experiment work rather than an arbitrary
    * choice of `f`. It is the only function whose own allocation is
    * unarguably zero, so whatever is measured above the spine was spent
    * getting a value *through* `f` rather than by `f`. Whether that is zero is
    * the question; predict it before running it, and predict it knowing that
    * `A => B` is erased.
    *
    * Two different floors apply and conflating them is the trap:
    *
    *   - the instrument's, which is zero here for the same reason it is zero
    *     in the four measurements above — the body returns a reference;
    *   - the model's. `mapCells(n)` counts the cells the result *retains*, and
    *     `MyList.map` is `@tailrec`. That is the same split `appendCells` and
    *     `appendAllocatedCells` were separated to make explicit, and `map` has
    *     no second function for it.
    *
    * State which of the two you are comparing against before calling the
    * comparison an agreement.
    */
  def measureMap(n: Int): Long =
    val ls = MyList((0 until n)*)
    bytesOf(ls.map(identity))

  /** Measured bytes allocated by one `insert` into a balanced tree of `n` nodes.
    *
    * Build the tree with `MyTree.fromRange`, warm the insert, then measure a
    * single one. On `n = 2^20 - 1` the reference measurement is 504 bytes —
    * 21 nodes at 24 — and yours should land within a node or two of it.
    */
  def measureTreeInsert(n: Int): Long =
    val t = MyTree.fromRange(0, n)
    bytesOf(t.insert(n + 1))

end SharingProof

/** Exercise 9 — what insertion order does to depth, and what depth does
  * to cost.
  *
  * Every number in the guide's §15 rests on one quantity, and that quantity is
  * not a property of the tree's contents. It is a property of the order they
  * arrived in.
  */
object Balance:

  /** A tree built by inserting `0, 1, ..., n - 1` in ascending order.
    *
    * Predict its depth before running this. Then run it, and check whether your
    * prediction and the measurement agree — the gap, if any, is a challenge-log
    * entry.
    *
    * Since all values come from an ascending order then all of them goes to
    * the right branch side of the tree on each node, i.e, depth = n.
    */
  def fromSorted(n: Int): MyTree[Int] =
    MyTree.fromMyList(MyList((0 until n)*))

  /** A tree holding the same values, built so that the depth is minimal.
    *
    * `MyTree.fromRange` already does this. The exercise is to state why taking
    * the midpoint as the root is what produces the bound, in one line, in this
    * Scaladoc.
    */
  def fromBalanced(n: Int): MyTree[Int] =
    MyTree.fromRange(0, n)

  /** The factor by which one `insert` costs more on the sorted-built tree than
    * on the balanced one, at size `n`.
    *
    * Compute it from `depth`, not from a measurement: the point is that the cost
    * is a function of a structural property you can read off the tree. The spec
    * then measures it, and the two must agree.
    *
    * Returns `0.0` for `n <= 0`.
    */
  def degenerationFactor(n: Int): Double =
    if n <= 0 then 0.0
    else (fromSorted(n).depth + 1) / (fromBalanced(n).depth + 1).toDouble

end Balance
