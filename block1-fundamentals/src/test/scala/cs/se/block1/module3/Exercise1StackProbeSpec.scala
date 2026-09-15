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
    // Warm up first. A compiled frame is smaller than an interpreted one, so a
    // cold search reports a boundary that stops being true while it runs -
    // measured here at 32,768 cold against 61,653 warm, a factor of 2.51 - and
    // the cold number fails the very next call. Guide §23.
    (0 until 3).foreach(_ => StackProbe.maxDepth(limit)(deep))
    val found = StackProbe.maxDepth(limit)(deep)
    report("maxDepth(deep) on the test thread, warm", found)

    assert(found > 1_000, s"a recursion should manage more than a thousand frames; got $found")
    assert(found < limit, "if the search saturates its limit it has not found a boundary")

    // The boundary is real, asserted with a margin rather than to the frame.
    // Even warm it drifts by a few frames between calls, and `found + 1` is a
    // margin of one - which is this module's own §23 mistake in miniature.
    assert(
      survives(deep(found * 9 / 10)),
      s"a depth 10% below the reported boundary of $found must survive"
    )
    assert(
      !survives(deep(found * 11 / 10)),
      s"a depth 10% above the reported boundary of $found must not survive"
    )
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

  test("onStack propagates whatever the body throws, fatal throwables included") {
    // "Whatever" is the whole contract, and the word does real work here. The
    // obvious way to carry a result across a thread boundary is a Try, and
    // `scala.util.Try` catches only what `NonFatal` admits - which excludes
    // every `VirtualMachineError`, and `StackOverflowError` is one.
    //
    // An implementation built on Try passes the two ordinary cases below and
    // loses the third: the error escapes the Try, kills the thread, leaves the
    // result box empty, and the caller is handed whatever an empty box
    // produces. Measured on exactly that implementation, the caller saw a
    // `MatchError` - a failure that names nothing about a stack.
    //
    // This is the one assertion in the module that separates catching
    // `Throwable` from catching anything narrower.

    val ordinary = intercept[IllegalArgumentException] {
      StackProbe.onStack(512)(throw IllegalArgumentException("from the other thread"))
    }
    assertEquals(
      ordinary.getMessage,
      "from the other thread",
      "the throwable that arrives must be the one that was thrown, not a wrapper around it"
    )

    val checked = intercept[UnsupportedOperationException] {
      StackProbe.onStack(512)(throw UnsupportedOperationException("also ordinary"))
    }
    assertEquals(checked.getMessage, "also ordinary")

    // The body overflows with nothing catching it on the way out, which is what
    // `survives` normally does and is deliberately absent here.
    //
    // Written with a plain catch rather than `intercept`, because MUnit's
    // `intercept` cannot express this assertion: it is built on `NonFatal` and
    // a `StackOverflowError` escapes it untouched. Verified - `intercept` let
    // it through while catching an `IllegalStateException` normally. The tool
    // for testing that a Throwable is caught is defeated by the same
    // fatal/non-fatal split the test is about, which is worth knowing before
    // you reach for it anywhere else in this module.
    val overflowed =
      try
        val _ = StackProbe.onStack(256)(deep(Int.MaxValue))
        "the body returned, so nothing overflowed"
      catch
        case _: StackOverflowError => "propagated"
        case t: Throwable => s"propagated the wrong thing: ${t.getClass.getName}"
    assertEquals(
      overflowed,
      "propagated",
      "a StackOverflowError raised by the body must reach the caller as itself. An " +
        "implementation whose result box stays empty hands back whatever an empty box " +
        "produces - measured on a Try-based version, a MatchError naming nothing about a stack"
    )
  }

end Exercise1StackProbeSpec
