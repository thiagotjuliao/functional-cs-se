package cs.se.block1.module1

/** Exercise 6 (Medium) — persistent collections and structural sharing.
  *
  * A first, gentle encounter with the structure Module 2 rebuilds by hand:
  * Scala's immutable `Map` is a Hash Array Mapped Trie. Updating it does not
  * copy the map — it rewrites only the path from the root to the changed leaf,
  * roughly `O(log32 n)` nodes, and shares every untouched subtree with the
  * previous version.
  *
  * Note the connection back to module guide §3: every one of those freshly
  * allocated path nodes points *down* into older, shared nodes. Young → old.
  * The write barrier never fires.
  */
object WordStats:

  /** Count occurrences of each word.
    *
    * Constraints:
    *   - a single fold over the input; no `var`, no `mutable.Map`, no
    *     `groupBy(...).view.mapValues(...).toMap` round-trip;
    *   - words are counted verbatim: no trimming, no case folding. If the
    *     caller wants normalisation, that is the caller's fold to write;
    *   - the empty input must yield the empty map.
    *
    * Think about which `Map` operation lets you express "increment or insert"
    * as one expression rather than a lookup followed by a branch.
    */
  def wordFrequencies(words: List[String]): Map[String, Int] =
    words.foldLeft(Map[String, Int]()) { (acc, s) =>
      acc.updatedWith(s) {
        case Some(n) => Some(n + 1)
        case None => Some(1)
      }
    }

  /** The `n` most frequent entries, most frequent first.
    *
    * The ordering must be **total and deterministic**, because a test that
    * depends on hash iteration order is a test that fails on someone else's
    * machine: sort by descending count, and break ties by ascending word in
    * natural `String` order.
    *
    * Contract:
    *   - `n <= 0` yields the empty list;
    *   - `n` larger than the map yields every entry, still ordered;
    *   - no mutation, no sorting in place.
    */
  def topN(frequencies: Map[String, Int], n: Int): List[(String, Int)] =
    if n <= 0 then List()
    else frequencies.toList.sortBy((s, n) => (-n, s)).take(n)
end WordStats

/** Exercise 8 (Hard) — asymptotic allocation, not asymptotic time.
  *
  * The lesson here is that Big-O applies to *bytes* as well as to steps, and
  * that the two can have different exponents for the same algorithm.
  *
  * Building a string by repeated concatenation is `O(n²)` in allocated bytes:
  * each `+` copies the entire accumulated prefix into a fresh `String`. For
  * 2,000 rows that is not a micro-inefficiency, it is a different complexity
  * class — and on G1 those growing intermediate strings eventually become
  * humongous allocations that bypass Eden entirely (module guide §6.1).
  *
  * The spec measures your implementation against a deliberately naive control
  * and requires you to beat it by a wide margin.
  */
object Csv:

  /** Render rows as CSV: cells joined by `,`, rows joined by `\n`, with no
    * trailing newline.
    *
    * Simplifying assumption, stated so that it is a decision rather than an
    * oversight: cells contain no commas, quotes or newlines, so no escaping is
    * performed. Real CSV escaping is RFC 4180 and belongs to a codec, not to an
    * allocation exercise.
    *
    * Contract:
    *   - the empty list renders as `""`;
    *   - a row with no cells renders as an empty line;
    *   - total allocation must be `O(output length)`, not `O(output length²)`;
    *   - no `var`, no explicit `StringBuilder` in your own code, and no loops.
    *
    * The standard library already gives you a combinator whose implementation
    * is a single growing buffer. Find it, and be able to explain why it is
    * linear while `foldLeft(""){ _ + _ }` is quadratic.
    */
  def renderCsv(rows: List[List[String]]): String =
    rows.map(_.mkString(",")).mkString("\n")
