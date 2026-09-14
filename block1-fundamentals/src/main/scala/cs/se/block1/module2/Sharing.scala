package cs.se.block1.module2

import cs.se.block1.module1.Footprint

import scala.annotation.unused

/** Exercise 1 — the cost model, and the first thing you write.
  *
  * This exercise is out of tier order on purpose. It contains no data structure,
  * no recursion and no measurement: it is arithmetic over the object layout you
  * derived in Module 1, and its whole job is to **predict** what Exercises 3
  * through 9 will measure.
  *
  * Do it on paper first. Module 1 placed its predictive exercise seventh of
  * nine, and the result was that every measurement arrived as a new fact to
  * memorise rather than as confirmation of something already derived. The
  * difference between those two experiences is the reason this one is first.
  *
  * Everything here follows from one rule, stated in the guide, Part III.11:
  *
  *   ''You allocate the path from the root to the change. You share everything
  *   else.''
  *
  * All functions are total for non-negative inputs and return `Long`, because
  * `n` times a cell size overflows `Int` at a few tens of millions — the same
  * trap `Footprint.arrayOfIntSize` forecloses in Module 1, Exercise 7.
  */
object Sharing:

  // Note on the first run: `CellBytes` and `NodeBytes` are `val`s, so their
  // `???` throws while the object is being initialised rather than when a method
  // is called. The JVM marks the class unusable from that point, so
  // `Exercise1SharingSpec` reports one failure and three skipped tests instead
  // of four failures. That is not a defect in the suite — it is what a failed
  // static initialiser does. Implement these two first and the other three tests
  // start running.

  /** Heap cost of one cons cell: a `head` reference and a `tail` reference.
    *
    * Do not write `24`. Derive it from `cs.se.block1.module1.Footprint`, so that
    * this module inherits Module 1's layout model rather than restating its
    * conclusion. A literal here is a number that stops being checked the moment
    * the model changes.
    */
  val CellBytes: Int = Footprint.align(
    Footprint.HeaderBytes +
      2 * Footprint.ReferenceBytes
  )

  /** Heap cost of one binary tree node: a value reference and two child
    * references.
    *
    * Derive it the same way. The result is worth pausing on — a third reference
    * lands inside padding the two-reference cell was already paying for, so the
    * node and the cell cost the same. Guide, Part I.3.
    */
  val NodeBytes: Int = Footprint.align(
    Footprint.HeaderBytes +
      3 * Footprint.ReferenceBytes
  )

  /** Cells allocated by `x :: xs`, where `xs` has `n` cells.
    *
    * The answer does not mention `n`. If yours does, re-read Part I.4: the old
    * list is not touched, not copied, and not read.
    *
    * That is why `n` carries `@unused`. The parameter is here so that this
    * function has the same shape as `appendCells` and the two can be read side
    * by side; the annotation is what keeps `-Wall -Werror` from rejecting the
    * correct implementation. Treat the compiler's complaint as confirmation:
    * an implementation that reads `n` is the one that is wrong.
    */
  def prependCells(@unused n: Int): Long = 1L

  /** Cells of the old spine that `xs :+ x` must rebuild, where `xs` has `n`
    * cells.
    *
    * The last cell's `tail` would have to change, and it cannot, so it is
    * rebuilt — which forces its parent to be rebuilt, all the way to the front.
    *
    * This counts the rebuilt spine and nothing else. The cell that holds `x`
    * itself is not in it, and neither is anything an implementation allocates
    * on the way — see `appendAllocatedCells`, which counts both.
    */
  def appendCells(n: Int): Long = n

  /** Cells a stack-safe functional `xs :+ x` **allocates**, where `xs` has `n`
    * cells — as distinct from the cells its result **retains**.
    *
    * `AllocationProbe` counts allocation. The two coincide only when an
    * operation produces no garbage, which is why `reverse` measures exactly
    * `reverseCells(n) × CellBytes` and append does not.
    *
    * Three quantities, and the exercise is to keep them apart:
    *
    *   - `appendCells(n)` — the old spine, rebuilt.
    *   - what the result retains — that spine plus one cell for `x`.
    *   - what the operation allocates — the above, plus every cell it builds
    *     and discards before returning.
    *
    * The last term is not an implementation detail to be optimised away. A
    * `concat` written to stay `@tailrec` has to walk `xs` forwards and emit
    * backwards, so it materialises an intermediate spine and then consumes it.
    * The alternatives are a non-tail recursion, which holds a frame per cell
    * and overflows the stack at the sizes this module measures, or a mutable
    * builder, which is what the standard library uses and what §D of the
    * checklist forbids here. The constant is the price of the constraint;
    * derive it, do not measure it first.
    *
    * Verified against the measurement in Exercise 8 once you have both.
    */
  def appendAllocatedCells(n: Int): Long = ???

  /** Cells allocated by `xs.reverse`, where `xs` has `n` cells.
    *
    * Note what is *not* allocated: the elements. Reverse rebuilds the spine and
    * shares every value it holds.
    */
  def reverseCells(n: Int): Long = n

  /** Cells allocated by `xs.map(f)`, where `xs` has `n` cells.
    *
    * Count only the spine. Whatever `f` allocates is `f`'s business, and a
    * measurement of `map(identity)` is the experiment that separates the two —
    * Exercise 8 runs it.
    */
  def mapCells(n: Int): Long = n

  /** Bytes for a list-shaped structure of `cells` cells. */
  def cellBytes(cells: Long): Long =
    cells * CellBytes

  /** Bytes for a tree-shaped structure of `nodes` nodes. */
  def nodeBytes(nodes: Long): Long =
    nodes * NodeBytes

  /** Depth of a perfectly balanced binary tree holding `n` values.
    *
    * Depth is the number of nodes on the path from the root to a leaf, counting
    * the root. A tree of one node has depth 1, and the empty tree has depth 0.
    *
    * Worked values you must reproduce, and they are the ones Part II.6 tabulates:
    * {{{
    * balancedDepth(0)          == 0
    * balancedDepth(1)          == 1
    * balancedDepth(15)         == 4      // 2^4 - 1
    * balancedDepth(1_048_575)  == 20     // 2^20 - 1
    * balancedDepth(1_000_000)  == 20
    * }}}
    * Beware the boundary: `balancedDepth(16)` is 5, not 4. Getting that wrong is
    * an off-by-one in a limit, which is pattern 3 of `error-patterns.md`, and
    * the suite does test it.
    */
  def balancedDepth(n: Int): Int =
    if n <= 0 then 0
    else 32 - java.lang.Integer.numberOfLeadingZeros(n)

  /** Nodes allocated by one `insert` into a balanced tree of `n` nodes, when the
    * value is not already present.
    *
    * Every node on the path from the root to the insertion point is rebuilt,
    * and one new leaf is created. The count therefore exceeds the depth by
    * exactly one, which is a fact §E of the checklist asks you to explain rather
    * than assert.
    */
  def treeInsertNodes(n: Int): Long = balancedDepth(n) + 1L

  /** How many times cheaper one `insert` is than copying the whole tree.
    *
    * `nodeBytes(n) / (treeInsertNodes(n) * NodeBytes)`, as a `Double` so that
    * the answer is not silently truncated. For `n = 1_048_575` the measured
    * value is about `49,932` — verify yours lands there before trusting it.
    *
    * Returns `0.0` for the empty tree, where there is nothing to share.
    */
  def sharingRatio(n: Int): Double =
    if n == 0 then 0.0
    else nodeBytes(n) / (treeInsertNodes(n) * NodeBytes).toDouble

end Sharing
