name := "conhub"

organization := "com.evolutiongaming"

homepage := Some(new URL("http://github.com/evolution-gaming/conhub"))

startYear := Some(2018)

organizationName := "Evolution Gaming"

organizationHomepage := Some(url("http://evolutiongaming.com"))

bintrayOrganization := Some("evolutiongaming")

scalaVersion := crossScalaVersions.value.last

crossScalaVersions := Seq("2.11.12", "2.12.6")

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-deprecation",
//  "-Xfatal-warnings",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Xfuture"
)

Test / fork := true

scalacOptions in(Compile, doc) ++= Seq("-groups", "-implicits", "-no-link-warnings")

resolvers ++= Seq(
  Resolver.bintrayRepo("evolutiongaming", "maven"),
  "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster" % "2.5.16",
  "org.mockito" % "mockito-core" % "2.19.0" % Test,
  "com.evolutiongaming" %% "akka-tools-util" % "1.5.4",
  "com.evolutiongaming" %% "akka-tools-test" % "1.5.4" % Test,
  "com.evolutiongaming" %% "config-tools" % "1.0.3",
  "com.evolutiongaming" %% "future-helper" % "1.0.3",
  "com.evolutiongaming" %% "sequentially" % "1.0.12",
  "com.evolutiongaming" %% "akka-serialization" % "0.0.3",
  "com.evolutiongaming" %% "nel" % "1.3.1",
  "com.evolutiongaming" %% "safe-actor" % "1.7",
  "com.evolutiongaming" %% "scala-tools" % "2.2" % Test)

licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT")))

releaseCrossBuild := true