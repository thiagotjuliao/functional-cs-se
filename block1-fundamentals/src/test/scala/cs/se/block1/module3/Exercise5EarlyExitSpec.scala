package cs.se.block1.module3

import cs.se.block1.module2.MyList
import cs.se.block1.module2.MyList.*
import java.util.concurrent.atomic.AtomicInteger

/** Exercise 5 — stopping early, and proving that it stopped.
  *
  * Every test that asserts a returned value here would pass on an
  * implementation that visits the whole list. The counting predicate is what
  * separates "returns the right answer" from "is the right function", and it is
  * the same instrument Module 2's `contains` needed for the same reason.
  */
class Exercise5EarlyExitSpec extends Module3Harness:

  private def counting[A](p: A => Boolean): (A => Boolean, AtomicInteger) =
    val n = AtomicInteger(0)
    (
      a =>
        n.incrementAndGet(); p(a)
      ,
      n
    )

  private val sample = MyList(3, 1, 4, 1, 5, 9, 2, 6)

  test("indexOf finds the first occurrence, and -1 when there is none") {
    assertEquals(EarlyExit.indexOf(sample, 3), 0)
    assertEquals(EarlyExit.indexOf(sample, 1), 1, "the *first* occurrence")
    assertEquals(EarlyExit.indexOf(sample, 6), 7)
    assertEquals(EarlyExit.indexOf(sample, 7), -1)
    assertEquals(EarlyExit.indexOf(MyList[Int](), 1), -1, "nothing is in the empty list")
  }

  test("forall and exists return what they should") {
    assert(EarlyExit.forall(MyList[Int](), (_: Int) > 0), "vacuously true on the empty list")
    assert(!EarlyExit.exists(MyList[Int](), (_: Int) > 0), "vacuously false on the empty list")
    assert(EarlyExit.forall(sample, (x: Int) => x > 0))
    assert(!EarlyExit.forall(sample, (x: Int) => x > 3))
    assert(EarlyExit.exists(sample, (x: Int) => x == 9))
    assert(!EarlyExit.exists(sample, (x: Int) => x == 100))

    // De Morgan, which is the identity the Scaladoc asks you to name.
    List[Int => Boolean](_ > 3, _ % 2 == 0, _ => true, _ => false).foreach { p =>
      assertEquals(
        EarlyExit.forall(sample, p),
        !EarlyExit.exists(sample, (x: Int) => !p(x)),
        "forall p == !exists !p"
      )
    }
  }

  test("forall and exists stop at the element that decides the answer") {
    val big = cells(1_000_000)

    val (pFail, nFail) = counting[Int](_ != 3)
    val _ = EarlyExit.forall(big, pFail)
    report("forall: elements visited before the first failure", nFail.get)
    assertEquals(nFail.get, 4, "0, 1, 2, 3 - and then it must stop")

    val (pHit, nHit) = counting[Int](_ == 3)
    val _ = EarlyExit.exists(big, pHit)
    report("exists: elements visited before the first hit", nHit.get)
    assertEquals(nHit.get, 4, "the same four, and then it must stop")

    val (pMiss, nMiss) = counting[Int](_ < 0)
    assert(!EarlyExit.exists(big, pMiss))
    assertEquals(nMiss.get, 1_000_000, "with no hit there is nothing to stop at")
  }

  test("takeWhile keeps the prefix and nothing after it") {
    assertEquals(EarlyExit.takeWhile(sample, (x: Int) => x < 5).toScalaList, List(3, 1, 4, 1))
    assertEquals(
      EarlyExit.takeWhile(sample, (x: Int) => x < 3).toScalaList,
      List.empty[Int],
      "the first element already fails"
    )
    assertEquals(EarlyExit.takeWhile(sample, (_: Int) => true).toScalaList, sample.toScalaList)
    assertEquals(EarlyExit.takeWhile(MyList[Int](), (_: Int) => true).toScalaList, List.empty[Int])

    // It must stop, not filter. 1 appears after 4 and before 5; a filter would
    // keep the later 1 as well, and the values alone would not reveal it.
    assertEquals(
      EarlyExit.takeWhile(MyList(1, 2, 9, 1, 2), (x: Int) => x < 5).toScalaList,
      List(1, 2),
      "everything after the first failure is dropped, including elements that satisfy p"
    )
  }

  test("every walk here is tail recursive") {
    val big = cells(1_000_000)
    assert(survives(EarlyExit.indexOf(big, -1)), "indexOf over a million with no match")
    assert(survives(EarlyExit.forall(big, (_: Int) >= 0)), "forall over a million")
    assert(survives(EarlyExit.exists(big, (_: Int) < 0)), "exists over a million")
    assert(survives(EarlyExit.takeWhile(big, (_: Int) >= 0)), "takeWhile keeping everything")
  }

end Exercise5EarlyExitSpec
