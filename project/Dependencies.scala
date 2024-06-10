import sbt._

object Dependencies {

  val `config-tools`       = "com.evolutiongaming"        %% "config-tools"       % "1.0.4"
  val `future-helper`      = "com.evolutiongaming"        %% "future-helper"      % "1.0.6"
  val sequentially         = "com.evolutiongaming"        %% "sequentially"       % "1.1.5"
  val `akka-serialization` = "com.evolutiongaming"        %% "akka-serialization" % "1.0.4"
  val nel                  = "com.evolutiongaming"        %% "nel"                % "1.3.4"
  val `safe-actor`         = "com.evolutiongaming"        %% "safe-actor"         % "3.0.0"
  val `scala-tools`        = "com.evolutiongaming"        %% "scala-tools"        % "3.0.5"
  val scalatest            = "org.scalatest"              %% "scalatest"          % "3.2.14"
  val `scala-logging`      = "com.typesafe.scala-logging" %% "scala-logging"      % "3.9.5"

  object Akka {
    private val version = "2.6.19"
    val actor    = "com.typesafe.akka" %% "akka-actor"    % version
    val remote   = "com.typesafe.akka" %% "akka-remote"   % version
    val cluster  = "com.typesafe.akka" %% "akka-cluster"  % version
    val testkit  = "com.typesafe.akka" %% "akka-testkit"  % version
    val stream   = "com.typesafe.akka" %% "akka-stream"   % version
    val protobuf = "com.typesafe.akka" %% "akka-protobuf" % version
  }

  object AkkaTools {
    private val version = "3.0.12"
    val test = "com.evolutiongaming" %% "akka-tools-test" % version
  }

  object Scodec {
    val core = "org.scodec" %% "scodec-core" % "1.11.3"
    val bits = "org.scodec" %% "scodec-bits" % "1.1.9"
  }
}
