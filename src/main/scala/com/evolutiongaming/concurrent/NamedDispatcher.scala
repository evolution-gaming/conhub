package com.evolutiongaming.concurrent

import akka.actor.ActorSystem

import scala.concurrent.ExecutionContext

@deprecated(message = "roll out your own NamedDispatcher, this one will be removed", since = "1.3.0")
final case class NamedDispatcher(name: String, ec: ExecutionContext)

@deprecated(message = "roll out your own NamedDispatcher, this one will be removed", since = "1.3.0")
object NamedDispatcher {

  def apply(actorSystem: ActorSystem): NamedDispatcher = {
    NamedDispatcher("akka.actor.default-dispatcher", actorSystem.dispatcher)
  }
}