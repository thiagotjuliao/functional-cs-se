package cs.se.block1.module1

/** Exercise 9 (Hard) — the timing harness: a measurement is only a measurement
  * if the optimiser could not delete the thing being measured.
  */
class Exercise9BenchSpec extends Module1Harness:

  private def work(n: Int): Unit =
    Bench.consume((1 to n).foldLeft(0.0)((acc, i) => acc + math.sqrt(i.toDouble)))

  test("consume is cheap and does not allocate") {
    warmup(10_000)(Bench.consume(math.Pi))
    val bytes = bytesOf(Bench.consume(math.Pi))
    report("consume (bytes)", bytes)
    assert(bytes < 512L, s"consume must not allocate; measured $bytes bytes")
  }

  test("medianNanos reports a positive, plausible duration") {
    val nanos = Bench.medianNanos(1_000, 1_000)(() => work(100))
    report("medianNanos of work(100)", nanos)
    assert(nanos > 0L, "a non-trivial body cannot take zero nanoseconds")
    assert(nanos < 1_000_000_000L, s"$nanos ns for 100 square roots is not plausible")
  }

  test("medianNanos separates a heavy body from a light one") {
    val light = Bench.medianNanos(2_000, 2_000)(() => work(10))
    val heavy = Bench.medianNanos(2_000, 2_000)(() => work(1_000))

    report("work(10) ns", light)
    report("work(1000) ns", heavy)

    // 100x the work. Demanding only 2x guards against a machine under load
    // while still failing loudly if dead code elimination deleted the body.
    assert(
      heavy > light * 2L,
      s"""100x the work measured as light=$light ns, heavy=$heavy ns.
         |Either the harness is not timing the body, or C2 eliminated the computation
         |because `consume` failed to make its result observable.""".stripMargin
    )
  }

end Exercise9BenchSpec
