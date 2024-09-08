import Dependencies._

name := "conhub"

organization := "com.evolutiongaming"

homepage := Some(url("https://github.com/evolution-gaming/conhub"))

startYear := Some(2018)

organizationName := "Evolution"

organizationHomepage := Some(url("https://evolution.com"))

scalaVersion := crossScalaVersions.value.head

crossScalaVersions := Seq("2.13.8", "2.12.20")

Test / fork := true

publishTo := Some(Resolver.evolutionReleases)

libraryDependencies ++= Seq(
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
  nel,
  `safe-actor`,
  `scala-tools` % Test,
  scalatest % Test)

licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT")))

releaseCrossBuild := true

Compile / doc / scalacOptions ++= Seq("-groups", "-implicits", "-no-link-warnings")

versionScheme := Some("semver-spec")

//addCommandAlias("check", "all versionPolicyCheck Compile/doc")
addCommandAlias("check", "show version")
addCommandAlias("build", "+all compile test")
