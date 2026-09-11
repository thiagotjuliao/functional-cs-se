package cs.se.block1.module2

/** Exercise 7 (Medium) — traversal, and the law that makes the invariant
  * observable.
  */
class Exercise7TreeFoldSpec extends Module2Harness:

  private def build(xs: Int*): MyTree[Int] =
    xs.foldLeft(MyTree.Leaf: MyTree[Int])((t, x) => t.insert(x))

  test("foldInOrder visits ascending, whatever the insertion order was") {
    assertEquals((MyTree.Leaf: MyTree[Int]).foldInOrder(0)(_ + _), 0)

    val a = build(8, 4, 12, 2, 6, 10, 14)
    val b = build(2, 4, 6, 8, 10, 12, 14)
    val c = build(14, 12, 10, 8, 6, 4, 2)

    val collect = (t: MyTree[Int]) => t.foldInOrder(List.empty[Int])((acc, x) => x :: acc).reverse
    assertEquals(collect(a), List(2, 4, 6, 8, 10, 12, 14))
    assertEquals(collect(b), collect(a), "shape differs, order does not")
    assertEquals(collect(c), collect(a), "shape differs, order does not")

    assertEquals(a.foldInOrder(0)(_ + _), 56, "a fold that ignores order still sees every value")
  }

  test("toList is sorted, and is expressible through foldInOrder") {
    assertEquals((MyTree.Leaf: MyTree[Int]).toMyList.toScalaList, List.empty[Int])

    val values = shuffled(200)
    val t = values.foldLeft(MyTree.Leaf: MyTree[Int])((acc, x) => acc.insert(x))
    val out = t.toMyList.toScalaList

    assertEquals(out, values.sorted, "an in-order walk of a BST is a sort")
    assertEquals(out.length, t.size)
    assertEquals(
      out,
      t.foldInOrder(List.empty[Int])((acc, x) => x :: acc).reverse,
      "toMyList must agree with foldInOrder; two traversals that disagree means one is wrong"
    )
  }

  test("treeMap preserves shape, and does not promise to preserve the invariant") {
    val t = build(8, 4, 12, 2, 6)
    val doubled = t.treeMap(_ * 2)

    assertEquals(doubled.size, t.size, "shape: same number of nodes")
    assertEquals(doubled.depth, t.depth, "shape: same depth")
    assertEquals(doubled.toMyList.toScalaList, t.toMyList.toScalaList.map(_ * 2))

    // Monotone f keeps the invariant; the in-order walk stays sorted.
    assertEquals(doubled.toMyList.toScalaList, doubled.toMyList.toScalaList.sorted)

    // A non-monotone f keeps the SHAPE and breaks the ORDERING. This is not a
    // defect to fix — it is the documented limit of treeMap, and the assertion
    // records it so that nobody later "fixes" it into a re-insertion.
    val negated = t.treeMap(x => -x)
    assertEquals(negated.size, t.size, "shape survives a non-monotone function")
    assertNotEquals(
      negated.toMyList.toScalaList,
      negated.toMyList.toScalaList.sorted,
      "a non-monotone treeMap is expected to leave the tree out of order"
    )
  }

  test("the tree's own recursion is bounded by depth, not by size") {
    // A balanced tree of a million nodes is 20 deep, so a non-tail-recursive
    // walk is perfectly safe. This is the claim §D asks you to document.
    val big = MyTree.fromRange(0, 1_000_000)
    report("depth of a balanced million-node tree", big.depth)
    assertEquals(big.size, 1_000_000)
    assertEquals(big.foldInOrder(0L)((acc, _) => acc + 1L), 1_000_000L)
  }

end Exercise7TreeFoldSpec
