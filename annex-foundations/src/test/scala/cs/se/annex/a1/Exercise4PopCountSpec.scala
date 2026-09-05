package cs.se.annex.a1

/** Exercise 4 (Medium) — PopCount: three algorithms measured against one
  * intrinsic.
  */
class Exercise4PopCountSpec extends AnnexA1Harness:

  private val implementations: List[(String, Int => Int)] =
    List(
      "naive" -> PopCount.naive,
      "kernighan" -> PopCount.kernighan,
      "swar" -> PopCount.swar
    )

  test("every implementation agrees with the JDK intrinsic") {
    val samples = domain(2000)
    implementations.foreach { (name, f) =>
      samples.foreach { x =>
        assertEquals(f(x), Integer.bitCount(x), s"$name(${bin(x)}) disagrees with Integer.bitCount")
      }
    }
  }

  test("the boundary values every bit-counting loop gets wrong") {
    implementations.foreach { (name, f) =>
      assertEquals(f(0), 0, s"$name(0)")
      assertEquals(f(-1), 32, s"$name(-1): all thirty-two bits are set")
      assertEquals(f(Int.MinValue), 1, s"$name(Int.MinValue): one bit, in the sign position")
      assertEquals(f(Int.MaxValue), 31, s"$name(Int.MaxValue)")
      assertEquals(f(1), 1, s"$name(1)")
      // A sign-propagating shift in a scanning loop never terminates here, or
      // reports 32. Both failures show up on this line.
      assertEquals(f(-2), 31, s"$name(-2)")
    }
  }

  test("counting is invariant under complement: popcount(x) + popcount(~x) == 32") {
    implementations.foreach { (name, f) =>
      domain(1000).foreach { x =>
        assertEquals(f(x) + f(~x), 32, s"$name failed the complement law at ${bin(x)}")
      }
    }
  }

  test("inclusion-exclusion: |a| + |b| == |a & b| + |a | b|") {
    val samples = randomInts(400)
    implementations.foreach { (name, f) =>
      samples.zip(samples.reverse).foreach { (a, b) =>
        assertEquals(
          f(a) + f(b),
          f(a & b) + f(a | b),
          s"$name violates inclusion-exclusion on ${bin(a)} / ${bin(b)}"
        )
      }
    }
  }

  test("symmetric difference: popcount(a ^ b) is the Hamming distance") {
    val samples = randomInts(400)
    implementations.foreach { (name, f) =>
      samples.zip(samples.reverse).foreach { (a, b) =>
        assertEquals(f(a ^ b), f(a | b) - f(a & b), s"$name breaks the Hamming identity")
      }
    }
  }

  test("results are bounded by the width of the word") {
    implementations.foreach { (name, f) =>
      domain(1000).foreach { x =>
        val c = f(x)
        assert(c >= 0 && c <= 32, s"$name(${bin(x)}) = $c is outside [0, 32]")
      }
    }
  }

  test("observation: the cost of each implementation against the intrinsic") {
    // Not an assertion. A timing test that fails because a laptop throttled is
    // a coin toss, not a test. Record these numbers in docs/checklist.md §E.
    //
    // Read the output sceptically, and read it as a lesson rather than a
    // ranking. Every candidate is invoked through the same `Int => Int`
    // reference, which makes this one megamorphic call site: the JIT cannot
    // inline any of them, and the intrinsic loses the very advantage the
    // exercise is trying to display. Watch for `intrinsic` failing to win, then
    // explain *why* in the checklist — that explanation is worth more than the
    // number, and it is exactly the class of measurement error JMH exists to
    // eliminate.
    val samples = randomInts(1000).toArray
    val sparse = Array.fill(1000)(1 << 7)
    val dense = Array.fill(1000)(~(1 << 7))

    val candidates: List[(String, Array[Int], Int => Int)] =
      List(
        ("naive", samples, PopCount.naive),
        ("kernighan", samples, PopCount.kernighan),
        ("kernighan/sparse", sparse, PopCount.kernighan),
        ("kernighan/dense", dense, PopCount.kernighan),
        ("swar", samples, PopCount.swar),
        ("swar/sparse", sparse, PopCount.swar),
        ("intrinsic", samples, Integer.bitCount)
      )

    candidates.foreach { (label, data, f) =>
      warmup(50_000)(f(data(0)))
      val (median, blackhole) = medianNanos(2_000) {
        data.foldLeft(0)((acc, x) => acc + f(x))
      }
      report(s"$label — median ns per 1000 calls", s"$median (blackhole $blackhole)")
    }
  }

end Exercise4PopCountSpec
