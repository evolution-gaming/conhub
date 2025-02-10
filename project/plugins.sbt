addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.2.0")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.3.13")

// This sets the 'version' property based on the git tag during release process to publish the right version
addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.1.0")

addSbtPlugin("com.evolution" % "sbt-scalac-opts-plugin" % "0.0.9")

addSbtPlugin("com.evolution" % "sbt-artifactory-plugin" % "0.0.2")

addSbtPlugin("ch.epfl.scala" % "sbt-version-policy" % "3.2.1")