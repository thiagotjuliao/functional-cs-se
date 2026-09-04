# IntelliJ IDEA — Feedback-Loop Configuration Contract

Verified against **IntelliJ IDEA Ultimate 2026.2**, Scala plugin, sbt `1.12.13`,
Scala `3.9.0`, scalafmt `3.9.4`.

An IDE is not a text editor with colours: it is the *first* stage of the
verification pipeline. `build.sbt` already makes the compiler reject every latent
bug it can prove — `-Wall` plus `-Werror` on `Compile`. That barrier is
non-negotiable and nothing here weakens it. What this document does is move the
moment you *observe* the barrier from `sbt compile` to the keystroke, which is
the only lever the IDE actually controls.

Every item is an acceptance criterion. Tick it when set.

---

## 1. Type Visibility — Making Inference Observable

Type inference is the single mechanism you must be able to audit at all times.
An inferred type you cannot see is an assumption, not a fact.

**`Ctrl+Alt+S` → Editor → Inlay Hints → Types → Scala**

- [ ] `Method result types` — the inferred signature of every unannotated `def`
- [ ] `Local variable types` — the type of each intermediate `val`
- [ ] `Member variable types` — fields of `case class` / `trait`
- [ ] `Method chain hints` — the type at *every stage* of a combinator chain
- [ ] `Type mismatch hints` — the exact position where the types diverged

### 1.1 Chain hint thresholds

`Method chain hints` ships with two thresholds that must be lowered:

| Setting | Default | Set to | Rationale |
| :--- | :---: | :---: | :--- |
| `minimal chain length` | 3 | **2** | A two-stage `filter().map()` is where inference most often silently widens |
| `minimal unique types` | 2 | **1** | Suppresses chains whose type never changes — precisely the `Functor` / `Monad` case, where the *shape* is the whole point |

In `xs.filter(p).map(f).foldLeft(z)(g)`, the interesting fact is what each stage
returns. Hidden, that chain is a single opaque expression; annotated, it is a
four-step derivation you can check by eye.

### 1.2 On-demand inspection

Inlay hints report the type of a *line*. `Ctrl+Shift+P` (*Expression Type*)
reports the type of any **subexpression**, and pressing it repeatedly walks up
the expression tree. Keep both: the hints are ambient, the shortcut is surgical.

---

## 2. Compiler-Based Highlighting — Non-Optional in Scala 3

**Languages & Frameworks → Scala → Highlighting**

- [ ] `Use compiler for error highlighting`

The Scala plugin carries its own type checker. It is a *reimplementation*, and it
diverges from dotty exactly where Scala 3 is most novel: `given` / `using`
resolution, `extension` methods, match types, `inline`, and type lambdas — which
is to say, the entire subject matter of Block 2 and beyond.

With this flag on, the authority that marks errors is the real compiler. False
positives go to zero, and the `-Wall -Werror` contract surfaces in the editor
exactly as it will surface in the build. The cost is roughly half a second of
latency before highlights settle. Pay it.

---

## 3. Formatting — Delegate to scalafmt

**Editor → Code Style → Scala → `Formatter` tab**

- [ ] Formatter: **`Scalafmt`**
- [ ] Configuration: `.scalafmt.conf`
- [ ] `Reformat on file save`

The repository already defines a formatting normal form: `.scalafmt.conf` pins
`rewrite.scala3.removeOptionalBraces`, `align.preset = none`, and
`docstrings.style = SpaceAsterisk`. Until the formatter is switched, IntelliJ
ignores that file entirely and every `Ctrl+Alt+L` applies its own defaults,
silently undoing the contract.

`align.preset = none` exists so that a diff never touches a line whose meaning
did not change. A competing formatter defeats that guarantee on the first save.

---

## 4. Documentation Rendering

**Editor → General → Appearance**

- [ ] `Render documentation comments`

Scaladoc then opens already rendered — formatted HTML, resolved `@param` tags,
highlighted code blocks — instead of requiring a per-file click on the gutter
icon. `Ctrl+Alt+Q` toggles a single comment back to source for editing.

**Editor → Natural Languages → Grazie**

- [ ] English enabled, checking `Comments` and `Documentation`

The project mandates English documentation. This enforces it mechanically.

---

## 5. Build Loop

**Build, Execution, Deployment → Build Tools → sbt**

- [ ] `Use sbt shell for: builds`
- [ ] (optional) `Use sbt shell for: imports`

Compiling through the sbt shell reuses an already-warm JVM together with Zinc's
incremental state. Against a cold Scala Compile Server on a Scala 3 project with
`-explain` enabled, that is several seconds per cycle.

### 5.1 The real feedback loop is not a button

```bash
sbt "~fundamentals/testQuick"
```

Recompiles and re-runs only the affected MUnit tests on every save. Leave it
running in a terminal tab for the duration of a module. No IDE run configuration
is faster, because none of them skip unaffected tests.

---

## 6. Context Parameters — Seeing the Injected `given`

**Languages & Frameworks → Scala → Editor**

- [ ] `Show implicit hints` (toggle: `Ctrl+Alt+Shift++`)

Renders *which* `given` instance the compiler selected for each `using` clause.
Ambiguity and unintended-instance bugs in typeclass derivation are invisible in
the source and obvious here. From Block 2 onward this stops being a convenience
and becomes the primary debugging instrument for implicit resolution.

---

## 7. Memory

Scala 3 compilation is allocation-heavy, and `inline` / `given` resolution
recurses deeply.

- [ ] `Help → Change Memory Settings` → **4096 MB**
- [ ] `Languages & Frameworks → Scala → Compile Server`:
      - JVM maximum heap: **2048 MB** or more
      - JVM parameters include **`-Xss4m`**

Deep `inline` expansion and long given-derivation chains overflow the compiler's
default thread stack. `-Xss4m` is cheaper than diagnosing the resulting
`StackOverflowError` as if it were a logic bug.

---

## 8. Reading Indentation-Based Syntax

`.scalafmt.conf` removes optional braces, which removes the visual delimiters of
every block. Restore that information by other means.

**Editor → General → Appearance**

- [ ] `Show indent guides`
- [ ] `Highlight selected indent guide`
- [ ] `Show method separators`
- [ ] Sticky lines enabled (2026.2 pins the enclosing scope to the top of the
      editor — worth substantially more without braces than it was with them)

**Editor → Font**

- [ ] JetBrains Mono with `Enable ligatures`

`=>`, `<-`, `?=>`, `>=`, `<:` collapse into single glyphs. In dense combinator
code the reduction in token noise is measurable.

---

## 9. Shortcuts That Replace Configuration

| Shortcut | Action | Use |
| :--- | :--- | :--- |
| `Ctrl+Shift+P` | Expression Type | Type of any subexpression; repeat to widen |
| `Ctrl+W` / `Ctrl+Shift+W` | Extend / Shrink Selection | Syntactic selection — essential without braces |
| `Alt+Enter` | Intentions | On an unannotated `def`: *Add type annotation* promotes the inferred hint to real source |
| `Ctrl+Alt+V` | Extract Variable | Extracts with the inferred type already applied |
| `Ctrl+Alt+Shift++` | Expand Implicit Hints | Show resolved `given` instances |
| `Ctrl+Alt+Q` | Toggle Rendered View | Edit a single rendered Scaladoc comment |

---

## 10. Scope Note

`.idea/` is git-ignored (see `.gitignore`), and the settings above are a mix of
per-project and per-IDE state. None of it is version-controlled. This document
*is* the configuration record — if the IDE is reinstalled or the project
re-imported, replay the checklist from here.
