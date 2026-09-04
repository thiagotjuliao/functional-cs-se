package cs.se.block1.module1

import scala.util.Random

/** Exercise 5 (Hard) — Escape analysis: the same `Vec2` allocation is free when
  * it cannot escape and expensive when it can.
  */
class Exercise5EscapeSpec extends Module1Harness:

  private val EscapeSize = 200_000

  test("sumNorms is numerically correct") {
    val rng = Random(Seed)
    val xs = exactDoubles(rng, 1_000)
    val ys = exactDoubles(rng, 1_000)

    val expected = xs.indices.foldLeft(0.0) { (acc, i) =>
      acc + math.sqrt(xs(i) * xs(i) + ys(i) * ys(i))
    }
    assertEqualsDouble(Escape.sumNorms(xs, ys), expected, 1e-6)
    assertEqualsDouble(Escape.sumNorms(Array.empty, Array.empty), 0.0, Tolerance)
  }

  test("collectVecs materialises every vector") {
    val rng = Random(Seed)
    val xs = exactDoubles(rng, 100)
    val ys = exactDoubles(rng, 100)
    val out = Escape.collectVecs(xs, ys)

    assertEquals(out.length, 100)
    assertEquals(out(0), Vec2(xs(0), ys(0)))
    assertEquals(out(99), Vec2(xs(99), ys(99)))
  }

  test("sumNorms is scalar-replaced while collectVecs is not") {
    val rng = Random(Seed)
    val xs = exactDoubles(rng, EscapeSize)
    val ys = exactDoubles(rng, EscapeSize)

    warmup(10)(Escape.sumNorms(xs, ys))
    warmup(3)(Escape.collectVecs(xs, ys))

    val nonEscaping = bytesOf(Escape.sumNorms(xs, ys))
    val escaping = bytesOf(Escape.collectVecs(xs, ys))

    report(s"sumNorms over $EscapeSize elements (bytes)", nonEscaping)
    report(s"collectVecs over $EscapeSize elements (bytes)", escaping)
    report("ratio", if nonEscaping == 0L then "infinite" else escaping.toDouble / nonEscaping)

    assert(
      escaping > EscapeSize.toLong * 16L,
      s"the escaping variant must allocate at least one real object per element; measured $escaping bytes"
    )
    assert(
      nonEscaping < escaping / 4L,
      s"""sumNorms allocated $nonEscaping bytes against collectVecs' $escaping.
         |Either the Vec2 is escaping (check that nothing stores or returns it, and that the
         |method is small enough to inline), or escape analysis is disabled — which is exactly
         |what running with -XX:-DoEscapeAnalysis proves.""".stripMargin
    )
  }

end Exercise5EscapeSpec
