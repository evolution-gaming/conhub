import Dependencies._

name := "conhub"

organization := "com.evolutiongaming"

homepage := Some(new URL("http://github.com/evolution-gaming/conhub"))

startYear := Some(2018)

organizationName := "Evolution"

organizationHomepage := Some(url("http://evolution.com"))

scalaVersion := crossScalaVersions.value.head

crossScalaVersions := Seq("2.13.8", "2.12.17")

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