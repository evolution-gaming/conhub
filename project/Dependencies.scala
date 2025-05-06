import sbt.*

object Dependencies {

  val `config-tools`       = "com.evolutiongaming"        %% "config-tools"       % "1.0.5"
  val `future-helper`      = "com.evolutiongaming"        %% "future-helper"      % "1.0.7"
  val sequentially         = "com.evolutiongaming"        %% "sequentially"       % "1.2.0"
  val `akka-serialization` = "com.evolutiongaming"        %% "akka-serialization" % "1.1.0"
  val nel                  = "com.evolutiongaming"        %% "nel"                % "1.3.5"
  val `safe-actor`         = "com.evolutiongaming"        %% "safe-actor"         % "3.1.0"
  val `scala-tools`        = "com.evolutiongaming"        %% "scala-tools"        % "3.0.6"
  val scalatest            = "org.scalatest"              %% "scalatest"          % "3.2.19"
  val `scala-logging`      = "com.typesafe.scala-logging" %% "scala-logging"      % "3.9.5"
  val `scodec-bits`        = "org.scodec"                 %% "scodec-bits"        % "1.2.1"
  val `scodec-core1`       = "org.scodec"                 %% "scodec-core"        % "1.11.10"
  val `scodec-core2`       = "org.scodec"                 %% "scodec-core"        % "2.3.2"

  object Akka {
    private val version = "2.6.21"
    val actor    = "com.typesafe.akka" %% "akka-actor"    % version
    val remote   = "com.typesafe.akka" %% "akka-remote"   % version
    val cluster  = "com.typesafe.akka" %% "akka-cluster"  % version
    val testkit  = "com.typesafe.akka" %% "akka-testkit"  % version
    val stream   = "com.typesafe.akka" %% "akka-stream"   % version
    val protobuf = "com.typesafe.akka" %% "akka-protobuf" % version
  }

  object AkkaTools {
    private val version = "3.0.13"
    val test = "com.evolutiongaming" %% "akka-tools-test" % version
  }
}
