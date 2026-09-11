package cs.se.block1.module2

/** Exercise 4 (Easy) — the two folds, and the asymmetry between them.
  *
  * The last test asserts that `foldRight` *fails*. That is deliberate: a
  * `foldRight` which survives a million elements is one that quietly reversed
  * the list first, and hiding the cost is worse than paying it. Module 3 removes
  * the limit properly, with trampolining.
  */
class Exercise4FoldsSpec extends Module2Harness:

  // A def, not a val — see Exercise3CombinatorsSpec for why.
  private def sample = MyList(1, 2, 3, 4, 5)

  test("foldLeft associates to the left and foldRight to the right") {
    assertEquals(MyList[Int]().foldLeft(0)(_ + _), 0, "the empty fold is the seed")
    assertEquals(sample.foldLeft(0)(_ + _), 15)

    // A non-commutative operation is the only way to see the direction.
    assertEquals(sample.foldLeft("")((acc, a) => s"($acc+$a)"), "(((((+1)+2)+3)+4)+5)")
    assertEquals(sample.foldRight("")((a, acc) => s"($a+$acc)"), "(1+(2+(3+(4+(5+)))))")
  }

  test("foldRight with Cons rebuilds the list; foldLeft with Cons reverses it") {
    // The cleanest statement of what the two directions mean.
    val rebuilt = sample.foldRight(MyList.Nil: MyList[Int])((a, acc) => MyList.Cons(a, acc))
    assertEquals(rebuilt.toScalaList, sample.toScalaList, "foldRight over Cons is the identity")

    val reversed = sample.foldLeft(MyList.Nil: MyList[Int])((acc, a) => MyList.Cons(a, acc))
    assertEquals(reversed.toScalaList, sample.toScalaList.reverse, "foldLeft over Cons reverses")
  }

  test("concat shares its right argument and rebuilds only its left") {
    assertEquals(MyList[Int]().concat(sample).toScalaList, sample.toScalaList)
    assertEquals(sample.concat(MyList[Int]()).toScalaList, sample.toScalaList)
    assertEquals(MyList(1, 2).concat(MyList(3, 4)).toScalaList, List(1, 2, 3, 4))

    // Associativity: (MyList, concat, Nil) is a monoid, and Block 2 will name it.
    val a = MyList(1, 2)
    val b = MyList(3)
    val c = MyList(4, 5)
    assertEquals(a.concat(b).concat(c).toScalaList, a.concat(b.concat(c)).toScalaList)

    // The right argument is shared, not copied: the tail of the result from the
    // split point on must be the very same object.
    val right = MyList(9, 8, 7)
    val joined = MyList(1).concat(right)
    joined match
      case MyList.Cons(_, t) =>
        assert(
          t.asInstanceOf[AnyRef] eq right.asInstanceOf[AnyRef],
          "concat must share its right argument, not rebuild it"
        )
      case MyList.Nil => fail("joined cannot be empty")
  }

  test("appended puts the element at the back and costs a whole rebuild") {
    assertEquals(MyList[Int]().appended(1).toScalaList, List(1))
    assertEquals(sample.appended(6).toScalaList, List(1, 2, 3, 4, 5, 6))
    assertEquals(sample.appended(6).length, sample.length + 1)
  }

  test("foldLeft survives a million elements and foldRight does not") {
    val big = Building.byPrepend(1_000_000)
    assertEquals(big.foldLeft(0L)((acc, _) => acc + 1L), 1_000_000L, "foldLeft must be @tailrec")

    val overflowed =
      try
        val _ = big.foldRight(0L)((_, acc) => acc + 1L)
        false
      catch case _: StackOverflowError => true

    report("foldRight over 1,000,000 overflowed", overflowed)
    assert(
      overflowed,
      """foldRight completed over a million elements, which means it is not the
        |natural right fold: something reversed the list or trampolined the
        |recursion. Leave the limit visible here; Module 3 is where it is removed.""".stripMargin
    )
  }

end Exercise4FoldsSpec
