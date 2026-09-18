package cs.se.block1.module2

import cs.se.block1.module2.MyList.*
import scala.math.Ordering.Implicits.infixOrderingOps

/** Exercises 6, 7 and 9 — the binary search tree, where sharing stops being a
  * curiosity.
  *
  * Same two-case shape as the list, with two children instead of one tail. The
  * type is given; the behaviour is yours.
  *
  * The **invariant** is not expressed in the type and must be maintained by
  * every operation: everything in `left` is strictly less than `value`, and
  * everything in `right` is strictly greater. That invariant is what makes
  * lookup `O(depth)` — at each node you discard half of what remains. Note what
  * it does *not* buy: any bound on the depth itself. Exercise 9 is about that.
  */
enum MyTree[+A]:
  case Leaf
  case Branch(value: A, left: MyTree[A], right: MyTree[A])

object MyTree:

  extension [A](t: MyTree[A])

    /** Insert `x`, returning a new tree and leaving `t` untouched and valid.
      *
      * Inserting a value already present returns a tree equal to the original.
      * Whether it returns the *same* tree — sharing everything, allocating
      * nothing — or an equal copy is a choice, and the spec measures which one
      * you made. One of them is free and one costs the path.
      *
      * Allocation: every node on the path from the root to the insertion point
      * is rebuilt, plus one new leaf. Everything hanging off that path is
      * shared. Exercise 1 predicted the count; Exercise 8 measures it.
      *
      * Recursion here is bounded by **depth**, not by size, so it is not
      * required to be `@tailrec` — a balanced tree of a billion nodes recurses
      * 30 deep. §D of the checklist asks you to document that reasoning, because
      * it stops being true the moment the tree degenerates, which is exactly
      * what Exercise 9 provokes.
      */
    def insert(x: A)(using Ordering[A]): MyTree[A] = t match
      case Leaf => Branch(x, Leaf, Leaf)
      case Branch(v, l, r) if x < v => Branch(v, l.insert(x), r)
      case Branch(v, l, r) if x > v => Branch(v, l, r.insert(x))
      case t => t

    /** Whether `x` is present. `O(depth)`, and for the same reason. */
    def contains(x: A)(using Ordering[A]): Boolean = t match
      case Leaf => false
      case Branch(v, l, _) if x < v => l.contains(x)
      case Branch(v, _, r) if x > v => r.contains(x)
      case _ => true

    /** Number of `Branch` nodes. `Leaf` counts as zero. */
    def size: Int = t match
      case Leaf => 0
      case Branch(_, l, r) => 1 + l.size + r.size

    /** Nodes on the longest root-to-leaf path, counting the root.
      *
      * `Leaf.depth == 0`; a single `Branch` has depth 1. This must agree with
      * `Sharing.balancedDepth(size)` for any balanced tree — and disagree
      * violently for a degenerate one, which is the whole point of Exercise 9.
      */
    def depth: Int = t match
      case Leaf => 0
      case Branch(_, l, r) => Math.max(1 + l.depth, 1 + r.depth)

    /** Fold the values in ascending order: left subtree, then value, then right.
      *
      * This is the operation that makes the BST invariant observable. Every law
      * in Exercise 7 is stated in terms of it.
      */
    def foldInOrder[B](z: B)(f: (B, A) => B): B = t match
      case Leaf => z
      case Branch(v, l, r) =>
        r.foldInOrder(f(l.foldInOrder(z)(f), v))(f)

    /** The values in ascending order.
      *
      * Must be expressible through `foldInOrder` — if you find yourself writing
      * a second traversal, one of the two is doing something the other should
      * have. Mind Part V.17 while you are at it: building this by appending is
      * quadratic in the number of nodes.
      */
    def toMyList: MyList[A] =
      t.foldInOrder(Nil: MyList[A])((ls, a) => ls.prepended(a)).reverse

    /** Apply `f` to every value, preserving the *shape* of the tree.
      *
      * Note what this is not: it is not `map` on a set. If `f` is not monotone
      * the result still has the original shape and therefore **violates the BST
      * invariant**. That is not a defect to fix here; it is a constraint to
      * document, and the spec asserts the shape rather than the ordering.
      */
    def treeMap[B](f: A => B): MyTree[B] = t match
      case Leaf => Leaf
      case Branch(v, l, r) => Branch(f(v), l.treeMap(f), r.treeMap(f))

  end extension

  /** Build a tree by inserting every element of `xs`, left to right.
    *
    * The depth of the result depends entirely on the order `xs` arrives in,
    * which is the subject of Exercise 9.
    */
  def fromMyList[A](xs: MyList[A])(using Ordering[A]): MyTree[A] =
    xs.foldLeft(Leaf: MyTree[A])((t, a) => t.insert(a))

  /** Build a perfectly balanced tree over the integers `lo` until `hi`.
    *
    * `fromRange(0, 15)` must have depth 4 and size 15. Take the midpoint as the
    * root and recurse on the halves; that is the only way to get the depth bound
    * without rebalancing, and it is how the guide's §15 measurement was set up.
    */
  def fromRange(lo: Int, hi: Int): MyTree[Int] =
    def midPoints(lo: Int, hi: Int): LazyList[Int] =
      if lo > hi then LazyList()
      else
        val mid = (lo + hi) / 2
        mid #:: (midPoints(lo, mid - 1) #::: midPoints(mid + 1, hi))
    end midPoints

    midPoints(lo, hi - 1).foldLeft(Leaf: MyTree[Int])((t, a) => t.insert(a))

end MyTree
