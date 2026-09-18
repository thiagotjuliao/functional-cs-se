package cs.se.block1.module3

import cs.se.block1.module2.MyTree
import MyTree.*
import cs.se.block1.module3.Rotations.*

/** Exercise 8 — changing a tree's shape without changing what it holds.
  *
  * Module 2 proved that a BST fed sorted input becomes a spine of depth `n`,
  * that one insert into such a tree costs 4,097 nodes, and that `contains` then
  * visits 4,096 of them. Module 2 also named the production inputs that arrive
  * sorted. This exercise is the first half of the repair. Guide, Part V.17.
  *
  * A rotation in an immutable tree is a pattern match that rebuilds the nodes
  * whose links change and shares everything hanging below them. It allocates
  * two nodes. Not two per level — two.
  */
object Rotations:

  /** Rotate `t` right: the left child becomes the root.
    *
    * {{{
    *       3                 2
    *      /                 / \
    *     2        ==>      1   3
    *    /
    *   1
    * }}}
    *
    * A tree with no left child is returned unchanged rather than rejected: a
    * rotation that cannot be performed is not an error, it is a no-op, and the
    * rebalancing in Exercise 9 relies on that being total.
    */
  def rotateRight[A](t: MyTree[A]): MyTree[A] = t match
    case Branch(v, Branch(lv, ll, lr), r) => Branch(lv, ll, Branch(v, lr, r))
    case other => other

  /** Rotate `t` left: the right child becomes the root. The mirror of
    * `rotateRight`, and the spec asserts they are mirrors by composing them.
    */
  def rotateLeft[A](t: MyTree[A]): MyTree[A] = t match
    case Branch(v, l, Branch(rv, rl, rr)) => Branch(rv, Branch(v, l, rl), rr)
    case other => other

  /** The law that makes a rotation legal, as a predicate the spec can check.
    *
    * `true` when `before` and `after` have the same in-order walk.
    *
    * '''This is the whole justification for rotations existing.''' A rotation
    * changes the shape and not the contents, so every BST invariant survives
    * it; anything that changed the in-order sequence would not be a rotation
    * but a corruption. Module 2's challenge 28 is what happens when a
    * shape-preserving operation is allowed to disturb the order instead.
    *
    * Express it through `MyTree.toMyList` and nothing else.
    */
  def preservesOrder[A](before: MyTree[A], after: MyTree[A]): Boolean =
    before.toMyList == after.toMyList

end Rotations

/** Exercise 9 — the invariant that makes depth stop depending on arrival order.
  *
  * A rotation fixes one local imbalance. AVL is the rule that says when to
  * rotate, and the bound it buys:
  *
  *   ''For every node, the depths of its two subtrees differ by at most 1.''
  *
  * An AVL tree of `n` nodes has depth at most `1.44 log2(n + 2)`. Weaker than
  * perfect balance, far cheaper to maintain, and enough — it keeps the depth
  * within a constant factor of the minimum whatever order the data arrives in.
  * Guide, Part V.18 and V.19.
  *
  * Recursion here is bounded by depth and, unlike Module 2's `MyTree`, that is
  * now a promise the '''code''' keeps rather than one the caller keeps. Part
  * VI.22 is the difference, and §G will ask you to state it.
  */
object Avl:

  /** `depth(left) - depth(right)` for a branch; `0` for a leaf.
    *
    * The AVL invariant is that this is in `{-1, 0, +1}` at every node.
    *
    * `MyTree.depth` caches nothing, so one call costs the subtree it is asked
    * about. Worse, all four guards of `rebalance` open with `balanceFactor(t)`,
    * and on a balanced node — nearly every node on the path — all four fail on
    * that first test and recompute it from scratch. Measured:
    *
    * {{{
    *      n   depth   depth() calls   nodes visited   visited / n
    *   4096      13             104          32,763           8.0
    * }}}
    *
    * `104 = 8 x 13`: eight traversals per level, thirteen levels. The subtree
    * at the root holds n nodes, the next n/2, and the geometric sum gives ~8n —
    * one insert walks the whole tree eight times. So `insertBalanced` is `O(n)`
    * and `fromSeq` is `O(n^2)`, measured at 1.9, 6.8, 29.2 and 107.8 ms for
    * n = 512, 1024, 2048, 4096. Doubling n quadruples the time.
    *
    * Caching the height in the node makes this `O(1)`, the insert `O(log n)`
    * and `fromSeq` `O(n log n)`. The price, from Module 2's `Footprint`:
    * `Branch` is header 12 + three 4-byte references = 24 bytes exactly, and an
    * added `Int` gives 28, which aligns to 32. A third more per node, across
    * the whole tree, permanently. Challenge 38 carries the derivation.
    */
  def balanceFactor[A](t: MyTree[A]): Int = t match
    case Leaf => 0
    case Branch(_, l, r) => l.depth - r.depth

  /** Restore the AVL invariant at the root of `t`, assuming both subtrees
    * already satisfy it.
    *
    * Four cases, and the two "inner" ones need two rotations because a single
    * rotation on an inner-leaning child moves the problem rather than fixing
    * it:
    *
    * {{{
    * left-left     +2, left child leaning left     rotateRight(t)
    * left-right    +2, left child leaning right    rotateLeft on left, then Right
    * right-right   -2, right child leaning right   rotateLeft(t)
    * right-left    -2, right child leaning left    rotateRight on right, then Left
    * }}}
    *
    * A tree already in balance is returned unchanged, and the spec checks that
    * — a `rebalance` that rotates when it did not need to is correct and
    * allocates for nothing.
    */
  def rebalance[A](t: MyTree[A]): MyTree[A] =
    t match
      case Branch(_, l, _) if balanceFactor(t) == 2 && balanceFactor(l) >= 0 =>
        rotateRight(t)
      case Branch(v, l, r) if balanceFactor(t) == 2 && balanceFactor(l) <= 0 =>
        rotateRight(Branch(v, rotateLeft(l), r))
      case Branch(_, _, r) if balanceFactor(t) == -2 && balanceFactor(r) <= 0 =>
        rotateLeft(t)
      case Branch(v, l, r) if balanceFactor(t) == -2 && balanceFactor(r) >= 0 =>
        rotateLeft(Branch(v, l, rotateRight(r)))
      case other => other

  /** Insert `x`, rebalancing on the way back up.
    *
    * Same contract as `MyTree.insert` — a value already present returns an
    * equal tree — with the invariant of this object maintained afterwards.
    *
    * Rebalancing happens as the recursion unwinds — each `Branch` is rebuilt and
    * handed to `rebalance` on the way back up — so the recursive call is not the
    * last operation and `@tailrec` would be rejected. What bounds it instead is
    * the AVL invariant itself: the recursion is as deep as the tree, and the
    * tree is at most `1.44 log2(n + 2)` deep. A billion elements recurse about
    * 44 frames.
    *
    * That bound is the entire difference from Module 2. There, depth bounded the
    * recursion too, but nothing bounded the depth: ascending input produced a
    * spine of 4,096 frames. Here the promise is kept by the code rather than by
    * the caller.
    *
    * The comparison goes through `ord.lt`/`ord.gt` rather than `x < v`. The
    * latter expands to `new ord.OrderingOps(x)` — 24 bytes per comparison, one
    * comparison per level — which escape analysis deletes only while this method
    * stays under the inlining budget, and `rebalance` pushes it to the edge of
    * that budget. Pattern 20 of `error-patterns.md` has the measurements.
    *
    * An insert performs at most one rotation, single or double, however large
    * the tree. The spec counts them.
    */
  def insertBalanced[A](t: MyTree[A], x: A)(using ord: Ordering[A]): MyTree[A] =
    t match
      case Leaf => Branch(x, Leaf, Leaf)
      case Branch(v, l, r) if ord.lt(x, v) => rebalance(Branch(v, insertBalanced(l, x), r))
      case Branch(v, l, r) if ord.gt(x, v) => rebalance(Branch(v, l, insertBalanced(r, x)))
      case t => t

  /** Build a tree from `xs` by repeated `insertBalanced`, left to right.
    *
    * Module 2's `fromMyList` over ascending input produced depth `n`. This must
    * produce a depth within the AVL bound for the same input, and §E asks for
    * both numbers side by side.
    */
  def fromSeq[A](xs: Seq[A])(using Ordering[A]): MyTree[A] =
    xs.foldLeft(Leaf: MyTree[A]): (t, a) =>
      insertBalanced(t, a)

  /** The AVL depth bound for `n` nodes: `1.44 * log2(n + 2)`.
    *
    * Derived rather than measured, and used by the spec as a ceiling. The
    * classical bound is `1.4405 log2(n + 2) - 0.3277`; this is the rounded,
    * weaker form, which is the safe direction to round in for an assertion.
    *
    * Rounding the other way would make the test lie, because the ceiling has to
    * clear the tallest tree AVL actually permits — the Fibonacci tree, which at
    * height h holds `F(h + 2) - 1` nodes. Measured against it:
    *
    * {{{
    *       n   tallest legal AVL   1.44 log2(n+2)   classical   margin
    *    1023                  14            14.40       14.08     0.40
    *    4096                  16            17.28       16.96     1.28
    *   65535                  22            23.04       22.72     1.04
    * }}}
    *
    * The thinnest margin measured is 0.40, and the `- 0.3277` term alone eats
    * 0.33 of it. Tighten the constant as well and the ceiling drops below a
    * height a correct AVL tree is allowed to reach: the assertion then fails on
    * an implementation with no defect, and blames the tree rather than the
    * model. A ceiling that is too loose can only fail to catch a bad tree; a
    * ceiling that is too tight accuses a good one.
    */
  def depthBound(n: Int): Double =
    1.44 * Math.log(n + 2) / Math.log(2)

end Avl
