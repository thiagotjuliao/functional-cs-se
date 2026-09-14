package cs.se.block1.module2

import cs.se.block1.module2.MyList.*

/** Exercise 5 — two ways to build the same list, and the doubling test
  * that tells them apart.
  *
  * Both functions below must return exactly the same list. They are pure, they
  * compile identically, and they differ by one operator. At `n = 16,000` the
  * measured gap in allocated bytes is about 4,800×, and one of them allocates
  * six gigabytes.
  *
  * The point of the exercise is not to learn that `:+` is slow. It is to learn
  * the **doubling test**: double `n`, look at the ratio of the costs, and read
  * the complexity class off the ratio.
  *
  * {{{
  * cost unchanged   ->  O(1)
  * cost doubles     ->  O(n)
  * cost quadruples  ->  O(n²)
  * }}}
  *
  * That technique outlives this module. It is how you establish a complexity
  * class for code you did not write and cannot read.
  */
object Building:

  /** `MyList(0, 1, ..., n - 1)`, built by appending each element at the back.
    *
    * Deliberately the naive version. Implement it as the obvious fold over
    * `0 until n` using `appended`, and do not optimise it — the quadratic is the
    * experimental control, and a clever implementation destroys the experiment.
    *
    * `n <= 0` yields the empty list.
    */
  def byAppend(n: Int): MyList[Int] =
    if n < 0 then Nil
    else
      (0 until n).foldLeft(Nil: MyList[Int]):
        case (ls, a) => ls.appended(a)

  /** The same list, built by prepending and reversing once at the end.
    *
    * Every element goes on the front, which is `O(1)` each, and the single
    * `reverse` at the end is `O(n)` once. Total `O(n)`.
    *
    * This is the idiom the whole Scala ecosystem uses to build a `List`, and
    * now you know why it is written that way rather than being written the way
    * it reads.
    */
  def byPrepend(n: Int): MyList[Int] =
    if n < 0 then Nil
    else
      (0 until n)
        .foldLeft(Nil: MyList[Int]):
          case (ls, a) => ls.prepended(a)
        .reverse

  /** The ratio of the two, as the doubling test consumes it.
    *
    * Given the cost at `n` and the cost at `2n`, this is `costAt2n / costAtN`.
    * Trivial arithmetic, and it exists so that the spec can assert on the ratio
    * rather than on absolute byte counts — an absolute threshold would encode
    * this machine's constants into a test that has to pass on any machine.
    *
    * Returns `0.0` when `costAtN` is zero.
    */
  def doublingRatio(costAtN: Long, costAt2n: Long): Double =
    if costAtN == 0 then 0.0
    else costAt2n / costAtN.toDouble

end Building
