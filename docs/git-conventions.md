# Git Conventions — Milestone Tagging Contract

This repository is a 22-week academic record, not a product. Its history has one
job: to make the *evolution of understanding* diffable. A tag is therefore not a
release — it is a **proof of completion**: the point at which every acceptance
criterion of a module's checklist is `[x]` and the module's MUnit suite is green.

---

## 1. Tag Naming Scheme

```text
b<block>-m<module>-<kebab-slug>     // a theory + exercise module
b<block>-p<n>-<kebab-slug>          // a block's capstone mini-project
```

Tags are **annotated** (`git tag -a`), never lightweight. An annotated tag is a
real object in the object database carrying an author, a date and a message; a
lightweight tag is just a pointer and records nothing about *why* the milestone
was reached.

## 2. The Complete Tag Map

| Tag | Milestone | Week |
| :--- | :--- | :---: |
| `v0-setup` | Build contract, tooling, repository baseline | 0 |
| `b1-m1-jvm-semantics` | JVM stack/heap, allocation stress, escape analysis, GC | 1 |
| `b1-m2-persistent-structures` | Manual `List` / `Tree`, structural sharing | 2 |
| `b1-m3-stack-optimization` | `@tailrec`, control-flow elimination, graded set | 3 |
| `b1-p1-expression-engine` | Mini-Project 1: algebraic AST parser + evaluator | 4 |
| `b2-m4-algebraic-opaque-types` | ADTs, opaque types, unrepresentable invalid states | 5 |
| `b2-m5-adhoc-polymorphism` | Native typeclasses via `given` / `using` / `extension` | 6 |
| `b2-m6-category-algebras` | `Functor`, `Applicative`, `Monad`, transformers | 7–8 |
| `b2-graded-set` | Graded exercise set + monad-law property engine | 9 |
| `b2-p2-validation-state-engine` | Mini-Project 2: `Validated` + `State` transaction processor | 10 |
| `b3-m7-lazy-trampolining` | `Eval`, laziness, heap-allocated call chains | 11 |
| `b3-m8-io-runtime-loom` | `IO[A]`, fiber scheduler, virtual threads | 12–13 |
| `b3-m9-lockfree-primitives` | `Ref`, `Deferred`, lock-free coordination | 14 |
| `b3-graded-set` | Graded exercise set + async `Semaphore` | 15 |
| `b3-p3-effect-runtime-jobs` | Mini-Project 3: micro-effect runtime + job processor | 16 |
| `b4-m10-streams-backpressure` | `Stream[A]`, pull/push protocol, demand control | 17 |
| `b4-m11-resource-lifecycle` | `Resource` / `bracket`, leak-free teardown | 18 |
| `b4-m12-tagless-binary-codecs` | Tagless final algebras, reflection-free codecs | 19–20 |
| `b4-graded-set` | Graded exercise set: infinite streams, leak-proof I/O, `Match Types` codecs | 21 |
| `b4-p4-nio-http-server` | Mini-Project 4: reactive NIO HTTP server (course capstone) | 22 |

## 3. Tag Message Template

The message is the scientific abstract of the milestone. Keep it terse and
factual:

```text
<milestone title>

Delivered:
  - <artifact / abstraction implemented>
Proved:
  - <law, invariant or asymptotic bound verified by the MUnit suite>
Learned:
  - <the one non-obvious JVM or type-system insight extracted>
```

## 4. Commit Message Convention

```text
<scope>: <imperative summary>
```

Where `<scope>` is the module slug (`b1-m2`), `build`, `docs`, or `chore`.
Commit granularity follows the pedagogy, not the calendar: one commit per
*concept proven*, so that `git log --oneline` reads as a syllabus.

## 5. Branching

Work happens on `main`. This is a single-author learning repository; a branching
model here would add ceremony without adding safety. The exception is a
deliberate experiment you may want to discard wholesale (for example, comparing
a trampolined interpreter against a naive recursive one) — that earns a
throwaway branch named `spike/<topic>`.

## 6. Useful Verification Commands

```bash
git tag -n9                                   # list milestones with their abstracts
git log --oneline --decorate --graph          # syllabus view
git diff b1-m2-persistent-structures..HEAD    # what changed since a milestone
git show b1-m1-jvm-semantics                  # inspect a milestone's annotation
```
