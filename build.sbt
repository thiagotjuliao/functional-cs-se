ThisBuild / scalaVersion := "3.9.0"

val munit = "org.scalameta" %% "munit" % "1.3.6" % Test

// Compiler contract for the whole curriculum.
// The compiler is the first line of proof: every latent bug it can reject
// statically is a bug that never reaches a test, a benchmark or production.
val strictWarnings = Seq(
  "-source:future", // opt into the next-generation Scala 3 semantics
  "-explain", // print the full inference/derivation trace on error
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Wall" // every warning category the compiler knows about
)

// Warnings are errors in production code only. Test sources stay permissive so
// that exploratory spikes and deliberate antipattern demonstrations compile.
//
// Both scopes are assigned inside each project rather than on ThisBuild: sbt's
// Test configuration extends Compile, and scope delegation reaches the more
// specific `<project> / Compile / scalacOptions` before it ever considers
// `ThisBuild / Test / scalacOptions`. Setting it at the build level therefore
// looks correct, is silently ignored, and leaks -Werror into the test sources.
lazy val commonSettings = Seq(
  libraryDependencies += munit,
  Compile / scalacOptions := strictWarnings :+ "-Werror",
  Test / scalacOptions := strictWarnings
)

lazy val root = (project in file("."))
  .aggregate(
    fundamentals,
    categoryTypes,
    effectConcurrency,
    distributedStreams
  )

lazy val fundamentals = (project in file("block1-fundamentals"))
  .settings(commonSettings)
  .settings(
    name := "fundamentals",
    // Allocation and JIT experiments must not observe sbt's own JVM: fork so
    // that the measurements describe the code under test and nothing else.
    Test / fork := true,
    Test / javaOptions ++= Seq("-Xmx2g", "-XX:+UseG1GC")
  )

lazy val categoryTypes = (project in file("block2-category-types"))
  .settings(commonSettings)
  .settings(name := "category-types")

lazy val effectConcurrency = (project in file("block3-effect-concurrency"))
  .settings(commonSettings)
  .settings(name := "effect-concurrency")

lazy val distributedStreams = (project in file("block4-distributed-streams"))
  .settings(commonSettings)
  .settings(name := "distributed-streams")
