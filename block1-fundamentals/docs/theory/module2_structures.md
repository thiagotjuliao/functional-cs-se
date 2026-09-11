# Module 2 — Manual Persistent Data Structures

> **Block 1 · Week 2 · Abstraction ⭐️⭐️ · Machine ⭐️⭐️⭐️**
> Milestone tag on completion: `b1-m2-persistent-structures`

---

## How To Read This Guide

This guide assumes **no prior exposure to persistent data structures** and no
formal background in asymptotic analysis. It builds from what a single cons cell
looks like in memory up to why one insert into a 24 MB tree costs 504 bytes.

It is organised as a staircase. Each Part depends on the one before it, and each
unlocks specific exercises:

| Part | Content | Unlocks |
| :--- | :--- | :--- |
| **I** | A list as cells and arrows: the shape, and what it costs | E1, E2 |
| **II** | Asymptotics, just enough — and how to *measure* a complexity class | E1, E5 |
| **III** | Structural sharing: the update that copies only what it changes | E1, E3, E4 |
| **IV** | Trees, where sharing stops being a curiosity | E6, E7, E9 |
| **V** | Five traps, each of which compiles and passes a naive test | E4, E5, E8 |
| **VI** | Where this lives in real systems | motivation for all |
| **VII** | Scala 3: the `enum`, the variance, and `Nothing` | E2, E6 |

**Every numeric result in this guide was executed and verified before it was
written**, on JDK 21 HotSpot with compressed oops, using the `AllocationProbe`
instrument built in Module 1. Where a number looks wrong to you, measure it — the
surprise is the lesson.

A note on notation: pictures use lists of three or four elements, because four
cells fit on a line and a million do not. Every rule stated for four holds for a
million unchanged, and the measurements are taken at the large sizes.

**One boundary, stated up front.** This guide teaches the asymptotic reasoning
the module needs: orders of growth, counting allocations, and the doubling test.
It does **not** teach the formal Θ and Ω definitions, nor amortised analysis.
Those live in Annex A2, and they become genuinely necessary only when you build a
persistent *queue*, where amortisation and persistence interact badly. Section 8
marks the boundary precisely.

---

# Part I — A List Is Cells And Arrows

## 1. The Question This Module Exists To Answer

Module 1 established that a `List[Int]` of one million elements costs 40 MB where
an `Array[Int]` costs 4 MB, and that the difference is header, pointer and
padding repeated a million times. A reasonable conclusion from that module alone
is: *immutable linked structures are a tax you pay for purity.*

That conclusion is half right, and the missing half is this module.

An array of a million elements, updated at one position, costs **4 MB** to
produce a new version — you copy the whole thing. A tree of a million elements,
updated at one position, costs **504 bytes**, measured, and the old version stays
valid and untouched.

The linked structure is more expensive to *hold* and dramatically cheaper to
*change*. This module is about the second half of that trade, and about how to
predict which side of it you are on before you write the code.

## 2. The Shape

A cons list is one of exactly two things: empty, or a cell holding a value and a
reference to another list. That is the entire definition, and Scala 3 states it
in three lines:

```scala
enum MyList[+A]:
  case Nil
  case Cons(head: A, tail: MyList[A])
```

`List(1, 2, 3)` is therefore not a block of memory holding three numbers. It is
three separate objects, each pointing at the next:

```text
   ┌───────┐      ┌───────┐      ┌───────┐
   │ 1 │ ●─┼─────▶│ 2 │ ●─┼─────▶│ 3 │ ●─┼─────▶ Nil
   └───────┘      └───────┘      └───────┘
     head tail      head tail      head tail
```

Two facts follow immediately from the picture, and both matter more than they
look:

* **The arrows point one way.** From a cell you can reach everything after it and
  nothing before it. There is no way back.
* **`Nil` is a single shared object.** Every empty list in the program is the
  same instance. It has no fields to differ in, so there is nothing to copy.

## 3. What One Cell Costs

You already have the arithmetic for this — it is `Footprint.shallowSize` from
Module 1, Exercise 7. A cons cell has two reference fields, `head` and `tail`:

```text
   shallowSize(references = 2, ints = 0, longs = 0, doubles = 0, booleans = 0)
      = align8(12 + 4 + 4)
      = align8(20)
      = 24 bytes
```

Measured, on the same JVM: **24 bytes**. And here is the fact that makes Part IV
work — a binary tree node holds a value and *two* child references, three
references in all:

```text
   shallowSize(3, 0, 0, 0, 0) = align8(12 + 4 + 4 + 4) = align8(24) = 24 bytes
```

**Also 24.** A tree node costs exactly what a list cell costs, because the third
reference lands inside padding the cell was already paying for. Twelve bytes of
header, twelve bytes of fields, no waste. Keep that number; the whole module is
counted in units of it.

*Exercise 1 builds the arithmetic that turns this one number into predictions for
every operation in the module.*

## 4. The Two Ends Are Not The Same

Because the arrows point one way, the two ends of a list are not interchangeable.

**Adding to the front** means making one new cell whose tail is the list you
already have:

```text
   before:              ┌───┐    ┌───┐    ┌───┐
                        │ 1 ●───▶│ 2 ●───▶│ 3 ●───▶ Nil
                        └───┘    └───┘    └───┘

   0 :: before:   ┌───┐   ▲
                  │ 0 ●───┘        one new cell, pointing at what existed
                  └───┘
```

The old list is not touched, not copied, not even read. It does not know this
happened. One cell, 24 bytes, **and the number does not depend on how long the
list was**.

**Adding to the back** cannot work that way. The last cell's `tail` is `Nil`, and
to make it point at something else you would have to *change* that cell — which
immutability forbids. So you build a new last cell, which forces a new
second-to-last cell to point at it, which forces a new third-to-last, all the way
to the front:

```text
   before :+ 4:   ┌───┐    ┌───┐    ┌───┐    ┌───┐
                  │ 1 ●───▶│ 2 ●───▶│ 3 ●───▶│ 4 ●───▶ Nil
                  └───┘    └───┘    └───┘    └───┘
                    ▲        ▲        ▲        ▲
                    └────────┴────────┴────────┴──  every cell is new
```

Every cell is rebuilt. The *values* are shared — `1`, `2` and `3` are the same
objects — but all `n` cells are freshly allocated.

Measured over a 100,000-element list:

```text
   x :: xs        ->          24 bytes
   xs :+ x        ->   2,400,104 bytes        = 100,000 x 24, plus overhead
   ratio          ->     100,004 x
```

One hundred thousand times the cost, for what reads like the same operation with
the arguments in a different order. This asymmetry is not a wart to be worked
around; it is the shape of the data structure showing through the API.

---

# Part II — Asymptotics, Just Enough

## 5. Counting Operations, Not Seconds

Seconds are a property of a machine, a JIT state, a thermal envelope and a
scheduler. They are not a property of an algorithm. What is a property of an
algorithm is **how the work grows when the input grows**.

So we count steps — or, in this module, allocations — as a function of `n`, and
we throw away everything that does not affect growth:

```text
   3n + 7        grows like n
   n/2           grows like n
   5n² + 1000n   grows like n²          for large n, the n² term drowns the rest
```

Constants and lower-order terms are discarded because they do not change what
happens when `n` doubles. That is the whole abstraction, and it is worth being
precise about what it costs you: an algorithm that is `O(n)` with a constant of
10,000 loses to one that is `O(n²)` with a constant of 1 until `n` reaches
10,000. Asymptotics tells you who wins *eventually*, not who wins on your input.

## 6. The Notation, Informally But Correctly

`f(n) = O(g(n))` means: beyond some input size, `f` grows no faster than `g`, up
to a constant factor. It is an **upper bound**.

The three you will meet in this module:

```text
   O(1)        the cost does not depend on n at all
   O(n)        double the input, double the cost
   O(log n)    double the input, the cost goes up by one step
```

`O(log n)` is the one worth staring at, because it is the reason trees exist.
Logarithms grow so slowly that the numbers feel wrong:

```text
   n              log2(n)     meaning
   -----------    -------     ---------------------------------------
   1,000              10      ten steps to find one item in a thousand
   1,000,000          20      twenty, in a million
   1,000,000,000      30      thirty, in a billion
```

A thousandfold increase in data buys ten extra steps. That is not a small
improvement over `O(n)`; it is a different universe.

## 7. The Doubling Test — How To *Measure* A Complexity Class

Here is the part that converts asymptotics from a thing you assert into a thing
you observe, and it is the single most useful technique in this Part.

**Double the input and look at the ratio of the costs.**

```text
   if the cost stays the same      ->  O(1)
   if the cost doubles             ->  O(n)
   if the cost quadruples          ->  O(n²)
   if the cost goes up by a step   ->  O(log n)
```

Two ways of building the same list, measured. Both produce `List(0, 1, ..., n-1)`;
both are pure; both compile identically. Building by repeated `append`:

```scala
(0 until n).foldLeft(List.empty[Int])((acc, i) => acc.appended(i))
```

versus building by `prepend` and reversing once at the end:

```scala
(0 until n).foldLeft(List.empty[Int])((acc, i) => i :: acc).reverse
```

Bytes allocated, measured:

```text
   n        append-built     x prev     prepend+reverse    x prev     ratio
   ------   --------------   ------     ---------------    ------     -------
    2,000       48,557,792      -                168,016      -         289.0
    4,000      192,484,672    3.964               316,024    1.881       609.1
    8,000     768,731,896     3.994               635,944    2.012     1,208.8
   16,000   3,073,467,896     3.998             1,275,944    2.006     2,408.8
```

Read the two `x prev` columns and you have *proved* the complexity classes
without writing a proof:

* the append column multiplies by **4** every time `n` doubles — that is `O(n²)`;
* the prepend column multiplies by **2** — that is `O(n)`;
* and the ratio between them doubles each row, which is what `n²/n = n` looks
  like from the outside.

Note the absolute number in the last row: **three gigabytes allocated to build a
sixteen-thousand-element list.** Nothing in that code looks expensive. There is
no nested loop to spot in review.

*Exercise 5 makes you reproduce this table from your own structure.*

## 8. Where The Formal Treatment Lives

What you have just read is enough to reason about every structure in this module,
and it is deliberately incomplete in three ways:

* `O` is only an upper bound. **Θ** (tight bound) and **Ω** (lower bound) are the
  other two, and the difference matters the moment someone claims an algorithm is
  optimal.
* Costs here are *worst case per operation*. **Amortised** analysis reasons about
  a sequence of operations, where an occasional expensive step is paid for by
  many cheap ones — the technique behind dynamic arrays and behind the two-stack
  queue.
* Amortisation and **persistence interact badly**: an amortised bound assumes you
  only use the latest version of a structure, and persistence lets you use an old
  one repeatedly, re-triggering the expensive step every time. Resolving that
  needs lazy evaluation, and it is the central result of Okasaki's book.

All three belong to **Annex A2**, which is on the backlog. You do not need them
for this module, and you will need them badly when you build a queue.

---

# Part III — Structural Sharing

## 9. The Idea

Here is the obvious first attempt at immutable update, and it is worth writing
down because it is what most people assume immutability means:

> To change one element without mutating, copy the structure, change the copy.

That is `O(n)` per update, in time and in bytes. If it were the only option,
functional data structures really would be the tax they are accused of being.

They are not, and the reason is one observation:

> **The parts you did not change are still valid. Point at them.**

A mutable structure cannot do this, because anyone holding a reference to a
"shared" part could mutate it out from under you. Sharing is safe **only** under
immutability. The property that looks like a restriction is what unlocks the
optimisation.

## 10. Prepend: The Cost That Does Not Grow

Section 4 showed the picture. Here is the number, measured over a list of
100,000 elements:

```text
   bytesOf(x :: xs)   =   24 bytes
```

Twenty-four bytes. One cell. The other 99,999 cells are **shared**, not copied —
both lists now reach them, neither can change them, and the garbage collector
will free them when the last of the two is dropped.

```text
   allocated:       1 cell        24 bytes
   shared:     99,999 cells   2,399,976 bytes
   total reachable from the new list:  2,400,000 bytes
```

That last line is the one people get wrong. The new list *is* 2.4 MB of reachable
memory. It **cost** 24 bytes. Those are different questions, and §20 is about
what happens when you confuse them.

## 11. Append: Paying For The Path

Append rebuilds every cell, as §4 showed, and the measurement confirms the model
exactly:

```text
   bytesOf(xs :+ x)  =  2,400,104 bytes
   100,000 x 24      =  2,400,000
   difference        =        104      the builder's own bookkeeping
```

The rule that generalises both cases, and the one worth memorising:

> **You allocate the path from the root to the change. You share everything
> else.**

For a list, "the root" is the front and the path to the back is the whole list —
hence `O(n)`. For the same list, the path to the front is empty — hence `O(1)`.
For a tree, the path is the depth, and that is the next Part.

## 12. Reverse, Map, And Why They Cost What They Cost

Apply the rule and you can predict the rest without measuring:

```text
   operation           new cells     measured over n = 100,000
   -----------------   -----------   -------------------------
   x :: xs             1                     24 bytes
   xs :+ x             n              2,400,104 bytes
   xs.reverse          n              2,400,000 bytes
   xs.map(f)           n              2,411,000 bytes
```

`reverse` lands on `n × 24` exactly, because it builds `n` cells and shares every
element. `map` costs a little more than `reverse` because the function and the
builder are themselves objects — the shape is the same.

Note what none of these copy: **the elements**. A `map(identity)` over 100,000
objects allocates 100,000 cells and zero objects. The distinction between copying
the *spine* and copying the *contents* is the whole subject of §20.

*Exercises 3 and 4 build these combinators; Exercise 1 predicts their costs
before you measure them in Exercise 8.*

---

# Part IV — Trees, Where Sharing Pays

## 13. The Structure

A binary search tree is the same two-case shape as a list, with two children
instead of one tail:

```scala
enum MyTree[+A]:
  case Leaf
  case Branch(value: A, left: MyTree[A], right: MyTree[A])
```

with the invariant that everything in `left` is smaller than `value` and
everything in `right` is larger. The invariant is what makes lookup `O(depth)`:
at each node you discard half the remaining tree.

## 14. Insert Copies The Path

To insert into an immutable tree you cannot write into a node. So you build a new
node — and its parent must point at the new node, so the parent is new too, and
so on up to the root.

```text
                 (8)                          (8')          new root
                /   \                        /   \
             (4)     (12)                (4')     (12)      12 is SHARED
            /   \                       /   \
         (2)     (6)                 (2)     (6')           2 is SHARED
                    \                           \
                     (7)                         (7)        7 is SHARED
                                                    \
                                                     (7.5)  the one new leaf
```

Four nodes rebuilt, everything hanging off them shared. **The path is the cost,
and the path is the depth.**

## 15. 504 Bytes To Modify 24 Megabytes

Measured on a perfectly balanced tree of `2²⁰ − 1` nodes:

```text
   nodes in the tree                        1,048,575
   node size, shallowSize(3, 0, 0, 0, 0)           24 bytes
   the whole tree                          25,165,800 bytes    (24 MiB)

   ONE insert allocates                           504 bytes
   nodes copied                                    21           = depth 20 + 1 new leaf
   sharing ratio                               49,932 x
```

Five hundred and four bytes to produce a new twenty-four-megabyte tree that
differs from the old one at a single position — with the old one still intact,
still valid, still usable by any other thread without a lock.

Set that against the array: a 24 MB array updated at one index costs 24 MB to
produce a new version. Five orders of magnitude, and the immutable structure is
the cheap one.

This is where the Module 1 conclusion inverts. And note the connection to that
module's Part III: every one of those 21 new nodes points *down* into older
shared nodes. Young → old. The write barrier never fires.

## 16. Depth Is Everything, And Balance Protects It

Every number in §15 depends on one quantity: the depth. And the depth depends
entirely on the order the values arrived in.

```text
   inserted in random order, 1,048,575 values:   depth ~ 20    insert ~ 504 bytes
   inserted in SORTED order, 1,048,575 values:   depth = n     insert ~ 25 MB
```

Inserting sorted data into an unbalanced BST produces a tree where every node has
one child — a linked list wearing a tree's type. Lookup degrades from `O(log n)`
to `O(n)`, and insert degrades from copying 20 nodes to copying a million.

The pathological input here is **sorted data**, which is exactly what a test
fixture, a database export or a sequence of timestamps looks like. The structure
is at its worst precisely on the input most likely to arrive in production.

Self-balancing trees — red-black, AVL — exist to make the depth bound
unconditional. They are Block 2 material. What this module asks is that you
*measure* the degeneration and understand what it costs.

*Exercises 6 and 7 build the tree; Exercise 9 measures the degeneration.*

---

# Part V — Five Traps

Each of these compiles cleanly, passes a test written over ten elements, and
fails in a way that only appears at scale.

## 17. The Quadratic That Looks Linear

```scala
items.foldLeft(List.empty[A])((acc, x) => acc :+ x)      // O(n²)
items.foldLeft(List.empty[A])((acc, x) => x :: acc).reverse   // O(n)
```

One character of difference in the operator, and a difference of 2,409× in
allocated bytes at `n = 16,000` — §7 has the table. There is no nested loop to
notice in review; the loop is hidden inside `:+`.

**How to spot it by eye:** any `O(n)` operation inside a fold or a loop over `n`
is `O(n²)`. `:+`, `++`, `length`, `last`, `apply(i)` on a `List` are all `O(n)`.

## 18. `foldRight` Is Not `foldLeft` Turned Around

```scala
def foldLeft [B](z: B)(f: (B, A) => B): B    // tail-recursive, constant stack
def foldRight[B](z: B)(f: (A, B) => B): B    // recursive, one frame per element
```

`foldLeft` can be written tail-recursively: the accumulator is complete before the
recursive call, so the call is the last thing that happens. `foldRight` cannot —
it must reach the end of the list before it can combine anything, so every
element's frame stays on the stack waiting.

```text
   foldLeft  over 1,000,000 elements   ->  fine
   foldRight over 1,000,000 elements   ->  StackOverflowError
```

The trap is that `foldRight` is often the *natural* definition — `map`, `filter`
and `append` all read beautifully as right folds — and the naturalness is what
puts a million-frame recursion into your hot path. Module 3 is about this in
full; Exercise 4 makes you meet it.

## 19. `length` In A Loop

```scala
def sum(xs: MyList[Int]): Int =
  if xs.length == 0 then 0 else xs.head + sum(xs.tail)      // O(n²)
```

`length` walks the whole list. Calling it once per element walks it `n` times.
The fix is to pattern-match on the shape instead of asking for a count:

```scala
xs match
  case Nil          => 0
  case Cons(h, t)   => h + sum(t)
```

This is why an ADT with a `case Nil` is not merely tidier than a `length == 0`
check — it is asymptotically different. **Ask the structure what it is, not how
big it is.**

## 20. Sharing Makes Garbage Invisible

```text
   bytesOf(x :: xs)                      =        24 bytes    what it COST
   memory reachable from the new list    = 2,400,000 bytes    what it RETAINS
```

Those two numbers answer different questions, and monitoring tools report the
second while code review reasons about the first.

The consequence: hold on to one short list that was built by prepending onto a
huge one, and you keep the huge one alive. A cache of "the last 10 versions" of a
structure can retain far more than ten times one version — or far less, if they
share. You cannot tell from the allocation rate, which is why heap dumps report
**retained size** separately from **shallow size**. Module 1's Exercise 7
computed shallow size; retained size is the transitive closure, and the gap
between them is exactly the sharing.

## 21. `Nil` Is Not `null`, And Not An Error

```scala
case Nil                                  // a value, and a shared singleton
def head: A                               // partial: what does Nil.head do?
```

The empty list is a legitimate value of the type, not a failure. But `head` on it
has no answer, and the choice of what to do there is a design decision you must
make rather than inherit: throw (loud, and outside the purity gate), return
`Option[A]` (total, and forces every caller to handle a case that is sometimes
impossible), or refuse it at compile time with a non-empty type.

Module 1's checklist asked the same question of `Escape.sumNorms` and the answer
there was a documented domain. Here the answer will be different, and the
exercise will ask you to defend it.

---

# Part VI — Where This Lives

## 22. Scala's Own Collections

* **`List`** is exactly the structure in this guide: `::` and `Nil`, prepend
  `O(1)`, append `O(n)`. Its API is honest about it — the operator for prepend is
  one character and the idiom for building is prepend-then-reverse.
* **`Vector`** is a 32-way branching trie, depth `log32(n)`, which is `≤ 7` for
  any collection that fits in memory. It is what you reach for when you need both
  ends and random access.
* **`Map` and `Set`** are Hash Array Mapped Tries — the HAMT whose node you
  already built in Annex A1, Exercise 8. `updated` copies the path from the root
  to the changed leaf, roughly `log32(n)` nodes, and shares every untouched
  subtree. Section 15's arithmetic *is* the arithmetic of `Map.updated`.

## 23. Git

A commit does not copy the repository. It writes new tree objects for the
directories on the path from the root to each changed file, and points at the
existing objects for everything else. A commit touching one file in a
hundred-thousand-file repository writes a handful of objects.

That is §14, applied to a filesystem, with SHA-1 hashes as the references. The
reason `git checkout` of an old commit is instant is the reason the old tree in
§15 is still valid: nothing was ever overwritten.

## 24. Undo, Event Sourcing, And Time Travel

Any feature of the form "show me what this looked like before" is structural
sharing or it is a full copy. An editor holding 200 undo steps of a large
document holds 200 versions; with sharing, the marginal cost of a step is the
path you edited. Event-sourced systems and MVCC databases (Postgres, Datomic)
are the same idea at a different scale — the old version stays readable, without
a lock, because it was never modified.

The property doing the work in all three is not the data structure. It is that
**nothing is ever overwritten**, which is the same property that lets a young
collection ignore the write barrier and the same property that makes a shared
`Nil` safe.

---

# Part VII — Scala 3: Saying It In The Type System

## 25. `enum` As A Recursive ADT

```scala
enum MyList[+A]:
  case Nil
  case Cons(head: A, tail: MyList[A])
```

Three things are happening in that declaration, and each is load-bearing.

**The type is closed.** Only these two cases exist, and the compiler knows it. A
`match` over both is provably exhaustive, and adding a third case makes every
incomplete `match` in the codebase fail to compile. That guarantee is the reason
§19's fix is safe: pattern-matching the shape cannot silently miss a case.

**It is recursive.** `Cons` holds a `MyList[A]`, so the type refers to itself.
This is what makes the structure arbitrarily long from a two-line definition.

**`Nil` takes no parameters**, so Scala 3 compiles it to a **singleton value**,
not a class. Every empty list in your program is the same object — which is why
§2's claim that `Nil` costs nothing is literally true.

## 26. The `+A`, And What `Nothing` Buys

The `+` in `MyList[+A]` declares **covariance**: if `Dog <: Animal`, then
`MyList[Dog] <: MyList[Animal]`. It is safe here precisely because the structure
is immutable — a covariant mutable container is unsound, which is why Java's
arrays are covariant and throw `ArrayStoreException` at runtime.

Covariance pays for itself immediately. Because `Nothing` is the **bottom type**,
a subtype of every type, the case `Nil` — which has type `MyList[Nothing]` —
is automatically a `MyList[Int]`, a `MyList[String]`, and a `MyList[A]` for every
`A`. One shared singleton serves every element type in the program.

Without the `+`, you would need a separate empty list per element type, and the
first line of every combinator would be a type error. The variance annotation is
not decoration; it is what makes the two-case definition work at all.

*Exercise 2 makes you feel this: write the enum without the `+` and see which
line the compiler rejects first.*

---

# Where To Go Next

| Source | Read | For |
| :--- | :--- | :--- |
| Okasaki, *Purely Functional Data Structures* | Ch. 2, then Ch. 3 | The canonical text. Chapter 2 is this module; Chapter 3 is balanced trees. |
| Okasaki, *Purely Functional Data Structures* | Ch. 5–6 | Amortisation, and why persistence breaks it. Read after Annex A2. |
| Cormen et al., *Introduction to Algorithms* | Ch. 3 | The formal asymptotic definitions the A2 annex will cover. |
| Bagwell, *Ideal Hash Trees* (2001) | §§1–3 | The HAMT behind Scala's `Map`. Pairs with Annex A1, Exercise 8. |
| Scala stdlib source | `scala.collection.immutable.List` | Read `:::` and `reverse` and check them against §12. |
| Chacon & Straub, *Pro Git* | Ch. 10, "Git Objects" | §23, from the inside. |
