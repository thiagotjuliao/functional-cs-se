package cs.se.block1.module1

/** Exercise 3 (Easy) — AllocationProbe: the instrument every later allocation
  * claim in this module is measured with.
  */
class Exercise3AllocationProbeSpec extends Module1Harness:

  test("the probe is monotonic and returns the body's value") {
    val before = AllocationProbe.allocatedBytes
    val after = AllocationProbe.allocatedBytes
    assert(after >= before, s"allocatedBytes went backwards: $before -> $after")

    val (value, bytes) = AllocationProbe.measure(Array.fill(10_000)(Vec2(1.0, 2.0)))
    assertEquals(value.length, 10_000)
    assert(bytes > 0L, "allocating 10,000 objects must be visible to the probe")
  }

  test("the probe evaluates the body exactly once") {
    // A by-name body evaluated twice — or zero times — is the classic bug.
    // 10,000 Vec2 instances plus the backing array is ~360 KB; a double
    // evaluation would show up as roughly twice that.
    val single = bytesOf(Array.fill(10_000)(Vec2(1.0, 2.0)))
    val double = bytesOf {
      val first = Array.fill(10_000)(Vec2(1.0, 2.0))
      val second = Array.fill(10_000)(Vec2(1.0, 2.0))
      first.length + second.length
    }
    assert(
      double > single * 3 / 2,
      s"expected the two-allocation body to cost clearly more: single=$single double=$double"
    )
  }

  test("the probe's own overhead is small") {
    warmup(1_000)(bytesOf(()))
    val overhead = bytesOf(())
    report("probe overhead (bytes)", overhead)
    assert(
      overhead >= 0L && overhead < 1_024L,
      s"instrument overhead of $overhead bytes is too large to measure anything with"
    )
  }

end Exercise3AllocationProbeSpec
