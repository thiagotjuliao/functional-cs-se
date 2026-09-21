package cs.se.block1.project1

import Expr.*

/** Stage 3 — traversing a tree without the stack. Guide, Part IV. */
class Stage3TraversalSpec extends Project1Harness:

  private val small = Bin(Op.Add, Lit(1.0), Bin(Op.Mul, Lit(2.0), Lit(3.0)))

  test("size counts every node") {
    assertEquals(Shape.size(Lit(1.0)), 1)
    assertEquals(Shape.size(Var("x")), 1)
    assertEquals(Shape.size(Neg(Lit(1.0))), 2)
    assertEquals(Shape.size(small), 5, "the guide's trace, §21")
    assertEquals(Shape.size(ast("(3 * 1 + 0 - 0) * (2 ^ 1) + (4 - 2) * 1")), 17)
  }

  test("size agrees with the naive control wherever the control survives") {
    // The control is the reason `size` is demonstrably correct rather than
    // merely plausible. It is kept for exactly this assertion.
    val shapes = List(
      Lit(1.0),
      Var("x"),
      Neg(Neg(Var("y"))),
      small,
      ast("-2 ^ 2 + (x - 1) / 3"),
      leftChain(1000),
      rightChain(1000)
    )
    shapes.foreach(e => assertEquals(Shape.size(e), Shape.sizeNaive(e), e.toString.take(60)))
  }

  test("depth is the longest root-to-leaf path") {
    assertEquals(Shape.depth(Lit(1.0)), 1, "a leaf alone has depth 1")
    assertEquals(Shape.depth(Var("x")), 1)
    assertEquals(Shape.depth(Neg(Lit(1.0))), 2)
    assertEquals(Shape.depth(small), 3)
    assertEquals(Shape.depth(leftChain(10)), 11)
    assertEquals(Shape.depth(rightChain(10)), 11, "depth is blind to which side is deep")
    // size is not: the two chains have the same depth and the same size here,
    // but a tree can be deep and small or shallow and large, and only one of
    // the two numbers bounds the naive traversals.
    assertEquals(Shape.size(leftChain(10)), 21)
  }

  test("the naive control has a ceiling, and it is the point of this stage") {
    val ceiling = maxSurviving(1 << 20)(n => Shape.sizeNaive(leftChain(n)))
    report("sizeNaive, deepest tree it survives", ceiling)
    assert(ceiling > 1000, s"suspiciously shallow at $ceiling — is the builder recursing?")
    assert(
      ceiling < 500_000,
      s"sizeNaive survived $ceiling; if it has no ceiling it is not the naive version"
    )
  }

  test("size and depth have no ceiling at a million") {
    // 1,000,000 additions: 2,000,001 nodes, depth 1,000,001. Guide §21 and §22.
    val big = leftChain(1_000_000)
    assertEquals(Shape.size(big), 2_000_001)
    assertEquals(Shape.depth(big), 1_000_001)
    assert(survives(Shape.size(rightChain(1_000_000))), "and the mirror shape too")
    assert(survives(Shape.depth(rightChain(1_000_000))))
  }

  test("render emits parentheses only where precedence requires them") {
    assertEquals(Shape.render(ast("1 + 2 * 3")), "1 + 2 * 3")
    assertEquals(Shape.render(ast("(1 + 2) * 3")), "(1 + 2) * 3")
    assertEquals(Shape.render(ast("2 ^ 3 ^ 2")), "2 ^ 3 ^ 2", "right associative: no brackets")
    assertEquals(Shape.render(ast("(2 ^ 3) ^ 2")), "(2 ^ 3) ^ 2", "left grouping: brackets")
    assertEquals(Shape.render(ast("10 - 3 - 2")), "10 - 3 - 2")
    assertEquals(Shape.render(ast("10 - (3 - 2)")), "10 - (3 - 2)")
    assertEquals(
      Shape.render(ast("-(2 ^ 2)")),
      "-2 ^ 2",
      "the brackets were redundant: §15 already makes these the same tree"
    )
  }

  test("render's law is about trees, not strings") {
    // parse(render(e)) == e. `render` is NOT required to be the identity on
    // strings — the row above proves it is not. Guide §40.
    val sources = List(
      "1 + 2 * 3",
      "(1 + 2) * 3",
      "2 ^ 3 ^ 2",
      "(2 ^ 3) ^ 2",
      "10 - 3 - 2",
      "10 - (3 - 2)",
      "-2 ^ 2",
      "-(2 ^ 2)",
      "1 / (2 / 3)",
      "1 - (2 + 3)",
      "-x * y",
      "-(x * y)",
      "x ^ -y",
      "(x + y) ^ 2"
    )
    sources.foreach { s =>
      val tree = ast(s)
      val text = Shape.render(tree)
      assertEquals(ast(text), tree, s"[$s] rendered as [$text] and did not round-trip")
    }
  }

  test("render preserves the sign of a zero") {
    // v.toLong erases it, and the round trip then loses a value the simplifier
    // itself creates from Neg(Lit(0.0)). Guide §36 and §40.
    val text = Shape.render(Lit(-0.0))
    assert(text.startsWith("-"), s"Lit(-0.0) rendered as [$text]")
    // The round trip closes MODULO simplification here, and not exactly: the
    // parser cannot produce a negative literal, only Neg(Lit(0.0)). Stage 5
    // asserts the simplified form; all this stage can ask is that the sign
    // survived the text.
    assertEquals(ast(text), Neg(Lit(0.0)))
  }

  test("render is allowed to keep its ceiling, and the number is recorded") {
    // A DOCUMENTED limit. A 12,000-deep expression has no readable string form,
    // so this recursion is not worth removing — but an undocumented limit and a
    // deliberate one are the same thing until the number is written down.
    val ceiling = maxSurviving(1 << 18)(n => Shape.render(leftChain(n)))
    report("render, deepest tree it survives", ceiling)
    assert(ceiling > 1000, s"render died at $ceiling, which is too shallow to be useful")
  }

end Stage3TraversalSpec
