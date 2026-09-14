package cs.se.block1.module3

/** Exercise 1 — the instrument.
  *
  * Tested against functions whose behaviour is known independently of the
  * probe, so that a broken probe cannot certify itself. The harness carries its
  * own `survives` for exactly that reason.
  */
class Exercise1StackProbeSpec extends Module3Harness:

  private def deep(n: Int): Int = if n == 0 then 0 else 1 + deep(n - 1)

  test("survives distinguishes a return from a stack overflow") {
    assert(StackProbe.survives(42), "a value that needs no stack survives")
    assert(StackProbe.survives(deep(100)), "a hundred frames is nothing")
    assert(!StackProbe.survives(deep(Int.MaxValue)), "an unbounded recursion does not survive")
  }

  test("survives does not swallow anything except StackOverflowError") {
    intercept[IllegalStateException] {
      StackProbe.survives(throw IllegalStateException("not a stack problem"))
    }
    // A probe that reported `false` here would let a broken implementation read
    // as an exhausted stack, which is the one failure mode that would make every
    // other number in this module meaningless.
  }

  test("maxDepth finds the boundary, and the boundary is real") {
    val limit = 1 << 20
    val found = StackProbe.maxDepth(limit)(deep)
    report("maxDepth(deep) on the test thread", found)

    assert(found > 1_000, s"a recursion should manage more than a thousand frames; got $found")
    assert(found < limit, "if the search saturates its limit it has not found a boundary")
    assert(survives(deep(found)), s"$found was reported as surviving and does not")
    assert(!survives(deep(found + 1)), s"${found + 1} was reported as failing and does not fail")
  }

  test("maxDepth reports -1 when nothing survives") {
    assertEquals(
      StackProbe.maxDepth(1000)(_ => throw StackOverflowError()),
      -1,
      "a function that cannot survive depth 0 has no maximum"
    )
  }

  test("onStack varies the stack size, and the depth follows it") {
    val small = StackProbe.onStack(256)(StackProbe.maxDepth(1 << 22)(deep))
    val large = StackProbe.onStack(4096)(StackProbe.maxDepth(1 << 22)(deep))
    report("maxDepth at 256 KiB", small)
    report("maxDepth at 4 MiB", large)

    // The assertion is on the *class*, never on the number: the guide's §23
    // measured a 40x range across stack sizes on this machine alone, so an
    // absolute threshold here would be a test of -Xss.
    assert(large > small * 8, s"16x the stack should buy far more depth: $small -> $large")

    val fitted = StackProbe.bytesPerFrame(256L * 1024, small, 4096L * 1024, large)
    val overhead = StackProbe.fixedOverhead(256L * 1024, small, 4096L * 1024, large)
    report("fitted bytes per frame", f"$fitted%.2f")
    report("fitted fixed overhead", f"$overhead%.0f")

    assert(fitted > 4.0 && fitted < 256.0, s"a frame of $fitted bytes is not a frame")
    assert(overhead >= 0.0, s"a negative fixed overhead means the two points are not on one line")
  }

  test("onStack propagates the body's result and its exceptions") {
    assertEquals(StackProbe.onStack(512)(6 * 7), 42, "the result must cross the thread boundary")
    intercept[IllegalArgumentException] {
      StackProbe.onStack(512)(throw IllegalArgumentException("from the other thread"))
    }
  }

end Exercise1StackProbeSpec
