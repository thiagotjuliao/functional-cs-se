package cs.se.annex.a1

import scala.reflect.ClassTag

/** One node of a Hash Array Mapped Trie: a 32-bit occupancy bitmap plus a
  * **dense** array holding only the children that actually exist.
  *
  * A naive node would carry a 32-slot array that is almost entirely empty. This
  * representation carries one word plus exactly `popcount(bitmap)` references,
  * and that compression is the reason a persistent `Map` is affordable at all.
  *
  * `IArray` is Scala 3's immutable array: the same JVM object as `Array`, with
  * no update operations in its API and no runtime cost. Every operation below
  * returns a **new** node sharing nothing mutable with its input.
  */
final case class SparseNode[A](bitmap: Int, children: IArray[A])

/** Exercise 8 (Hard) — The HAMT node primitive.
  *
  * This is the exercise the whole annex exists to make possible. Scala's
  * immutable `Vector`, `Map` and `Set` are tries of branching factor 32, and
  * every one of their operations passes through the three lines you are about to
  * write (guide, Part IV.20). Module B1-M2 builds the structure; this builds the
  * arithmetic underneath it.
  *
  * Invariant, to be maintained by every operation and asserted by the suite:
  *
  * {{{
  *   node.children.length == popcount(node.bitmap)
  * }}}
  */
object BitmapIndex:

  /** The 5-bit slot index selected by `hash` at trie depth `level`.
    *
    * A 32-way branch consumes `log2(32) = 5` hash bits per level, so level 0
    * reads bits 0–4, level 1 reads bits 5–9, and so on. A 32-bit hash therefore
    * supports levels 0–6, the last of which is partial.
    *
    * Must return a value in `[0, 31]` for every input, including negative
    * hashes — which is a question about which of `>>` and `>>>` you reach for.
    *
    * Precondition: `level` in `[0, 6]`.
    */
  def hashSlice(hash: Int, level: Int): Int = ???

  /** Is logical slot `slot` occupied in `bitmap`? Precondition: `slot` in `[0, 31]`. */
  def hasSlot(bitmap: Int, slot: Int): Boolean = ???

  /** The number of occupied slots — the required length of the dense array. */
  def arity(bitmap: Int): Int = ???

  /** The position of logical slot `slot` inside the dense children array.
    *
    * The identity at the heart of the HAMT: mask off every position **below**
    * `slot`, then count what remains. That count is exactly how many existing
    * children precede this one in the dense array.
    *
    * Defined whether or not the slot is occupied: when it is empty, the result
    * is the index at which a new child would be inserted. That dual reading is
    * what lets insertion and lookup share one computation.
    */
  def physicalIndex(bitmap: Int, slot: Int): Int = ???

  /** The child at logical slot `slot`, if the slot is occupied. */
  def get[A](node: SparseNode[A], slot: Int): Option[A] = ???

  /** A new node with `child` inserted at logical slot `slot`.
    *
    * Returns `None` if the slot is already occupied — replacement is a different
    * operation with different sharing behaviour, and conflating them is how
    * tries lose elements.
    *
    * The dense array must be rebuilt with `child` spliced in at
    * `physicalIndex`, preserving order. Build it from `IArray` combinators or
    * `IArray.tabulate`; no `var`, no in-place writes, no `System.arraycopy` into
    * a shared buffer.
    */
  def inserted[A: ClassTag](node: SparseNode[A], slot: Int, child: A): Option[SparseNode[A]] = ???

  /** A new node with logical slot `slot` vacated, or `None` if it was already
    * empty.
    *
    * Must restore the invariant: the bitmap loses one bit and the dense array
    * loses exactly the corresponding element.
    */
  def removed[A: ClassTag](node: SparseNode[A], slot: Int): Option[SparseNode[A]] = ???
end BitmapIndex

/** Exercise 9 (Hard) — Variable-length integer encoding.
  *
  * The bridge to Module B4-M12. This is not a toy format: zig-zag plus LEB128 is
  * exactly how Protocol Buffers encodes `sint32`, and the same shape appears in
  * DWARF, WebAssembly, MIDI and half the binary protocols in production.
  *
  * The motivation is that a fixed 4-byte integer wastes three bytes on every
  * small value, and small values dominate real payloads. LEB128 spends 7 bits of
  * payload per byte and uses the eighth as a continuation flag, so values below
  * 128 cost one byte.
  *
  * The complication is negative numbers: in two's complement, `-1` has all 32
  * bits set and would encode as the maximum five bytes. Zig-zag fixes this by
  * interleaving the signed values onto the non-negative ones —
  * `0, -1, 1, -2, 2, ...` maps to `0, 1, 2, 3, 4, ...` — so that small magnitudes
  * stay small regardless of sign.
  */
object VarIntCodec:

  /** Map a signed value onto a non-negative one, preserving magnitude ordering.
    *
    * The classical formulation is `(n << 1) ^ (n >> 31)`. Note the deliberate
    * asymmetry of the two shift operators, and be able to explain it: one
    * doubles the value, the other produces the sign mask of Exercise 2.
    *
    * Must be a bijection on the whole of `Int` — in particular
    * `zigZagEncode(Int.MinValue)` must round-trip.
    */
  def zigZagEncode(n: Int): Int = ???

  /** The inverse of `zigZagEncode`. Must satisfy
    * `zigZagDecode(zigZagEncode(n)) == n` for every `Int`.
    */
  def zigZagDecode(n: Int): Int = ???

  /** LEB128 bytes for the zig-zagged form of `n`.
    *
    * Each output byte carries seven payload bits in its low bits; the high bit
    * is `1` on every byte except the last. The encoding is little-endian in
    * groups: the least significant seven bits come first.
    *
    * Must emit between one and five bytes, and must never emit a trailing byte
    * whose payload is zero (a non-canonical encoding). Build it with `@tailrec`
    * accumulating a `List[Byte]`, then convert — no `var`, no
    * `ArrayBuffer`.
    */
  def encode(n: Int): IArray[Byte] = ???

  /** The number of bytes `encode(n)` will produce, computed **without**
    * encoding.
    *
    * Derivable from `log2Floor` of the zig-zagged value: seven payload bits per
    * byte. Useful in a real codec for sizing a buffer before writing into it,
    * and here it is a check that you understand the format rather than merely
    * having transcribed it.
    */
  def encodedSize(n: Int): Int = ???

  /** Decode one varint starting at `offset`.
    *
    * Returns the decoded value and the offset **immediately after** the varint,
    * so that a sequence of them can be decoded by folding. Returns `None` if the
    * input is truncated (the last available byte still has its continuation flag
    * set) or malformed (more than five bytes of payload).
    *
    * Remember `& 0xff` on every byte read: `Byte` is signed and widens with sign
    * extension (guide, Part III.16). Omitting it is the classic silent codec bug,
    * and this exercise's suite will find it.
    */
  def decodeAt(bytes: IArray[Byte], offset: Int): Option[(Int, Int)] = ???

  /** Decode a buffer holding exactly one varint.
    *
    * `None` if the buffer is malformed, truncated, or has trailing bytes — a
    * decoder that silently ignores trailing input is a decoder that will
    * silently accept a corrupted frame.
    */
  def decode(bytes: IArray[Byte]): Option[Int] = ???
end VarIntCodec
