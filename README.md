# ConHub
[![Build Status](https://github.com/evolution-gaming/conhub/workflows/CI/badge.svg)](https://github.com/evolution-gaming/conhub/actions?query=workflow%3ACI)
[![Coverage Status](https://coveralls.io/repos/evolution-gaming/conhub/badge.svg)](https://coveralls.io/r/evolution-gaming/conhub)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/6c727f80008a4e6f8b5519f2790a5916)](https://www.codacy.com/app/evolution-gaming/conhub?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=evolution-gaming/conhub&amp;utm_campaign=Badge_Grade)
[![version](https://api.bintray.com/packages/evolutiongaming/maven/conhub/images/download.svg)](https://bintray.com/evolutiongaming/maven/conhub/_latestVersion)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellowgreen.svg)](https://opensource.org/licenses/MIT)

ConHub is a distributed registry used to manage websocket connections on the different nodes of an appllication.
It enables you to send serializable message to one or many connections hiding away complexity of distributed system. 
In short: user provides `lookup` criteria and a `message`, there after `conHub` does the job routing message to physical instances of a matched connections

## Usage example
```scala
type Connection = ??? // type representing physical connection
final case class Msg(bytes: Array[Byte]) // serializable
final case class Envelope(lookup: LookupById, msg: Msg)
final case class LookupById(id: String)
val conHub: ConHub[String, LookupById, Connection, Envelope] = ???
conHub ! Envelope(LookupById("testId"), Msg(Array(…)))
```

## Setup

```scala
resolvers += Resolver.bintrayRepo("evolutiongaming", "maven")

libraryDependencies += "com.evolutiongaming" %% "conhub" % "1.1.1"
```
