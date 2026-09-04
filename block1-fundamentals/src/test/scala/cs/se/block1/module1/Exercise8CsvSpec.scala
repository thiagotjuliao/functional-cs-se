package cs.se.block1.module1

import scala.util.Random

/** Exercise 8 (Hard) — allocation complexity: an O(n) renderer proved against
  * an O(n^2) control, by measurement rather than by inspection.
  */
class Exercise8CsvSpec extends Module1Harness:

  /** The control: string building by repeated concatenation. Every `+` copies
    * the whole accumulated prefix, making total allocation quadratic in the
    * output length. Do not imitate this in `Csv`.
    */
  private def naiveRender(rows: List[List[String]]): String =
    rows.foldLeft("") { (acc, row) =>
      val line = row.foldLeft("") { (cells, cell) =>
        if cells.isEmpty then cell else cells + "," + cell
      }
      if acc.isEmpty then line else acc + "\n" + line
    }

  test("renderCsv produces the expected text") {
    assertEquals(Csv.renderCsv(Nil), "")
    assertEquals(Csv.renderCsv(List(Nil)), "")
    assertEquals(Csv.renderCsv(List(List("a"))), "a")
    assertEquals(Csv.renderCsv(List(List("a", "b"), List("c", "d"))), "a,b\nc,d")
    assertEquals(Csv.renderCsv(List(List("a"), Nil, List("b"))), "a\n\nb")

    val rng = Random(Seed)
    val rows = List.fill(200)(List.fill(5)(rng.alphanumeric.take(6).mkString))
    assertEquals(Csv.renderCsv(rows), naiveRender(rows), "must agree with the naive control")
  }

  test("renderCsv allocates linearly, not quadratically") {
    val rng = Random(Seed)
    val rows = List.fill(2_000)(List.fill(8)(rng.alphanumeric.take(6).mkString))

    warmup(3)(Csv.renderCsv(rows))
    val linear = bytesOf(Csv.renderCsv(rows))
    val quadratic = bytesOf(naiveRender(rows))
    val outputLength = Csv.renderCsv(rows).length.toLong

    report("output length (chars)", outputLength)
    report("renderCsv (bytes)", linear)
    report("naive concatenation (bytes)", quadratic)

    assert(
      linear < quadratic / 10L,
      s"""renderCsv allocated $linear bytes against the naive control's $quadratic.
         |An O(n) renderer over a ~$outputLength character output should be several orders of
         |magnitude cheaper than O(n^2) concatenation.""".stripMargin
    )
  }

end Exercise8CsvSpec
