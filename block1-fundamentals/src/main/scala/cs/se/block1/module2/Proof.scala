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
  def measurePrepend(n: Int): Long = ???

  /** Measured bytes allocated by `xs.appended(x)` on a list of `n` cells. */
  def measureAppend(n: Int): Long = ???

  /** Measured bytes allocated by `xs.reverse` on a list of `n` cells. */
  def measureReverse(n: Int): Long = ???

  /** Measured bytes allocated by one `insert` into a balanced tree of `n` nodes.
    *
    * Build the tree with `MyTree.fromRange`, warm the insert, then measure a
    * single one. On `n = 2^20 - 1` the reference measurement is 504 bytes —
    * 21 nodes at 24 — and yours should land within a node or two of it.
    */
  def measureTreeInsert(n: Int): Long = ???

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
    */
  def fromSorted(n: Int): MyTree[Int] = ???

  /** A tree holding the same values, built so that the depth is minimal.
    *
    * `MyTree.fromRange` already does this. The exercise is to state why taking
    * the midpoint as the root is what produces the bound, in one line, in this
    * Scaladoc.
    */
  def fromBalanced(n: Int): MyTree[Int] = ???

  /** The factor by which one `insert` costs more on the sorted-built tree than
    * on the balanced one, at size `n`.
    *
    * Compute it from `depth`, not from a measurement: the point is that the cost
    * is a function of a structural property you can read off the tree. The spec
    * then measures it, and the two must agree.
    *
    * Returns `0.0` for `n <= 0`.
    */
  def degenerationFactor(n: Int): Double = ???

end Balance
