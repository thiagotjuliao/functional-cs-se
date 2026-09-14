package cs.se.block1.module3

import cs.se.block1.module2.{MyTree, Sharing}
import cs.se.block1.module2.MyTree.*
import cs.se.block1.module2.MyList.*

/** Exercise 9 — the depth that stops depending on arrival order.
  *
  * The fixture that matters here is **ascending input**. A balance test run on
  * shuffled data measures nothing, because a plain BST is already acceptable on
  * shuffled data — that is pattern 11 of `error-patterns.md`, and this module's
  * §F warns about it by name.
  */
class Exercise9AvlSpec extends Module3Harness:

  private def isAvl(t: MyTree[Int]): Boolean = t match
    case Leaf => true
    case Branch(_, l, r) =>
      math.abs(Avl.balanceFactor(t)) <= 1 && isAvl(l) && isAvl(r)

  test("balanceFactor reads the difference in depths") {
    assertEquals(Avl.balanceFactor(Leaf: MyTree[Int]), 0)
    assertEquals(Avl.balanceFactor(Branch(1, Leaf, Leaf)), 0)
    assertEquals(Avl.balanceFactor(Branch(2, Branch(1, Leaf, Leaf), Leaf)), 1, "leaning left")
    assertEquals(Avl.balanceFactor(Branch(1, Leaf, Branch(2, Leaf, Leaf))), -1, "leaning right")
    assertEquals(
      Avl.balanceFactor(Branch(3, Branch(2, Branch(1, Leaf, Leaf), Leaf), Leaf)),
      2,
      "the imbalance rebalance exists to repair"
    )
  }

  test("rebalance repairs all four cases and leaves a balanced tree alone") {
    val leftLeft = Branch(3, Branch(2, Branch(1, Leaf, Leaf), Leaf), Leaf)
    val leftRight = Branch(3, Branch(1, Leaf, Branch(2, Leaf, Leaf)), Leaf)
    val rightRight = Branch(1, Leaf, Branch(2, Leaf, Branch(3, Leaf, Leaf)))
    val rightLeft = Branch(1, Leaf, Branch(3, Branch(2, Leaf, Leaf), Leaf))

    List(
      "left-left" -> leftLeft,
      "left-right" -> leftRight,
      "right-right" -> rightRight,
      "right-left" -> rightLeft
    )
      .foreach { (label, t) =>
        val b = Avl.rebalance(t)
        assertEquals(b.toMyList.toScalaList, List(1, 2, 3), s"$label changed the contents")
        assertEquals(b.depth, 2, s"$label was not flattened")
        assert(math.abs(Avl.balanceFactor(b)) <= 1, s"$label is still out of balance")
      }

    val balanced = MyTree.fromRange(0, 7)
    assertEquals(
      Avl.rebalance(balanced).toMyList.toScalaList,
      balanced.toMyList.toScalaList,
      "a balanced tree must come back with the same contents"
    )
    assertEquals(
      Avl.rebalance(balanced).depth,
      balanced.depth,
      "and the same depth: no rotation was needed"
    )
  }

  test("insertBalanced keeps the invariant under the input that breaks a BST") {
    val n = 4_096
    val plain = (0 until n).foldLeft(Leaf: MyTree[Int])((t, x) => t.insert(x))
    val avl = Avl.fromSeq(0 until n)

    report("ascending input, plain MyTree depth", plain.depth)
    report("ascending input, Avl depth", avl.depth)
    report("the AVL bound 1.44 log2(n + 2)", f"${Avl.depthBound(n)}%.2f")

    assertEquals(
      plain.depth,
      n,
      "the control must degenerate, or there is nothing to compare against"
    )
    assertEquals(avl.size, n, "no value was lost or duplicated")
    assertEquals(
      avl.toMyList.toScalaList,
      (0 until n).toList,
      "the in-order walk is still ascending"
    )
    assert(isAvl(avl), "the invariant must hold at every node, not only at the root")
    assert(
      avl.depth <= Avl.depthBound(n),
      s"depth ${avl.depth} exceeds the AVL bound ${Avl.depthBound(n)}"
    )
  }

  test("descending and shuffled input land inside the same bound") {
    val n = 4_096
    List(
      "descending" -> (n - 1 to 0 by -1),
      "shuffled" -> scala.util.Random(20260914L).shuffle(0 until n)
    )
      .foreach { (label, xs) =>
        val t = Avl.fromSeq(xs)
        report(s"$label input, Avl depth", t.depth)
        assertEquals(t.size, n, s"$label lost a value")
        assertEquals(t.toMyList.toScalaList, (0 until n).toList, s"$label is not in order")
        assert(isAvl(t), s"$label broke the invariant")
        assert(t.depth <= Avl.depthBound(n), s"$label exceeded the bound at depth ${t.depth}")
      }
  }

  test("one insert costs the path, and the path is now bounded") {
    val n = 4_096
    val avl = Avl.fromSeq(0 until n)
    val plain = (0 until n).foldLeft(Leaf: MyTree[Int])((t, x) => t.insert(x))

    val avlBytes = probeBytes(Avl.insertBalanced(avl, n))
    val plainBytes = probeBytes(plain.insert(n))
    report("one insert into the AVL tree", avlBytes)
    report("one insert into the degenerate tree", plainBytes)
    report("ratio", f"${plainBytes.toDouble / avlBytes}%.1f x")

    // The path plus the new leaf plus at most one rotation's two nodes, with
    // room for the element's box. The ceiling is stated in nodes so it reads as
    // the model rather than as this machine's byte count.
    val ceiling = (avl.depth + 1 + 2 + 1).toLong * Sharing.NodeBytes
    assert(
      avlBytes <= ceiling,
      s"$avlBytes bytes for an insert into a tree ${avl.depth} deep; the model allows $ceiling"
    )
    assert(
      plainBytes > avlBytes * 50,
      s"the degenerate tree must cost orders of magnitude more: $plainBytes against $avlBytes"
    )
  }

end Exercise9AvlSpec
