# Module 3 — Stack Optimization & Control Flow Elimination

This module is about a resource you have never had to think about, because until
now nothing you wrote came close to exhausting it. Module 2 ended with a
function that does:

```text
MyList.foldRight over 200,000 elements  ->  StackOverflowError
```

and a Scaladoc admitting the debt: *"Module 3 is about removing this limit
properly."* This is that module.

Every number below was executed on this machine before it was written: JDK
21.0.9 HotSpot, 64-bit, compressed oops on, in the forked test JVM that
`build.sbt` pins to `-Xmx2g -XX:+UseG1GC`.

---

## How To Read This Guide

Each Part depends only on the Parts before it. Sections are numbered
continuously, so any point can be cited as `Part III.11`.

| Part | What it covers | Unlocks |
| :--- | :--- | :--- |
| **I** | The stack as a measured object: what a frame is, how many you get | E1 |
| **II** | Tail position, what `@tailrec` compiles to, what it refuses | E2, E3 |
| **III** | Turning `while` loops into recursion, mechanically | E4, E5 |
| **IV** | The folds that cannot be tail recursive, and the three ways out | E6, E7 |
| **V** | Rotations and balance, with no mutation anywhere | E8, E9 |
| **VI** | Four traps, each of which compiles and passes a naive test | — |
| **VII** | Where this lives: the JDK, Scala's collections, virtual threads | — |
| **VIII** | Scala 3: `@tailrec`, `final`, and `inline` | — |

**Start with Part I even if you already know what a stack is.** The content of
Part I is not the concept; it is the *numbers*, and they are the reason the rest
of the module has a shape.

---

# Part I — The Stack, Measured

## 1. The Question This Module Exists To Answer

Two functions. Same inputs, same outputs, both pure, both free of `var`:

```scala
def sumNaive(n: Int): Long = if n == 0 then 0L else n.toLong + sumNaive(n - 1)

@tailrec def sumAcc(n: Int, acc: Long = 0L): Long =
  if n == 0 then acc else sumAcc(n - 1, acc + n)
```

They agree on every input both can handle:

```text
sumNaive(10,000) = 50,005,000
sumAcc(10,000)   = 50,005,000
```

And then:

```text
sumNaive   largest n that survives  =        14,335
sumAcc     n = 10,000,000           =  survives
```

One of them has a ceiling at fourteen thousand and the other does not have one
at ten million. Nothing in the types says so. Nothing in the tests says so
either, unless a test reaches past the ceiling — and a test that walks `0 to
500` never will.

**This module is about where that ceiling comes from, how to read it off the
source, and what to do when it cannot be removed.**

## 2. What A Frame Is

When a method is called, the JVM pushes a **frame** onto the calling thread's
stack. The frame holds:

* the method's **local variables**, including its parameters and `this`;
* an **operand stack**, the scratch space bytecode computes on;
* a reference to the method's constant pool, and the **return address**.

The frame lives until the method returns. That is the whole mechanism, and the
whole problem: if `sumNaive(n)` must add `n` to the result of
`sumNaive(n - 1)`, then `sumNaive(n)`'s frame cannot be released before
`sumNaive(n - 1)` returns. The additions are all waiting.

```text
sumNaive(3)
  frame: n=3, waiting to compute 3 + _
    sumNaive(2)
      frame: n=2, waiting to compute 2 + _
        sumNaive(1)
          frame: n=1, waiting to compute 1 + _
            sumNaive(0)
              frame: n=0, returns 0
```

Four frames alive at once for `n = 3`. For `n = 14,336`, fourteen thousand three
hundred and thirty-six, and that is one too many.

## 3. How Deep You Can Actually Go

The stack is a fixed-size region per thread. Its size is set at thread creation
and never grows. Measured, by binary search for the largest surviving depth, on
freshly created threads of known stack size:

```text
stack size    max depth    bytes per frame
-----------   ----------   ---------------
   256 KiB       13,123          20.0
   512 KiB       29,485          17.8
  1024 KiB       62,231          16.8
  8192 KiB      520,945          16.1
```

The per-frame column descends, which means it is not a constant plus nothing. It
is a constant plus a **fixed overhead** — the frames already on the stack before
the measurement begins. Fitting a straight line to the last two rows:

```text
stack bytes  =  16.00 x depth  +  52,798

stack     measured depth   model    delta
-------   --------------   ------   -----
256 KiB           13,123   13,083     +40
512 KiB           29,485   29,466     +19
1 MiB             62,231   62,231       0
8 MiB            520,945  520,945       0
```

**A frame for that method is 16 bytes, and 51.6 KiB of stack is already spoken
for** by the test framework, sbt, and the JVM's own entry frames.

Sixteen bytes is small. That is the trap: a stack frame is cheap, so the failure
does not creep up on you as gradually rising memory. It arrives as a cliff.

Frame size is **not** fixed across methods. The same experiment with a method
carrying four extra `Long` parameters:

```text
one Int parameter, one Int local    max depth  40,959
four extra Long parameters          max depth  32,768
```

More locals, bigger frames, fewer of them. You cannot compute a safe depth from
the stack size alone without knowing the method.

## 4. The Stack Is Not The Heap

Module 1 and Module 2 were about the heap, and every instinct they built is
wrong here.

| | Heap | Stack |
| :--- | :--- | :--- |
| shared between threads | yes | no, one per thread |
| reclaimed by | the garbage collector | returning from the method |
| grows on demand | up to `-Xmx` | never; fixed at thread creation |
| exhaustion signals | `OutOfMemoryError` | `StackOverflowError` |
| cost you can measure | `AllocationProbe` | only by binary search to failure |

Two consequences worth holding on to:

* **`-Xmx2g` does nothing for you here.** A recursion 14,336 deep fails on a
  machine with a terabyte of free heap.
* **You cannot instrument the stack the way Module 1 instrumented the heap.**
  There is no `ThreadMXBean` counter for "frames currently pushed". The only
  honest measurement is the one used above: call it with increasing `n` until it
  throws. Exercise 1 builds that instrument.

---

# Part II — Tail Position

## 5. Reading Tail Position Off The Syntax

A call is in **tail position** when its result *is* the result of the enclosing
method — when nothing happens after it returns.

This is a syntactic property. You can read it off the page, and you should learn
to, because the compiler reads it exactly the same way.

```text
expression                              is the call in tail position?
-------------------------------------   ------------------------------
f(x)                                    yes, if it is the whole body
1 + f(x)                                no - the + happens after
if p then f(x) else g(y)                yes, both of them
f(x) match { case ... => ... }          no - f(x) is the scrutinee
x match { case _ => f(x) }              yes, in every branch's result
try f(x) catch { ... }                  no - the handler must stay installed
f(x) + g(y)                             no, neither
return f(x)                             yes
f(g(x))                                 f yes, g no
```

The pattern is one rule: **follow the value.** If the value the call produces is
handed straight back to the caller, untouched, the call is in tail position. If
*anything* consumes it first — an operator, a `match`, a `try` — it is not.

The obvious wrong instinct is to think that a call being *last on the page*
makes it a tail call. It does not. In `1 + f(x)`, `f(x)` is written last and is
not in tail position, because the `+` is still waiting.

## 6. What `@tailrec` Compiles To

`scala.annotation.tailrec` is not a hint and not an optimisation request. It is
an **assertion**: the compiler either transforms the method or refuses to
compile it. There is no silent middle.

What the transformation produces is worth seeing rather than describing. Both
methods from §1, disassembled with `javap -c`:

```text
public long sumNaive(int);
   0: iload_1
   1: iconst_0
   2: if_icmpne     7
   5: lconst_0
   6: lreturn
   7: iload_1
   8: i2l
   9: aload_0
  10: iload_1
  11: iconst_1
  12: isub
  13: invokevirtual  sumNaive:(I)J       <- a call
  16: ladd                               <- work AFTER the call
  17: lreturn
```

```text
public long sumAcc(int, long);
   0: iload_1
   1: iconst_0
   2: if_icmpne     7
   5: lload_2
   6: lreturn
   7: iload_1
   8: iconst_1
   9: isub
  10: istore        4        <- compute the new n
  12: lload_2
  13: iload_1
  14: i2l
  15: ladd
  16: lstore        5        <- compute the new acc
  18: iload         4
  20: istore_1              <- overwrite the parameter n
  21: lload         5
  23: lstore_2              <- overwrite the parameter acc
  24: goto          0        <- jump back to the top
```

**There is no `invoke` in the second listing at all.** The recursive call became
`goto 0`. The parameters are overwritten in place, and one frame does the whole
job, however large `n` is.

That is the entire mechanism, and it explains both halves of §1's table: a jump
cannot overflow a stack, because it does not push anything.

It also explains something people find surprising the first time: the tail
recursive version and the `while` loop compile to *the same shape*. A
`while`-based `sumLoop` also survives `n = 10,000,000`, because after
compilation there is nothing left to tell them apart. **Tail recursion is not
"recursion that the JVM happens to tolerate". It is a loop, written so that the
loop variables are named and immutable in the source.**

## 7. What `@tailrec` Refuses

The refusals are the useful part, because each one names a real reason the
transformation would be unsound.

**It refuses a call that is not in tail position.**

```scala
@tailrec def bad(n: Int): Long = if n == 0 then 0L else n + bad(n - 1)
// error: Cannot rewrite recursive call: it is not in tail position
```

**It refuses a method that could be overridden.** If `sumAcc` were a non-final
method on an open class, the recursive call would be *virtual*: a subclass could
replace it, and turning it into a `goto` would jump to the wrong body. The fix
is `final`, `private`, or an `object` — which is why the helper inside a method
is the idiom this repository uses everywhere:

```scala
def length: Int =
  @tailrec def loop(as: MyList[A], acc: Int = 0): Int = ...
  loop(xs)
```

A local `def` cannot be overridden by anyone, so the question never arises.

**It refuses mutual recursion.**

```scala
@tailrec def isEven(n: Int): Boolean = if n == 0 then true else isOdd(n - 1)
@tailrec def isOdd(n: Int): Boolean = if n == 0 then false else isEven(n - 1)
// error: TailRec optimisation not applicable,
//        method isEven contains no recursive calls
```

Read that message twice. Both calls *are* in tail position, and the compiler
does not say they are badly placed — it says there are **no recursive calls at
all**. From the transformation's point of view that is literally true: it looks
for calls to `isEven` inside `isEven`, and finds none. `isOdd` is just some
other method.

The refusal is a limitation of the mechanism rather than of the theory: `goto 0`
can only jump to the top of the method it is in. Mutual tail calls need a
different mechanism, and that mechanism is a trampoline — Part IV.16.

**The rule to take away:** if you want a guarantee, you must ask for it. An
unannotated method that happens to be tail recursive today is tail recursive
until someone edits it, and nothing will tell you when that stops being true.
`@tailrec` is a test that runs at compile time.

## 8. The Accumulator

Every transformation in this module is the same move, so it is worth naming.

A non-tail recursion computes its answer **on the way back up**: the deepest
call returns first, and each frame applies its pending operation as the stack
unwinds. That pending operation is what the frame exists to remember.

An accumulator moves that work **to the way down**. Instead of remembering "I
still owe an addition of `n`", you perform the addition immediately and carry
the running total as a parameter.

```text
sumNaive(3)                        sumAcc(3, 0)
  3 + sumNaive(2)                    sumAcc(2, 3)
    3 + (2 + sumNaive(1))              sumAcc(1, 5)
      3 + (2 + (1 + 0))                  sumAcc(0, 6)
    = 6                                = 6

work happens as the stack unwinds     work happens before the call
memory: one frame per element         memory: one frame
```

**The parameter is the stack.** The information the frames were holding has been
made explicit and moved into an argument. That is why the transformation is
always possible in principle — and Part IV is about the cases where "in
principle" hides a real cost.

One caution, which Part VI.21 develops: the accumulator version applies the
operation in the **opposite order**. `sumAcc` adds `3 + 2 + 1` while `sumNaive`
adds `1 + 2 + 3`. For `+` on integers that does not matter. For an operation
that is not associative and commutative, it changes the answer.

---

# Part III — Eliminating Control Flow

## 9. `while` Becomes Tail Recursion, Mechanically

This is a translation with no creative step in it. Given:

```scala
var i = n
var acc = 0L
while i > 0 do
  acc += i
  i -= 1
acc
```

every part has a destination:

```text
the loop's mutable variables    ->   the helper's parameters
the loop condition              ->   the if that guards the recursive call
the loop body's updates         ->   the arguments of the recursive call
the value after the loop        ->   the base case's return
```

```scala
@tailrec def loop(i: Int, acc: Long): Long =
  if i > 0 then loop(i - 1, acc + i) else acc
loop(n, 0L)
```

Read the two side by side and the correspondence is exact: `i` and `acc` were
`var`s and are now parameters; `i > 0` moved into the `if`; `acc += i` and
`i -= 1` became the two arguments; the trailing `acc` became the `else`.

**Nothing was invented and nothing was lost.** The measured result is identical
in both value and cost — §6 showed they compile to the same shape.

What *was* gained is that the states are now named. In the loop, `acc` is one
variable that holds different values at different times, and to reason about it
you must simulate the machine. In the recursion, each iteration has its own
`acc`, immutable for the whole of its life, and to reason about it you check one
implication: if `acc` is correct on entry, is it correct in the call?

## 10. Two Variables, And Loops That Stop Early

Multiple loop variables need no new idea — they are more parameters:

```scala
// an imperative Fibonacci
var a = 0L; var b = 1L; var i = 0
while i < n do { val t = a + b; a = b; b = t; i += 1 }
a
```

```scala
@tailrec def loop(a: Long, b: Long, i: Int): Long =
  if i >= n then a else loop(b, a + b, i + 1)
loop(0L, 1L, 0)
```

Note what the recursion fixed for free: the imperative version needs the
temporary `t`, because assigning `a = b` before reading `a` would lose it. In
the recursive version `b` and `a + b` are both evaluated from the *current*
values before either is bound, so the temporary has nowhere to come from.
**A whole class of ordering bug is not fixed here; it is unrepresentable.**

**Stopping early** is an extra branch, not an extra mechanism:

```scala
@tailrec def indexOf(as: MyList[A], x: A, i: Int = 0): Int = as match
  case Nil => -1
  case Cons(h, t) => if h == x then i else indexOf(t, x, i + 1)
```

The `if h == x then i` is the early exit: a branch that returns instead of
recursing. There is no work to unwind because there never was any pending.

## 11. `break`, `continue`, And Early `return`

| imperative | functional replacement |
| :--- | :--- |
| `break` | a base case that returns without recursing |
| `continue` | a recursive call that skips the body's remaining work |
| early `return` | a branch of the `if` or `match` |
| a loop that sets a flag | a parameter carrying the flag, or a different base case |

`continue` is the one worth writing out, because the translation is less
obvious. An imperative loop that skips odd numbers:

```scala
var i = 0; var acc = 0L
while i < n do { i += 1; if i % 2 == 1 then () else acc += i }
```

becomes a recursion where the skip is simply *not adding*:

```scala
@tailrec def loop(i: Int, acc: Long): Long =
  if i >= n then acc
  else if (i + 1) % 2 == 1 then loop(i + 1, acc)   // the "continue"
  else loop(i + 1, acc + i + 1)
```

The `continue` branch and the normal branch differ only in what they pass as
`acc`. That is the general shape: **in a loop, control flow decides which
statements run; in a recursion, it decides which arguments are passed.**

## 12. A `var` In A Loop Is An Accumulator In Disguise

This is the diagnostic to carry out of Part III. When you see a `var` declared
before a loop and mutated inside it, you are looking at an accumulator that has
not been given a name yet. The translation is mechanical, and §9 is the whole of
it.

The `var`s that are *not* accumulators are the loop counters, and those are
accumulators too — they accumulate "how far along am I".

There is one honest exception, and this repository relies on it: the internal
engine of a low-level micro-library, where a lock-free algorithm needs
`AtomicReference` and local mutability. The rule in `CLAUDE.md` permits exactly
that and nothing wider. Everything in this module is on the other side of the
line.

---

# Part IV — When Tail Recursion Is Not Enough

## 13. Why `foldRight` Cannot Be Tail Recursive

```scala
def foldRight[B](z: B)(f: (A, B) => B): B = xs match
  case Nil => z
  case Cons(h, t) => f(h, t.foldRight(z)(f))
```

The recursive call is an **argument to `f`**. By §5's rule, that is not tail
position: `f` consumes the result, so the frame must survive to call it.

And this is not a defect in the writing. It is forced by what `foldRight`
*means*:

```text
foldLeft:   f(f(f(z, a1), a2), a3)      the leftmost combination is available first
foldRight:  f(a1, f(a2, f(a3, z)))      the rightmost combination is needed first
```

`foldLeft` can compute as it walks forwards, because its next argument is always
the running result. `foldRight` cannot: to compute `f(a1, ...)` it must already
know `f(a2, f(a3, z))`, which lies at the far end of the list. **It has to reach
the end before it can combine anything**, and a singly-linked list offers exactly
one way to reach the end.

Measured, on `MyList`:

```text
foldLeft  over 200,000    survives
foldRight over 200,000    StackOverflowError
foldRight overflows just above n = 14,990
```

Fifteen thousand. That is not a number anyone would find in testing and it is
smaller than plenty of real inputs.

## 14. Reverse-And-`foldLeft`: The Cheap Fix And Its Price

If the problem is that the right end is needed first, put the right end first:

```scala
def foldRightSafe[B](z: B)(f: (A, B) => B): B =
  xs.reverse.foldLeft(z)((b, a) => f(a, b))
```

Note the argument swap in the lambda: `foldLeft` hands you `(accumulator,
element)` and `f` wants `(element, accumulator)`. Getting that backwards
compiles whenever `A` and `B` are the same type, and silently computes something
else — Part VI.21.

It works, and it works to the horizon:

```text
agrees with foldRight at n = 1,000        true
foldRightSafe over 1,000,000              survives
```

The price is one full extra spine, exactly as Module 2's cost model predicts:

```text
                  bytes at n = 100,000
---------------   --------------------
foldLeft                     2,399,640
foldRightSafe                4,800,040
                             ---------
delta                        2,400,400   = 100,017 cells
```

One cell per element, which is `reverse`. You have converted **stack** into
**heap** — and that is the trade, stated plainly. The heap is larger, growable,
garbage-collected and measurable; the stack is none of those. It is usually the
right trade, and it is never a free one.

## 15. Laziness: The `foldRight` That Stops Early

There is a second way out, and it buys something the first cannot.

If `f` takes its second argument **by name**, it can decline to evaluate it:

```scala
def foldRightLazy[B](z: => B)(f: (A, => B) => B): B = xs match
  case Nil => z
  case Cons(h, t) => f(h, t.foldRightLazy(z)(f))
```

For an `f` that ignores its second argument on some input — `exists`, `find`,
`takeWhile`, `||` — the recursion *stops at that point* and never descends
further. A search that succeeds at element 3 of a million costs three frames.

This does not remove the ceiling: an `f` that always needs its second argument
still walks to the end and still overflows. What it removes is the ceiling *for
the operations that can short-circuit*, and it does so without allocating a
reverse spine.

The two fixes solve different halves, which is why the standard library has
both. Block 3 turns the by-name parameter into a first-class `Eval` type; this
module only needs to know the shape exists.

## 16. The Shape Of A Trampoline

The third way out is the general one, and this module stops at describing it.

Instead of *making* the call, return a value that *describes* the call, and let
a loop at the top perform it:

```text
sealed trait Step[A]
  case Done(a: A)                 "the answer is a"
  case More(next: () => Step[A])  "call this to get closer"

@tailrec def run[A](s: Step[A]): A = s match
  case Done(a) => a
  case More(k) => run(k())
```

The recursion is now a `@tailrec` loop in `run`, and the chain of pending calls
lives in **heap-allocated closures** rather than in frames. This is what makes
mutual recursion work (§7's refusal disappears — `run` only ever calls itself),
and it is the only technique here with no depth limit at all.

It also costs an object per step, an indirect call per step, and it defeats
inlining. Block 3, Module 7 builds one properly and measures the price.

**The three ways out, side by side:**

| technique | removes the limit | cost | works for |
| :--- | :--- | :--- | :--- |
| accumulator (`foldLeft`) | yes | none | operations that can compute forwards |
| reverse + `foldLeft` | yes | one extra spine, `O(n)` heap | any `foldRight` |
| by-name / lazy | only for short-circuiting `f` | none when it stops early | `exists`, `find`, `\|\|` |
| trampoline | yes, including mutual recursion | one object and one indirect call per step | everything |

---

# Part V — Trees Without Mutation

## 17. Rotation, Purely

Module 2 proved that a BST built from sorted input degenerates into a spine of
depth `n`, and that `contains` then costs `n` comparisons instead of `log n`.
Rotation is the repair, and in an immutable structure it is a **pattern match
that rebuilds three nodes' worth of links**.

```scala
def rotateRight[A](t: MyTree[A]): MyTree[A] = t match
  case Branch(v, Branch(lv, ll, lr), r) => Branch(lv, ll, Branch(v, lr, r))
  case other => other
```

Worked, on the three-node left spine `3 -> 2 -> 1`:

```text
before                after
                                        rotateRight
      3                   2
     /                   / \
    2        ==>        1   3
   /
  1

depth 3               depth 2
in-order [1, 2, 3]    in-order [1, 2, 3]      <- unchanged
allocated                        48 bytes = 2 nodes
```

Two facts, both measured, and both essential:

* **The in-order sequence is identical.** That is what makes a rotation *legal*:
  it changes the shape and not the contents, so every BST invariant survives it.
  Anything that changed the in-order walk would not be a rotation.
* **It allocates two nodes, not the subtree.** `ll`, `lr` and `r` are shared by
  reference. This is Module 2's path rule again: you allocate the nodes whose
  links change, and share everything hanging below them.

## 18. The AVL Invariant

A rotation fixes one local imbalance. To keep a tree balanced you need a rule
saying *when* to rotate, and a proof that following it bounds the depth.

The AVL rule is the simplest one that works:

> **For every node, the depths of its two subtrees differ by at most 1.**

Call that difference the node's **balance factor**, `depth(left) - depth(right)`.
It must stay in `{-1, 0, +1}`. An insert can push one node to `+2` or `-2`, and
exactly four cases can arise, each with a fixed repair:

```text
case          shape                         repair
-----------   ---------------------------   --------------------------------
left-left     +2, left child leaning left   rotateRight(t)
left-right    +2, left child leaning right  rotateLeft on left, then Right
right-right   -2, right child leaning right rotateLeft(t)
right-left    -2, right child leaning left  rotateRight on right, then Left
```

The two "outer" cases need one rotation; the two "inner" cases need two, because
a single rotation on an inner-leaning child moves the problem rather than fixing
it. Exercise 8 builds all four, and the spec drives every one of them.

The bound this buys: an AVL tree of `n` nodes has depth at most
`1.44 log2(n + 2)`. Not `log2 n` exactly — the guarantee is weaker than perfect
balance and much cheaper to maintain, and it is enough, because it keeps the
depth within a constant factor of the minimum forever.

## 19. What Rebalancing Allocates

An insert into an immutable AVL tree allocates:

```text
the path from the root to the insertion point    depth nodes    (Module 2, E1)
the new leaf                                     1 node
the nodes rebuilt by any rotation performed      at most 2 nodes per rotation
```

And the fact that makes AVL practical: **an insert performs at most one
rotation** (single or double), no matter how large the tree. Rebalancing does
not cascade upward on insert the way it can on delete.

So insert stays `depth + 1 + O(1)` nodes, which is `O(log n)` allocation and
`O(log n)` time, permanently — against a plain BST, where Module 2 measured
4,097 nodes for one insert into a tree that had been fed sorted input.

The depth stays bounded whatever order the data arrives in. That is the whole
point: Module 2 showed that **arrival order** decides the depth, and Module 2's
closing box named the production inputs that arrive sorted. AVL makes the
question stop mattering.

---

# Part VI — Four Traps

Every one of these compiles, and three of them pass a test suite that only
checks returned values.

## 20. `@tailrec` On A Method Someone Can Override

```scala
class Walker:
  @tailrec def count(as: MyList[Int], acc: Int = 0): Int = ...
// error: TailRec optimisation not applicable,
//        method count is neither private nor final so can be overridden
```

The compiler catches this one, which is why it is first: it is the trap you are
*allowed* to fall into, because the error message stops you. The lesson is what
it implies for the method you did **not** annotate — a public, non-final,
accidentally-tail-recursive method is not optimised, and nothing says so.

```text
annotated + final/private/local    ->  transformed, guaranteed, forever
annotated + overridable            ->  compile error
not annotated + tail recursive     ->  transformed today, silently, by luck
not annotated + not tail recursive ->  a ceiling nobody has measured
```

Only the first row is a guarantee. Rows three and four look identical in a
review.

## 21. The Accumulator That Changes The Answer

Part II.8 warned about it; here is the failure.

```text
list        [1, 2, 3]

foldRight with (-)      1 - (2 - (3 - 0))  =  1 - (2 - 3)  =  1 - (-1)  =   2
foldLeft  with (-)      ((0 - 1) - 2) - 3                              =  -6
```

Rewriting a `foldRight` as a `foldLeft` is only sound when `f` is associative
**and** `z` is its identity. For `+` and `*` it is. For `-`, `/`, string
concatenation, list `::`, and function composition it is not.

The second form of this trap is subtler and it is in §14's own fix. The lambda
must swap the arguments:

```scala
xs.reverse.foldLeft(z)((b, a) => f(a, b))   // correct
xs.reverse.foldLeft(z)((a, b) => f(a, b))   // compiles whenever A == B
```

When `A` and `B` are the same type — `MyList[Int]` folded to an `Int`, which is
most test fixtures — the wrong version type-checks and returns a wrong answer for
any non-commutative `f`. A suite that folds with `+` cannot see it. **Test the
folds with subtraction.**

## 22. Depth-Bounded Is Not Size-Bounded

Module 2 documented `MyTree`'s recursion as bounded by depth rather than size,
and allowed it to stay non-`@tailrec` on that argument. The argument is true and
its premise is not guaranteed:

```text
balanced tree of 4,096 nodes     depth     13     recursion 13 deep
sorted input, 4,096 inserts      depth  4,096     recursion 4,096 deep
```

A "depth-bounded" recursion on a degenerate tree is a size-bounded recursion
wearing a reassuring word. The depth of a plain BST is not a property of the
structure; it is a property of the input, and §18 exists to take that property
away from the input.

Until it is balanced, "bounded by depth" is a promise the caller keeps, not one
the code keeps.

## 23. A Stack Limit Is A Property Of The Machine

Every depth in this guide was measured here, and none of them transfers:

```text
this machine, default stack    sumNaive survives to  14,335
256 KiB stack                  depth                 13,123
8 MiB stack                    depth                520,945
```

A 40× range from one flag. And a second range, inside a single process, from
nothing you control at all — ten searches for the same boundary, in order:

```text
run  1    32,768    and the very next call to deep(32,768) fails
run  2    24,575    the interpreted frame
run  3+   61,653    the C2-compiled frame, stable to within 8 frames
```

**A compiled frame is 2.51× smaller than an interpreted one**, so the boundary
rises by that factor as the JIT does its work — and the first search straddles
the transition, returning a number that was true for part of it and false for
the rest. That first row is the dangerous one: it is not noise, it is a
measurement of a quantity that changed while it was being measured.

Which means:

* **Never write an assertion on an absolute depth.** `assert(survives(50_000))`
  passes on one machine and fails on another, and it is testing `-Xss`.
* **Warm the function before measuring it.** Module 2's `SharingProof.bytesOf`
  says it for the heap — *a cold measurement measures the interpreter* — and
  here it decides a factor of 2.51 rather than a few per cent.
* **Assert the *class* instead:** that the tail-recursive version survives an
  input the naive one cannot, that the ratio between them is large, that a
  transformation did not change a returned value. Exercise 1's harness exists so
  the specs can say those things without saying a number.
* **And leave a margin even then.** `found + 1` is a margin of one frame on a
  quantity that drifts by eight between consecutive warm calls. A boundary
  asserted to the frame is the same mistake as an absolute depth, only harder to
  see.

This is Module 2's argument about ratios against absolutes, moved to a different
resource. It is the same lesson and it keeps arriving.

---

# Part VII — Where This Lives

## 24. The JDK And Scala's Own Collections

`scala.collection.immutable.List.foldRight` does not recurse. It is:

```scala
def foldRight[B](z: B)(op: (A, B) => B): B = reverse.foldLeft(z)((right, left) => op(left, right))
```

— §14's fix, in the standard library, with the argument swap of §21 written out.
The library pays a whole extra spine on every `foldRight` rather than ship a
function with a fifteen-thousand-element ceiling.

`List.map` does not recurse either: it builds forwards with a `while` loop,
mutating the tail pointer of the cell it just created. That is exactly the
implementation Module 2's purity gate forbids, and the reason the library is
allowed to is that the mutation never escapes the method — which is the same
argument `CLAUDE.md` makes for a micro-library's internal engine.

`Vector`, `HashMap` and `TreeMap` are all bounded-depth by construction, for the
reason §22 gives: a structure whose depth is a function of its *contents* rather
than its *arrival order* never needs the caller to keep a promise.

## 25. Virtual Threads: Stacks On The Heap

A platform thread's stack is a fixed OS-level allocation — the 1 MiB of §3 —
which is why a server cannot have a million of them. A **virtual thread**
(`Thread.ofVirtual()`, JDK 21) keeps its stack on the **heap**, copying it in and
out as the thread mounts and unmounts a carrier.

That changes the arithmetic of this entire module:

* a virtual thread's stack **grows on demand**, bounded by the heap rather than
  by `-Xss`;
* it costs what it uses, so an idle one is nearly free;
* and deep recursion becomes a heap concern, which means it becomes measurable
  again by Module 1's instruments.

It does not make the problem go away — it converts a `StackOverflowError` into
memory pressure, and `CLAUDE.md` permits `Thread.ofVirtual` inside the
micro-library engines of Block 3, which is where this thread is picked up.

---

# Part VIII — Scala 3: Saying It In The Type System

## 26. `@tailrec`, `final`, `private`

The three modifiers that make the guarantee available, and what each one rules
out:

```text
private def       no subclass can see it, so none can override it
final def         a subclass can see it and cannot replace it
def inside a def  nothing outside the method can name it at all
object method     there is no subclass to worry about
```

The local `def` is the strongest and the cheapest, and it is what every
`@tailrec` walk in this repository uses. It also scopes the helper's parameters
to exactly where they mean something: `loop(as, acc)` makes no sense outside
`length`, and outside `length` it does not exist.

## 27. `inline` And The Frame That Never Exists

Scala 3's `inline def` is a compile-time expansion: the body is substituted at
the call site, so there is no call and therefore no frame.

```scala
inline def twice(inline f: Int => Int, x: Int): Int = f(f(x))
```

This interacts with everything above in one important way: **inlining removes
frames that `@tailrec` could not**, because it removes the call entirely rather
than converting it to a jump. But it only works where the expansion is finite —
a recursive `inline def` must terminate at compile time, or the compiler reports
an expansion limit rather than a stack overflow at run time.

It is not a substitute for tail recursion. It is the other end of the same
question: `@tailrec` makes an unbounded number of calls cost one frame, `inline`
makes a fixed number of calls cost none. Block 2 uses `inline` for typeclass
derivation; this module only needs the distinction.

---

## Where To Go Next

| Topic | Source |
| :--- | :--- |
| Frames, operand stacks, the `goto` instruction | *The Java Virtual Machine Specification*, §2.5.2 and §2.6 |
| Tail calls and why the JVM lacks them natively | JVM Spec §2.6.5; JEP 444 for what Loom changes instead |
| Accumulator passing, formally | Bird & Wadler, *Introduction to Functional Programming*, ch. 4 |
| `foldRight` vs `foldLeft` and their duality | Hutton, *A tutorial on the universality and expressiveness of fold* |
| AVL trees, insertion and the four rotation cases | Okasaki, *Purely Functional Data Structures*, ch. 3; Sedgewick, *Algorithms*, §3.3 |
| Trampolines and defunctionalised continuations | Bjarnason, *Stackless Scala With Free Monads* |
| Virtual thread stacks | JEP 444, *Virtual Threads*; Pressler's *Loom* talks |
