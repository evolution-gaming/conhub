import Dependencies.*

name := "conhub"

organization := "com.evolutiongaming"

homepage := Some(url("https://github.com/evolution-gaming/conhub"))

startYear := Some(2018)

organizationName := "Evolution"

organizationHomepage := Some(url("https://evolution.com"))

scalaVersion := crossScalaVersions.value.head

crossScalaVersions := Seq("2.13.18", "3.3.3")

Compile / scalacOptions ++= {
  if (scalaBinaryVersion.value == "2.13") {
    Seq(
      "-Xsource:3"
    )
  } else Seq.empty
}

Test / fork := true

publishTo := Some(Resolver.evolutionReleases)

libraryDependencies ++= Seq(
  `scodec-bits`,
  Akka.actor,
  Akka.remote,
  Akka.cluster,
  Akka.testkit % Test,
  Akka.stream,
  Akka.protobuf,
  AkkaTools.test % Test,
  `config-tools`,
  `future-helper`,
  sequentially,
  `scala-logging`,
  `akka-serialization`,
  cats,
  `scala-tools` % Test,
  scalatest % Test)

libraryDependencies ++= {
  if (scalaBinaryVersion.value == "2.13") {
    Seq(
      `scodec-core1`,
    )
  } else {
    Seq(
      `scodec-core2`,
    )
  }
}

licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT")))

Compile / doc / scalacOptions ++= Seq("-groups", "-implicits", "-no-link-warnings")

versionScheme := Some("semver-spec")

addCommandAlias("check", "+all versionPolicyCheck Compile/doc")
addCommandAlias("build", "+all test package")

// Your next release will be binary compatible with the previous one,
// but it may not be source compatible (ie, it will be a minor release).
ThisBuild / versionPolicyIntention := Compatibility.BinaryCompatible

/*
versionPolicyReportDependencyIssues ignored dependencies when compared to conhub 1.3.0.
All of those should not affect the library users, binary compatibility should be preserved.

Remember to clear up after 1.3.1 release!
 */
ThisBuild / versionPolicyIgnored ++= Seq(
  /*
  Examples:

  //com.chuusai:shapeless_2.13: missing dependency
  "com.chuusai" %% "shapeless",
  //org.scala-lang.modules:scala-java8-compat_2.13:
  //  incompatible version change from 0.9.0 to 1.0.0 (compatibility: early semantic versioning)
  "org.scala-lang.modules" %% "scala-java8-compat",
   */
)
