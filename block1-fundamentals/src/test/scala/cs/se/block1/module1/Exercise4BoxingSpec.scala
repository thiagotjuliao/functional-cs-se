package cs.se.block1.module1

import scala.util.Random

/** Exercise 4 (Medium) — Boxing: the allocation cost of `List[Int]` against
  * `Array[Int]`, measured rather than asserted from theory.
  */
class Exercise4BoxingSpec extends Module1Harness:

  test("both summations agree, on every input") {
    assertEquals(Boxing.sumBoxed(Nil), 0L)
    assertEquals(Boxing.sumPrimitive(Array.empty[Int]), 0L)

    val rng = Random(Seed)
    (1 to 50).foreach { _ =>
      val arr = Array.fill(rng.between(0, 500))(rng.between(-100_000, 100_000))
      assertEquals(Boxing.sumPrimitive(arr), Boxing.sumBoxed(arr.toList))
    }

    // Overflow safety: the accumulator must be a Long, not an Int.
    val large = Array.fill(1_000)(Int.MaxValue)
    assertEquals(Boxing.sumPrimitive(large), 1_000L * Int.MaxValue)
  }

  test("sumPrimitive allocates nothing in steady state") {
    val arr = Array.fill(100_000)(Random(Seed).between(1_000, 100_000))
    warmup(20)(Boxing.sumPrimitive(arr))

    val primitiveBytes = bytesOf(Boxing.sumPrimitive(arr))
    val list = arr.toList
    warmup(20)(Boxing.sumBoxed(list))
    val boxedBytes = bytesOf(Boxing.sumBoxed(list))

    report("sumPrimitive over 100k Ints (bytes)", primitiveBytes)
    report("sumBoxed over 100k Ints (bytes)", boxedBytes)

    assert(
      primitiveBytes < 2_048L,
      s"a tail-recursive walk over an int[] must allocate nothing; measured $primitiveBytes bytes. " +
        "A non-zero result means a generic combinator re-introduced boxing."
    )
  }

end Exercise4BoxingSpec
