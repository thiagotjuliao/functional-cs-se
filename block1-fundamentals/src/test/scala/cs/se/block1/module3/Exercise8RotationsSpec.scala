package cs.se.block1.module3

import cs.se.block1.module2.{MyTree, Sharing}
import cs.se.block1.module2.MyTree.*
import cs.se.block1.module2.MyList.*

/** Exercise 8 — shape changes, contents do not. */
class Exercise8RotationsSpec extends Module3Harness:

  private val leftSpine: MyTree[Int] =
    Branch(3, Branch(2, Branch(1, Leaf, Leaf), Leaf), Leaf)
  private val rightSpine: MyTree[Int] =
    Branch(1, Leaf, Branch(2, Leaf, Branch(3, Leaf, Leaf)))

  test("rotateRight lifts the left child and keeps the order") {
    val r = Rotations.rotateRight(leftSpine)
    report(
      "before: depth / in-order",
      s"${leftSpine.depth} / ${leftSpine.toMyList.toScalaList.mkString(", ")}"
    )
    report("after:  depth / in-order", s"${r.depth} / ${r.toMyList.toScalaList.mkString(", ")}")

    assertEquals(r.toMyList.toScalaList, List(1, 2, 3), "the in-order walk is the law")
    assertEquals(r.depth, 2, "a three-node spine becomes two deep")
    assertEquals(r.size, 3, "a rotation neither adds nor drops a node")
    assert(Rotations.preservesOrder(leftSpine, r))
  }

  test("rotateLeft is the mirror") {
    val r = Rotations.rotateLeft(rightSpine)
    assertEquals(r.toMyList.toScalaList, List(1, 2, 3))
    assertEquals(r.depth, 2)
    assert(Rotations.preservesOrder(rightSpine, r))
  }

  test("the two rotations undo each other") {
    assertEquals(
      Rotations.rotateRight(Rotations.rotateLeft(rightSpine)).toMyList.toScalaList,
      rightSpine.toMyList.toScalaList
    )
    assertEquals(Rotations.rotateRight(Rotations.rotateLeft(rightSpine)).depth, rightSpine.depth)
    assertEquals(Rotations.rotateLeft(Rotations.rotateRight(leftSpine)).depth, leftSpine.depth)
  }

  test("a rotation that cannot be performed is a no-op, not an error") {
    val single: MyTree[Int] = Branch(1, Leaf, Leaf)
    assertEquals(Rotations.rotateRight(single), single, "no left child to lift")
    assertEquals(Rotations.rotateLeft(single), single, "no right child to lift")
    assertEquals(Rotations.rotateRight(Leaf: MyTree[Int]), Leaf, "the empty tree rotates to itself")
    assertEquals(Rotations.rotateRight(rightSpine), rightSpine, "a right spine has no left child")
  }

  test("preservesOrder rejects a tree whose in-order walk changed") {
    assert(Rotations.preservesOrder(leftSpine, Rotations.rotateRight(leftSpine)))
    assert(
      !Rotations.preservesOrder(leftSpine, Branch(9, Leaf, Leaf)),
      "a predicate that accepts anything is not a law"
    )
    assert(
      !Rotations.preservesOrder(MyTree.fromRange(0, 3), MyTree.fromRange(0, 3).treeMap(-_)),
      "Module 2's challenge 28: shape preserved, order destroyed"
    )
  }

  test("a rotation allocates two nodes, not a subtree") {
    val deep =
      Branch(3, Branch(2, MyTree.fromRange(0, 1_000), Leaf), MyTree.fromRange(2_000, 3_000))
    val bytes = probeBytes(Rotations.rotateRight(deep))
    report("bytes to rotate the root of a 2,000-node tree", bytes)
    report("nodes", bytes / Sharing.NodeBytes)

    assertEquals(
      bytes,
      2L * Sharing.NodeBytes,
      s"a rotation rebuilds exactly the two nodes whose links change and shares the rest. " +
        s"Anything above ${2 * Sharing.NodeBytes} is a subtree that was copied instead of shared"
    )
  }

end Exercise8RotationsSpec
