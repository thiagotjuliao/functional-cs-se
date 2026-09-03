ThisBuild / scalaVersion := "3.9.0"

val munit = "org.scalameta" %% "munit" % "1.3.6" % Test

// Compiler contract for the whole curriculum.
// The compiler is the first line of proof: every latent bug it can reject
// statically is a bug that never reaches a test, a benchmark or production.
ThisBuild / scalacOptions ++= Seq(
  "-source:future",   // opt into the next-generation Scala 3 semantics
  "-explain",         // print the full inference/derivation trace on error
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Wall"             // every warning category the compiler knows about
)

// Warnings are errors in production code only. Test sources stay permissive so
// that exploratory spikes and deliberate anti-pattern demonstrations compile.
ThisBuild / Compile / scalacOptions += "-Werror"

lazy val root = (project in file("."))
  .aggregate(
    fundamentals,
    categoryTypes,
    effectConcurrency,
    distributedStreams
  )

lazy val fundamentals = (project in file("block1-fundamentals"))
  .settings(
    name := "fundamentals",
    libraryDependencies += munit
  )

lazy val categoryTypes = (project in file("block2-category-types"))
  .settings(
    name :=  "category-types",
    libraryDependencies += munit
  )

lazy val effectConcurrency = (project in file("block3-effect-concurrency"))
  .settings(
    name := "effect-concurrency",
    libraryDependencies += munit
  )

lazy val distributedStreams = (project in file("block4-distributed-streams"))
  .settings(
    name := "distributed-streams",
    libraryDependencies += munit
  )
