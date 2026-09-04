package cs.se.block1.module1

/** Exercise 1 (Easy) — Scala 3 syntax alignment: `extension` blocks.
  *
  * A two-dimensional vector. `final` is deliberate: a class that cannot be
  * subclassed keeps its call sites monomorphic, which is a precondition for the
  * inlining that escape analysis depends on (module guide §4.2).
  *
  * Heap footprint of one instance: `align8(12 + 8 + 8)` = 32 bytes. You will
  * re-derive that number mechanically in Exercise 7.
  */
final case class Vec2(x: Double, y: Double)

object Vec2:

  /** The zero vector — the identity element of `+`.
    *
    * Note that this is a single shared instance, not a factory. Returning the
    * same immutable object to every caller is safe precisely because nobody can
    * mutate it.
    */
  val Zero: Vec2 = Vec2(0.0, 0.0)

  extension (v: Vec2)

    /** Component-wise addition.
      *
      * Must satisfy, for all vectors `a`, `b`, `c`:
      *   - associativity: `(a + b) + c == a + (b + c)`
      *   - commutativity: `a + b == b + a`
      *   - identity: `a + Vec2.Zero == a`
      *
      * Those three laws make `(Vec2, +, Zero)` a commutative monoid. You are
      * building your first algebraic structure without noticing.
      */
    def +(w: Vec2): Vec2 =
      Vec2(v.x + w.x, v.y + w.y)

    /** Scalar multiplication. `v * 1.0` must return a value equal to `v`. */
    def *(k: Double): Vec2 =
      Vec2(v.x * k, v.y * k)

    /** Euclidean inner product. Must be commutative: `a.dot(b) == b.dot(a)`. */
    def dot(w: Vec2): Double =
      (v.x * w.x) + (v.y * w.y)

    /** Euclidean length.
      *
      * Must equal `math.sqrt(v.dot(v))`, and must be non-negative for every
      * input, including negative components.
      */
    def norm: Double =
      Math.sqrt(v.dot(v))
  end extension
end Vec2

/** Exercise 2 (Easy) — Scala 3 syntax alignment: `enum` as an ADT.
  *
  * A closed sum type over three shapes. Because the type is closed, the
  * compiler can prove a `match` on it exhaustive — the whole point of an ADT.
  * If you find yourself writing a `case _ =>` fallback, you have thrown that
  * guarantee away and the compiler will stop protecting you when a fourth shape
  * is added.
  */
enum Shape:
  case Circle(radius: Double)
  case Rectangle(width: Double, height: Double)
  case Triangle(base: Double, height: Double)

object Shape:

  extension (s: Shape)

    /** Surface area of the shape.
      *
      * Implement with a single exhaustive pattern match. No `if`, no
      * `isInstanceOf`, no default case.
      */
    def area: Double =
      s match
        case Circle(r) => Math.PI * r * r
        case Rectangle(w, h) => w * h
        case Triangle(b, h) => 0.5 * b * h

  /** Total area of a collection of shapes.
    *
    * Must be implemented as a fold. No loops, no mutable accumulator. The
    * empty list must yield `0.0` — the identity of the `(Double, +)` monoid,
    * which is the correct answer rather than an edge case to special-case.
    */
  def totalArea(shapes: List[Shape]): Double =
    shapes.foldLeft(0.0)((acc, s) => acc + s.area)
