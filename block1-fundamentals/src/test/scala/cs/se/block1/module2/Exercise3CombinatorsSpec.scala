package cs.se.block1.module2

/** Exercise 3 — map, filter and reverse, and the laws that pin them. */
class Exercise3CombinatorsSpec extends Module2Harness:

  // A def, not a val: a val is built at construction time, so an unimplemented
  // MyList.apply would fail the whole suite with one initializationError instead
  // of failing each test by name.
  private def sample = MyList(3, 1, 4, 1, 5, 9, 2, 6)

  test("map applies f in order and preserves length") {
    assertEquals(MyList[Int]().map(_ + 1).toScalaList, List.empty[Int])
    assertEquals(sample.map(_ * 2).toScalaList, sample.toScalaList.map(_ * 2))
    assertEquals(sample.map(_.toString).toScalaList, sample.toScalaList.map(_.toString))
    assertEquals(sample.map(_ + 1).length, sample.length, "map must not change the length")
  }

  test("map is a functor: identity and composition") {
    // These two laws are the whole definition of a functor, and Block 2 will
    // ask you to state them abstractly. Here they are concrete.
    assertEquals(sample.map(identity).toScalaList, sample.toScalaList, "identity law")

    val f: Int => Int = _ * 3
    val g: Int => String = i => s"<$i>"
    assertEquals(
      sample.map(f).map(g).toScalaList,
      sample.map(f andThen g).toScalaList,
      "composition law: map(f).map(g) == map(f andThen g)"
    )
  }

  test("filter keeps order and is idempotent for the same predicate") {
    assertEquals(MyList[Int]().filter(_ > 0).toScalaList, List.empty[Int])
    assertEquals(sample.filter(_ > 3).toScalaList, List(4, 5, 9, 6))
    assertEquals(sample.filter(_ => false).toScalaList, List.empty[Int])
    assertEquals(sample.filter(_ => true).toScalaList, sample.toScalaList)

    val p: Int => Boolean = _ % 2 == 0
    assertEquals(sample.filter(p).filter(p).toScalaList, sample.filter(p).toScalaList)
  }

  test("reverse is an involution and preserves the multiset of elements") {
    assertEquals(MyList[Int]().reverse.toScalaList, List.empty[Int])
    assertEquals(sample.reverse.toScalaList, sample.toScalaList.reverse)
    assertEquals(
      sample.reverse.reverse.toScalaList,
      sample.toScalaList,
      "reverse twice is identity"
    )
    assertEquals(sample.reverse.length, sample.length)

    // Head and last swap. Stated through headOption so it holds for the empty
    // list too, where both sides are None.
    assertEquals(sample.reverse.headOption, sample.toScalaList.lastOption)
    assertEquals(MyList[Int]().reverse.headOption, None)
  }

  test("reverse is tail-recursive at a million cells") {
    val big = cells(1_000_000)
    assertEquals(
      big.reverse.headOption,
      Some(999_999),
      "if this overflows, reverse is not @tailrec"
    )
  }

end Exercise3CombinatorsSpec
