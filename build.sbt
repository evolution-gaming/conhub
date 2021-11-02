import Dependencies._

name := "conhub"

organization := "com.evolutiongaming"

homepage := Some(new URL("http://github.com/evolution-gaming/conhub"))

startYear := Some(2018)

organizationName := "Evolution Gaming"

organizationHomepage := Some(url("http://evolutiongaming.com"))

bintrayOrganization := Some("evolutiongaming")

scalaVersion := crossScalaVersions.value.head

crossScalaVersions := Seq("2.13.7", "2.12.11")

Test / fork := true

resolvers += Resolver.bintrayRepo("evolutiongaming", "maven")

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

scalacOptions in(Compile, doc) ++= Seq("-groups", "-implicits", "-no-link-warnings")