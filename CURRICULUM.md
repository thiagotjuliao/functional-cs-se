# 🎓 Advanced Curriculum: Computer Science & Functional Software Engineering

This document outlines the complete, 22-week self-guided curriculum using **Scala 3** under the **Pure Functional Programming** paradigm. Every block is designed to be built from scratch without external dependencies.

---

## 🏛️ Repository Architecture (sbt Multi-Project)

The repository layout aggregates 4 independent sbt subprojects under a single root project:

```text
functional-cs-se/
 ├── build.sbt
 ├── block1-fundamentals/           // Subproject 1
 │    ├── src/main/scala/...        
 │    ├── src/test/scala/...        // Exercises + MUnit specs
 │    └── docs/                     // Requirements & Learning central
 │         ├── checklist.md         
 │         └── theory/              
 │              ├── references.md   
 │              └── module1_jvm.md  
 ├── block2-category-types/         // Subproject 2
 ├── block3-effects-concurrency/    // Subproject 3
 └── block4-distributed-streams/    // Subproject 4
```

---

## 📊 Schedule & Complexity Overview

| Block | Estimated Duration | Average Complexity | Core Focus |
| :--- | :---: | :---: | :--- |
| **1. CS Foundations & Pure FP** | 4 Weeks | ⭐️⭐️ | JVM Semantics, Recursion, Stack Tuning. |
| **2. Category Theory & Type Systems** | 6 Weeks | ⭐️⭐️⭐️⭐️ | Type-Level Programming, Custom Monads. |
| **3. Software Engineering & Effects**| 6 Weeks | ⭐️⭐️⭐️⭐️⭐️ | Fiber Runtime, Concurrency, Project Loom. |
| **4. Distributed Systems & Streams** | 6 Weeks | ⭐️⭐️⭐️⭐️⭐️ | Manual Backpressure, NIO Sockets, Codecs. |
| **Total Course** | **22 Weeks (~5 Months)** | **High (4.2/5.0)** | Elite Engineer & Scientist Formation. |

---

## 📚 Detailed Block Curriculum

### 📂 Subproject 1: `block1-fundamentals`
* **Duration:** 4 Weeks | **Complexity:** ⭐️⭐️
* **Focus:** Low-level JVM memory management under immutability stress, loop elimination, and functional persistent data structures.

* **Module 1: JVM Semantics & Immutability Allocation Stress (Week 1)**
  * *Theory:* Stack vs. Heap semantics, the mechanics of high object allocation rates in FP, JIT Compiler *Escape Analysis*, and tuning Garbage Collection (G1GC/ZGC) behavior under heavy object churning.
* **Module 2: Manual Persistent Data Structures (Week 2)**
  * *Theory:* Asymptotic Analysis (Big-O Notation), structural sharing mechanics in memory.
  * *Implementation:* Writing native `List` and `Tree` data structures from scratch using Scala 3 `enum` and `case class`.
* **Module 3: Stack Optimization & Control Flow Elimination (Week 3)**
  * *Theory:* Elimination of imperative constructs, advanced tail-call recursion optimization (`@tailrec`), and structural prevention of `StackOverflowError`.
* **🎯 Graded Exercise Set (Week 3):**
  * *Easy:* Transforming imperative `while` loops into pure tail-recursive functions.
  * *Medium:* Implementing custom structural combinators (`map`, `filter`, `foldLeft`, `foldRight`) on your manual persistent list.
  * *Hard:* Writing a balanced, pure functional Binary Search Tree (BST) supporting rotations with zero variable mutations.
* **🚀 Mini-Project 1: Algebraic Expression Evaluation Engine & AST (Week 4)**
  * *Description:* Build a purely functional mathematical parser and evaluation engine. The system will ingest raw math strings, generate an Abstract Syntax Tree (AST), optimize the expressions algebraically using pattern matching identities, and safely compute the output guaranteeing zero stack overflow risk and clean heap allocation.

---

### 📂 Subproject 2: `block2-category-types`
* **Duration:** 6 Weeks | **Complexity:** ⭐️⭐️⭐️⭐️
* **Focus:** Mapping Category Theory to production software design, type-driven boundaries, and mathematical property verifications.

* **Module 4: Domain-Driven Design via Algebraic & Opaque Types (Week 5)**
  * *Theory:* Type Isomorphism, Product and Sum Types, and enforcing domain boundaries at compile-time by making invalid states unrepresentable.
  * *Implementation:* Modeling complex data domains using Scala 3 `opaque types` for primitives and `enum` for type-safe Algebraic Data Types (ADTs).
* **Module 5: Cutting-Edge Ad-Hoc Polymorphism (Week 6)**
  * *Theory:* Theoretical distinction between Object-Oriented subclassing and Ad-Hoc Polymorphism.
  * *Implementation:* Writing native Typeclasses from scratch leveraging Scala 3’s context mechanics: `given`, `using`, and `extension` blocks.
* **Module 6: Reconstructing Category Theory Algebras (Weeks 7-8)**
  * *Theory:* Endofunctors, Monads as Monoids in the category of Endofunctors, Composability boundaries, and Monad Transformers.
  * *Implementation:* Coding your own structural interfaces for `Functor`, `Applicative`, and `Monad`. Implementing concrete instances for custom data wrappers alongside `State` and `Either` monads.
* **🎯 Graded Exercise Set (Week 9):**
  * *Easy:* Designing smart constructors with opaque types to block bad primitives (e.g., structural Email/CPF wrappers).
  * *Medium:* Providing manual `Functor` and `Monad` instances for deeply nested or custom recursive structures.
  * *Hard:* Creating a custom property-based validation mini-engine on top of MUnit to empirically verify Monad Laws (Left Identity, Right Identity, Associativity).
* **🚀 Mini-Projeto 2: Combinatoric Validation Framework & State Mutation Engine (Week 10)**
  * *Description:* Code your own mini-"Cats Core" engine. The integration goal is a resilient Financial Transaction Processor. Incoming complex data validations must accumulate multiple parallel processing errors via a custom `Validated` data structure, and successive structural state mutations must be evaluated purely functionally through your manual `State` monad, yielding immutable transaction logs without any variable mutations.

---

### 📂 Subproject 3: `block3-effects-concurrency`
* **Duration:** 6 Weeks | **Complexity:** ⭐️⭐️⭐️⭐️⭐️
* **Focus:** Computations suspension, lazy evaluation mechanics, designing custom asynchronous green-thread schedulers, and Loom virtualization.

* **Module 7: Eager vs. Lazy Evaluation & Trampolining Mechanics (Week 11)**
  * *Theory:* The danger of immediate expression side-effects. Building functional control types (`Eval`). The theory of *Trampolining* (transforming runtime stack call chains into heap-allocated data records).
* **Module 8: IO Monad Architecture & Project Loom Scheduling (Weeks 12-13)**
  * *Theory:* The `IO[A]` data type as a blueprint/algebraic description of a program. Concurrency paradigms: OS Threads vs. Application-level Fibers vs. JVM-Native Virtual Threads (Project Loom).
  * *Implementation:* Writing a native `IO[A]` type supporting lazy encapsulation, flatMap chains, and pure error recovery. Designing the internal interpreter runtime (*Fiber Scheduler*) implementing two swappable strategies: one over a standard OS Thread Pool Executor, and one leveraging native `Thread.ofVirtual()` bindings.
* **Module 9: Custom Lock-Free Concurrency Primitives (Week 14)**
  * *Theory:* Abstract mutual exclusion, structural signaling, and coordinating shared state under highly asynchronous thread environments.
  * *Implementation:* Coding thread-safe `Ref` wrappers (using Java's `AtomicReference`) and async single-assignment `Deferred` variables (logical async promises) from scratch without utilizing heavy `synchronized` statements.
* **🎯 Graded Exercise Set (Week 15):**
  * *Easy:* Writing a custom lazy data abstraction that handles atomic memoization (evaluate once, cache safely).
  * *Medium:* Implementing a concurrency racing combinator (`race`), where two competing `IO` tasks execute simultaneously, automatically canceling the loser cleanly.
  * *Hard:* Coding an asynchronous, non-blocking binary `Semaphore` relying exclusively on your native `IO`, `Ref`, and `Deferred` implementations.
* **🚀 Mini-Projeto 3: Micro-Effect Runtime & Resilient Background Job Processor (Week 16)**
  * *Description:* Build a lightweight, custom clone of a framework like "Cats Effect" or "ZIO". The mini-project consists of an high-throughput background batch processing engine. The system will poll high-volume mock tasks, dynamically throttle concurrency quotas through your custom fiber runtime, allocate loads across virtual threads, and track system vital signs via an immutable fiber context tracker (`FiberRef`).

---

### 📂 Subproject 4: `block4-distributed-streams`
* **Duration:** 6 Weeks | **Complexity:** ⭐️⭐️⭐️⭐️⭐️
* **Focus:** Infinite data stream pipes with explicit backpressure, bulletproof resource safety, low-level binary codecs, and high-performance network programming.

* **Module 10: Reactive Streaming Foundations & Manual Backpressure (Week 17)**
  * *Theory:* Co-induction in Computer Science. The problem of unbounded memory inflation in streaming systems. Controlling consumer demand (*Backpressure*).
  * *Implementation:* Engineering a custom `Stream[A]` based on a Pull/Push protocol that enforces processing rates based on the actual consumer availability.
* **Module 11: Rigid Resource Management & I/O Lifecycles (Week 18)**
  * *Theory:* Strong guarantees in software production environments (eliminating leakage of file descriptors, database pools, and sockets under catastrophic failures).
  * *Implementation:* Engineering a structural `Resource` abstraction (or embedding a `bracket` lifecycle engine directly into your `IO`) to enforce deterministic setup and tear-down actions.
* **Module 12: Tagless Final Algebras & Low-Level Binary Codecs (Weeks 19-20)**
  * *Theory:* The Tagless Final pattern (Functional Inversion of Control). Extreme network serialization optimizations via pure typeclass-driven transformations without runtime reflection overhead.
