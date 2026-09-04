package cs.se.block1.module1

import scala.util.Random

/** Exercise 6 (Medium) — WordStats: persistent map folding and a total,
  * deterministic ordering that must not leak hash order.
  */
class Exercise6WordStatsSpec extends Module1Harness:

  test("wordFrequencies counts verbatim") {
    assertEquals(WordStats.wordFrequencies(Nil), Map.empty[String, Int])
    assertEquals(
      WordStats.wordFrequencies(List("a", "b", "a", "c", "a", "b")),
      Map("a" -> 3, "b" -> 2, "c" -> 1)
    )
    // No case folding, no trimming: normalisation is the caller's business.
    assertEquals(
      WordStats.wordFrequencies(List("Scala", "scala", " scala")),
      Map("Scala" -> 1, "scala" -> 1, " scala" -> 1)
    )
  }

  test("the total count is preserved by the fold") {
    val rng = Random(Seed)
    val words = List.fill(5_000)(s"w${rng.between(0, 200)}")
    assertEquals(WordStats.wordFrequencies(words).values.sum, words.length)
  }

  test("topN ordering is total and deterministic") {
    val freqs = Map("b" -> 5, "a" -> 5, "c" -> 9, "d" -> 1)

    // Descending count, ties broken by ascending word.
    assertEquals(
      WordStats.topN(freqs, 3),
      List("c" -> 9, "a" -> 5, "b" -> 5)
    )
    assertEquals(WordStats.topN(freqs, 0), Nil)
    assertEquals(WordStats.topN(freqs, -1), Nil)
    assertEquals(WordStats.topN(freqs, 100).length, 4)
    assertEquals(WordStats.topN(Map.empty[String, Int], 5), Nil)

    // Same input, repeated calls: identical output. Hash order must not leak.
    assertEquals(WordStats.topN(freqs, 4), WordStats.topN(freqs, 4))
  }

end Exercise6WordStatsSpec
