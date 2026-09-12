package cs.se.block1.module2

/** Exercise 2 (Easy) — the ADT and the three questions you can ask a cell. */
class Exercise2MyListSpec extends Module2Harness:

  test("apply builds front-to-back, and Nil is a shared singleton") {
    assertEquals(MyList[Int]().toScalaList, List.empty[Int])
    assertEquals(MyList(1).toScalaList, List(1))
    assertEquals(MyList(1, 2, 3).toScalaList, List(1, 2, 3), "the first argument is the head")

    // Structural: the shape is exactly Cons(1, Cons(2, Cons(3, Nil))).
    assertEquals(MyList(1, 2, 3), MyList.Cons(1, MyList.Cons(2, MyList.Cons(3, MyList.Nil))))

    // One object, not one per empty list. Reference equality is the assertion
    // that distinguishes a singleton from an equal copy.
    assert(
      MyList[Int]().asInstanceOf[AnyRef] eq MyList[String]().asInstanceOf[AnyRef],
      "every empty list must be the same instance"
    )
  }

  test("the covariance lets one Nil serve every element type") {
    // This compiles only because MyList is declared [+A] and Nil is
    // MyList[Nothing]. Delete the + in MyList.scala and this is the line that
    // stops compiling first — run that experiment, it is asked in the checklist.
    val ints: MyList[Int] = MyList.Nil
    val strings: MyList[String] = MyList.Nil
    val anys: MyList[Any] = ints

    assert(ints.isEmpty)
    assert(strings.isEmpty)
    assert(anys.isEmpty)

    val dogs: MyList[String] = MyList("rex")
    val animals: MyList[Any] = dogs
    assertEquals(animals.length, 1, "a MyList[String] is usable as a MyList[Any]")
  }

  test("isEmpty, length and headOption agree with each other") {
    assert(MyList[Int]().isEmpty)
    assert(!MyList(1).isEmpty)

    assertEquals(MyList[Int]().length, 0)
    assertEquals(MyList(1, 2, 3).length, 3)

    assertEquals(MyList[Int]().headOption, None)
    assertEquals(MyList(7, 8).headOption, Some(7))

    // The invariant tying them together, over many sizes.
    (0 to 50).foreach { n =>
      val xs = MyList((0 until n)*)
      assertEquals(xs.length, n, s"length disagrees at n = $n")
      assertEquals(xs.isEmpty, n == 0, s"isEmpty disagrees at n = $n")
      assertEquals(xs.headOption.isDefined, n > 0, s"headOption disagrees at n = $n")
    }
  }

  test("length is tail-recursive: a million cells must not overflow the stack") {
    val big = cells(1_000_000)
    assertEquals(big.length, 1_000_000, "if this overflows, length is not @tailrec")
  }

end Exercise2MyListSpec
