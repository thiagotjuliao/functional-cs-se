package cs.se.annex.a1

/** Exercise 6 (Medium) — A 64-element set in one machine word.
  *
  * A `Long` read set-theoretically (guide, Part II) is the characteristic
  * function of a subset of `{0, ..., 63}`. Under that reading the bitwise
  * operators *are* the operations of a Boolean algebra, and this exercise makes
  * the algebra explicit and then proves its laws.
  *
  * `opaque type` is what makes this safe rather than merely fast: `BitSet64` is
  * a distinct type at compile time — you cannot pass a permission mask where a
  * colour was expected — while after erasure it is still a primitive `Long`.
  * No box, no header, no allocation (guide, Part VII).
  *
  * **The trap.** Inside this file the alias is *transparent*: `BitSet64` and
  * `Long` are the same type. An extension method named `|` whose body writes
  * `f | g` therefore resolves to itself and recurses forever. Ascribe the
  * operands (`(s: Long) | (t: Long)`) so that the primitive operator is
  * selected. Getting this wrong produces a `StackOverflowError`, not a type
  * error — which is precisely why you should meet it here rather than in
  * Block 2.
  */
opaque type BitSet64 = Long

object BitSet64:

  /** The empty set — the identity of `union` and the annihilator of `intersect`. */
  val Empty: BitSet64 = 0L

  /** The universe `{0, ..., 63}` — the identity of `intersect`.
    *
    * Its representation is `-1L`, all bits set. That a "full set" is a negative
    * number is a good reminder that the numeric reading of the word is, here,
    * meaningless.
    */
  val Full: BitSet64 = -1L

  /** Reinterpret a raw word as a set. The two are the same bits. */
  def fromRaw(raw: Long): BitSet64 = raw

  /** The set containing exactly the given members.
    *
    * Precondition: every member lies in `[0, 63]`. Build it as a fold over
    * `members`, starting from `Empty` — no loop, no mutable accumulator.
    */
  def of(members: Int*): BitSet64 = ???

  extension (s: BitSet64)

    /** The underlying word. The only sanctioned escape from the abstraction. */
    def raw: Long = s

    /** Membership. Precondition: `i` in `[0, 63]`. */
    def contains(i: Int): Boolean = ???

    /** `s` with `i` added. Idempotent. */
    def incl(i: Int): BitSet64 = ???

    /** `s` with `i` removed. Idempotent. */
    def excl(i: Int): BitSet64 = ???

    /** Union. Must be associative, commutative, idempotent, with identity `Empty`. */
    infix def union(t: BitSet64): BitSet64 = ???

    /** Intersection. Same laws, with identity `Full`. */
    infix def intersect(t: BitSet64): BitSet64 = ???

    /** Relative complement: members of `s` that are not in `t`. */
    infix def diff(t: BitSet64): BitSet64 = ???

    /** Symmetric difference. Note that `(BitSet64, symDiff, Empty)` is a *group*
      * — every element is its own inverse — which `union` is not. Being able to
      * say why is worth more than the one line of code.
      */
    infix def symDiff(t: BitSet64): BitSet64 = ???

    /** Complement with respect to `Full`. Must be an involution. */
    def complement: BitSet64 = ???

    /** Cardinality. One call to the 64-bit population count intrinsic — here it
      * is permitted, and idiomatic, because Exercise 4 has already made the
      * point.
      */
    def size: Int = ???

    /** Is `s` a subset of `t`? Express it with one intersection and one
      * comparison, not with iteration over members.
      */
    infix def subsetOf(t: BitSet64): Boolean = ???

    /** The members, in ascending order.
      *
      * The efficient formulation visits only the set bits — repeatedly take
      * `numberOfTrailingZeros` of the word and clear that bit, Kernighan-style
      * (Exercise 4). Must be `@tailrec` or a fold, and must be O(size), not
      * O(64).
      */
    def toList: List[Int] = ???
  end extension
end BitSet64

/** Exercise 7 (Medium) — Packing several fields into one word.
  *
  * Two independent payoffs, both of which the curriculum collects later:
  *
  *   - **Atomicity (B3-M9).** `AtomicLong.compareAndSet` updates one word. Pack
  *     two counters into that word and you have updated both atomically, with
  *     no wrapper object and therefore no allocation to defeat escape analysis
  *     (guide, Part IV.21).
  *   - **Wire formats (B4-M12).** Every binary protocol packs small fields into
  *     fixed-width words. An RGBA pixel is the simplest honest example.
  *
  * The recurring bug this exercise exists to inoculate against is sign
  * extension: widening a negative narrow value fills the high bits with ones and
  * corrupts every field above it (guide, Part III.16).
  */
object Packing:

  /** Pack two 32-bit fields into one 64-bit word, `hi` in the upper half.
    *
    * Must satisfy, for **every** pair including negatives:
    *   - `unpackHi(packInts(a, b)) == a`
    *   - `unpackLo(packInts(a, b)) == b`
    *
    * The mask that makes the negative case work is the entire exercise. Write
    * the version without it first, watch `packInts(0, -1)` destroy the high
    * half, and only then fix it.
    */
  def packInts(hi: Int, lo: Int): Long = ???

  /** The upper 32 bits, as a signed `Int`. */
  def unpackHi(packed: Long): Int = ???

  /** The lower 32 bits, as a signed `Int`. */
  def unpackLo(packed: Long): Int = ???

  /** Pack four channels into one `Int`, in the layout `0xAARRGGBB`.
    *
    * Precondition: every channel lies in `[0, 255]`. Returns `None` otherwise —
    * silently truncating an out-of-range channel is how one bad value becomes a
    * wrong colour in a different channel.
    */
  def packRgba(r: Int, g: Int, b: Int, a: Int): Option[Int] = ???

  /** The red channel of a packed pixel, in `[0, 255]`. */
  def red(pixel: Int): Int = ???

  /** The green channel of a packed pixel, in `[0, 255]`. */
  def green(pixel: Int): Int = ???

  /** The blue channel of a packed pixel, in `[0, 255]`. */
  def blue(pixel: Int): Int = ???

  /** The alpha channel of a packed pixel, in `[0, 255]`.
    *
    * This is the one that catches people: alpha occupies the top byte, so a
    * fully opaque pixel is a *negative* `Int`. An arithmetic shift here returns
    * a negative channel. Choose your shift operator deliberately.
    */
  def alpha(pixel: Int): Int = ???
end Packing
