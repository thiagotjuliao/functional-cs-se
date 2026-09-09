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

**Build, Execution, Deployment → Build Tools → sbt → `sbt shell`**

- [ ] `Use sbt shell for: builds` — **off**
- [ ] (optional) `Use sbt shell for: imports`

The obvious reading — that compiling through the sbt shell reuses an
already-warm JVM together with Zinc's incremental state — holds only for a
workflow that presses *Build*. This project does not have one: §5.2 is the loop,
and it is an `sbt` process of its own in a terminal.

Turning the option on therefore puts **two sbt instances on the same build**,
each carrying its own Zinc analysis, both writing `target/scala-3.9.0/classes`.
They invalidate each other's incremental state, and the full recompilations that
follow read like an IDE defect rather than a configuration conflict. Off, the
Build action goes through the already-warm Compile Server (§7) and the terminal
owns `target/` alone.

What that costs, stated plainly: the Compile Server does not read `build.sbt`.
It compiles with the profiles in `.idea/scala_compiler.xml`, translated from the
build at import time. That translation is currently faithful — `-source:future
-explain -Wall` on both scopes, `-Werror` on `Compile` only, matching
`commonSettings` — but it is a *snapshot*. Change a flag in `build.sbt` and the
editor keeps using the old one until the build is re-imported.

### 5.1 Auto-build

**Build, Execution, Deployment → Compiler**

- [ ] `Build project automatically` — **off**

With §2 in force the editor's errors come from the Compile Server directly, and
the authoritative signal comes from §5.2. A background build triggered on every
pause in typing therefore produces a result nothing consults, while contending
for the same files the compiler and the antivirus scanner (§8.1) are already
touching.

### 5.2 The real feedback loop is not a button

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
      - JDK: **21** (`Use default SDK` unchecked — see §7.1)
      - JVM maximum heap: **4096 MB**
      - JVM parameters:
        `-Xss4m -XX:ReservedCodeCacheSize=384m -XX:MaxInlineLevel=20 -XX:+UseParallelGC`

| Flag | Why |
| :--- | :--- |
| `-Xss4m` | Deep `inline` expansion and long given-derivation chains overflow the default thread stack. Cheaper than diagnosing the resulting `StackOverflowError` as if it were a logic bug. |
| `-XX:ReservedCodeCacheSize=384m` | The compiler is a long-lived JIT target. A full code cache silently disables JIT compilation, and the server degrades to interpreted speed with no error. |
| `-XX:MaxInlineLevel=20` | dotty's hot paths are deep chains of small methods. The default of 9 stops inlining before the chain bottoms out. |
| `-XX:+UseParallelGC` | The Compile Server is throughput-bound and nobody observes its pause times. G1 spends cycles on a guarantee that has no consumer here. |

### 7.1 Compile Server JDK vs project SDK

These are two different settings and only one of them affects compilation
speed. `.idea/misc.xml` records the *project* SDK — it governs indexing of the
JDK's own classes and resolution in the editor. The Compile Server runs on the
JDK named above, independently.

Pin the Compile Server to an LTS. IntelliJ ships prebuilt *shared indexes* for
LTS releases only; on a non-LTS JDK the IDE indexes several thousand JDK classes
locally on every cache invalidation.

---

## 8. Performance — Latency That Buys Nothing

Sections 1, 2 and 6 purchase observability *with* latency, deliberately:
compiler-based highlighting runs real dotty behind every keystroke, and chain
hints at threshold 2/1 re-infer every stage of every visible combinator chain.
That expense is the contract and this section does not touch it. What follows
removes the latency that buys nothing.

State the baseline first, because it rules out the usual explanation:

```text
Scala sources         28 files / 2,919 lines
build output          5 MB across all target/ directories
machine               64 GB RAM / 32 cores
IntelliJ modules      19  (5 subprojects x main/test, plus root and root-build)
```

Nothing at this size is slow because of volume. Every item below is
configuration.

### 8.1 Antivirus — the largest single item on Windows

Every `.class`, `.tasty` and coursier jar the build writes passes through
Defender's real-time scanner before the compiler reads it back. Exclude the
build's working set. `Add-MpPreference` requires an **elevated** PowerShell, and
`Get-MpPreference` will not even display the current exclusions without it.

```powershell
@(
  '<absolute path to this repository>'
  "$HOME\.ivy2"
  "$HOME\.sbt"
  "$env:LOCALAPPDATA\Coursier"
  "$env:LOCALAPPDATA\JetBrains"
) | ForEach-Object { Add-MpPreference -ExclusionPath $_ }
```

Note the double quotes on the four cache paths. `Add-MpPreference` performs no
expansion of its own: single-quoted `'~\.ivy2'` is registered verbatim, creating
an exclusion for a directory named `~` that protects nothing and reports no
error. Verify with `Get-MpPreference | Select-Object -ExpandProperty
ExclusionPath` — the list must contain absolute paths.

- [ ] Repository directory excluded
- [ ] `$HOME\.ivy2`, `$HOME\.sbt` and the coursier cache excluded
- [ ] IntelliJ caches (`$env:LOCALAPPDATA\JetBrains`) excluded

The trade-off is explicit rather than accidental: dependency jars fetched by
coursier stop being scanned as they are written. That is a decision about a
build cache, taken knowingly.

### 8.2 Metals artefacts must not be indexed

This workspace is opened by two language servers — IntelliJ and Metals (see
`.vscode/settings.json`). Metals leaves `.bloop/` behind: one JSON file per
build target, each carrying a fully expanded classpath, currently 283 files.
sbt's import teaches IntelliJ to ignore `target/`; nothing teaches it what
`.bloop` is, so it indexes all of it, and re-indexes after every `bloopInstall`
the other editor triggers.

**Right-click → Mark Directory as → Excluded**, or
**Editor → File Types → Ignored Files and Folders**:

- [ ] `.bloop` excluded
- [ ] `.metals` excluded
- [ ] `.bsp` excluded

### 8.3 The Bloop daemon outlives its editor

Bloop's server is a daemon: closing VS Code does not stop it. It stays resident
holding a file watcher over the same tree IntelliJ is watching and the scanner
is scanning — a third observer of every write, with no client attached.

```powershell
Get-Process java | Where-Object { $_.Path -and $_.WorkingSet64 -gt 100MB }
```

- [ ] When working in IntelliJ, no orphaned Bloop server resident

Stopping it is safe: Metals restarts it on demand.

### 8.4 What will not go away

With §2 enabled, two type engines run per keystroke: the Scala plugin's own PSI
— which is what powers navigation, refactoring and the hints of §1 — and dotty
itself, which is what marks the errors. A Metals-based editor runs one, its
presentation compiler over SemanticDB.

That difference is structural, not a misconfiguration, and it is the correct
trade for this curriculum: from Block 2 onward the subject matter is precisely
where a reimplemented type checker diverges from the real one. The latency is
the price of the errors being true.

---

## 9. Reading Indentation-Based Syntax

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

## 10. Shortcuts That Replace Configuration

| Shortcut | Action | Use |
| :--- | :--- | :--- |
| `Ctrl+Shift+P` | Expression Type | Type of any subexpression; repeat to widen |
| `Ctrl+W` / `Ctrl+Shift+W` | Extend / Shrink Selection | Syntactic selection — essential without braces |
| `Alt+Enter` | Intentions | On an unannotated `def`: *Add type annotation* promotes the inferred hint to real source |
| `Ctrl+Alt+V` | Extract Variable | Extracts with the inferred type already applied |
| `Ctrl+Alt+Shift++` | Expand Implicit Hints | Show resolved `given` instances |
| `Ctrl+Alt+Q` | Toggle Rendered View | Edit a single rendered Scaladoc comment |

---

## 11. Scope Note

`.idea/` is git-ignored (see `.gitignore`), and the settings above are a mix of
per-project and per-IDE state. None of it is version-controlled. This document
*is* the configuration record — if the IDE is reinstalled or the project
re-imported, replay the checklist from here.
