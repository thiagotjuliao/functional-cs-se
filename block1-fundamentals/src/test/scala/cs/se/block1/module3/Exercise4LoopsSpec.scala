package cs.se.block1.module3

/** Exercise 4 — the loops, transcribed.
  *
  * Each test carries the imperative original it was translated from, so the
  * correspondence can be checked line against line rather than trusted.
  */
class Exercise4LoopsSpec extends Module3Harness:

  test("factorial matches the loop it came from") {
    def reference(n: Int): Long =
      var acc = 1L
      var i = 2
      while i <= n do
        acc *= i; i += 1
      acc

    (0 to 20).foreach(n => assertEquals(Loops.factorial(n), reference(n), s"factorial($n)"))
    assertEquals(Loops.factorial(0), 1L, "the empty product is one")
    assertEquals(Loops.factorial(1), 1L)
    assertEquals(Loops.factorial(20), 2_432_902_008_176_640_000L, "the largest that fits a Long")
    assertEquals(Loops.factorial(-3), 1L, "an empty range yields the identity, as the loop does")

    // Both sides of the documented domain. Asserting only the last input that
    // works leaves the first one that does not with no call site at all, which
    // is how pattern 3 survived a green suite twice.
    assertEquals(
      Loops.factorial(21),
      -4_249_290_049_419_214_848L,
      "21! exceeds Long.MaxValue by 5.54x and wraps; the wrap is the contract, not an accident"
    )
    assert(
      Loops.factorial(21) < Loops.factorial(20),
      "and the wrap is silent: n! stops being monotone"
    )
  }

  test("fibonacci matches the loop, including the base cases") {
    def reference(n: Int): Long =
      var a = 0L
      var b = 1L
      var i = 0
      while i < n do
        val t = a + b; a = b; b = t; i += 1
      a

    (0 to 90).foreach(n => assertEquals(Loops.fibonacci(n), reference(n), s"fibonacci($n)"))
    assertEquals(Loops.fibonacci(0), 0L)
    assertEquals(Loops.fibonacci(1), 1L)
    assertEquals(Loops.fibonacci(10), 55L)
    assertEquals(Loops.fibonacci(90), 2_880_067_194_370_816_120L)

    // Both sides of this domain too. F(92) is the largest that fits.
    assertEquals(Loops.fibonacci(92), 7_540_113_804_746_346_429L, "the largest that fits a Long")
    assertEquals(
      Loops.fibonacci(93),
      -6_246_583_658_587_674_878L,
      "F(93) wraps, and the sign is the only visible symptom"
    )

    // The termination test is an ordering, not an identity. Written as
    // `i == n`, this agreed with the reference across every fixture above and
    // ran 2^32 - 3 iterations before returning garbage for any n < 0 — a
    // tail call has no stack to overflow, so nothing announced the defect.
    // Error pattern 14.
    List(-1, -3, -1000, Int.MinValue).foreach { n =>
      assertEquals(
        Loops.fibonacci(n),
        reference(n),
        s"fibonacci($n) must stop where the loop stopped"
      )
      assertEquals(Loops.fibonacci(n), 0L, s"fibonacci($n)")
    }

    // The defining recurrence, checked on the implementation rather than on the
    // reference: if these agree for every n and the recurrence holds, the
    // translation preserved the function and not merely a table of values.
    (2 to 90).foreach { n =>
      assertEquals(
        Loops.fibonacci(n),
        Loops.fibonacci(n - 1) + Loops.fibonacci(n - 2),
        s"F($n) = F(${n - 1}) + F(${n - 2})"
      )
    }
  }

  test("fibonacci has no ceiling, which the loop also did not") {
    assert(survives(Loops.fibonacci(1_000_000)), "an iterative fibonacci must not build a stack")
  }

  test("reverseDigits matches the loop, overflow included") {
    def reference(n: Int): Int =
      var acc = 0
      var m = n
      while m != 0 do
        acc = acc * 10 + m % 10; m /= 10
      acc

    List(0, 7, 42, 1024, 100, 120, -42, -1024, Int.MaxValue, Int.MinValue + 1).foreach { n =>
      assertEquals(Loops.reverseDigits(n), reference(n), s"reverseDigits($n)")
    }
    assertEquals(Loops.reverseDigits(1024), 4201)
    assertEquals(Loops.reverseDigits(0), 0)
    assertEquals(Loops.reverseDigits(100), 1, "leading zeros vanish, as they do in the loop")

    // A faithful translation preserves the bugs. 1,999,999,999 reverses to a
    // value that does not fit an Int, and both versions wrap the same way.
    report("reverseDigits(1999999999)", Loops.reverseDigits(1_999_999_999))
    assertEquals(
      Loops.reverseDigits(1_999_999_999),
      reference(1_999_999_999),
      "the translation must wrap exactly where the loop wrapped, not fix it silently"
    )
  }

end Exercise4LoopsSpec
