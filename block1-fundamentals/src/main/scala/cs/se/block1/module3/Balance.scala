package cs.se.block1.module3

import cs.se.block1.module2.MyTree

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
  def rotateRight[A](t: MyTree[A]): MyTree[A] = ???

  /** Rotate `t` left: the right child becomes the root. The mirror of
    * `rotateRight`, and the spec asserts they are mirrors by composing them.
    */
  def rotateLeft[A](t: MyTree[A]): MyTree[A] = ???

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
  def preservesOrder[A](before: MyTree[A], after: MyTree[A]): Boolean = ???

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
    * Computing it by calling `MyTree.depth` on both children is `O(n)` per
    * node, which makes a single insert `O(n log n)`. Real AVL trees cache the
    * height in the node. This module's `MyTree` has nowhere to cache it, so:
    * implement the honest slow version, measure it, and record in the Scaladoc
    * what caching would change and what it would cost in bytes per node.
    * Module 2's `Footprint` has the arithmetic.
    */
  def balanceFactor[A](t: MyTree[A]): Int = ???

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
  def rebalance[A](t: MyTree[A])(using Ordering[A]): MyTree[A] = ???

  /** Insert `x`, rebalancing on the way back up.
    *
    * Same contract as `MyTree.insert` — a value already present returns an
    * equal tree — with the invariant of this object maintained afterwards.
    *
    * Rebalancing happens as the recursion unwinds, which means this function is
    * '''not''' tail recursive and must not be annotated. Say why in the
    * Scaladoc, and say what bounds it instead. That bound is the entire
    * difference between this exercise and Module 2's.
    *
    * An insert performs at most one rotation, single or double, however large
    * the tree. The spec counts them.
    */
  def insertBalanced[A](t: MyTree[A], x: A)(using Ordering[A]): MyTree[A] = ???

  /** Build a tree from `xs` by repeated `insertBalanced`, left to right.
    *
    * Module 2's `fromMyList` over ascending input produced depth `n`. This must
    * produce a depth within the AVL bound for the same input, and §E asks for
    * both numbers side by side.
    */
  def fromSeq[A](xs: Seq[A])(using Ordering[A]): MyTree[A] = ???

  /** The AVL depth bound for `n` nodes: `1.44 * log2(n + 2)`.
    *
    * Derived rather than measured, and used by the spec as a ceiling. The
    * classical bound is `1.4405 log2(n + 2) - 0.3277`; this is the rounded,
    * weaker form, which is the safe direction to round in for an assertion.
    * Say in the Scaladoc why rounding the other way would make the test lie.
    */
  def depthBound(n: Int): Double = ???

end Avl
