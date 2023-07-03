package com.evolutiongaming.concurrent

import akka.actor.ActorSystem

import scala.concurrent.ExecutionContext

final case class NamedDispatcher(name: String, implicit val ec: ExecutionContext)

object NamedDispatcher {

  def apply(actorSystem: ActorSystem): NamedDispatcher = {
    NamedDispatcher("akka.actor.default-dispatcher", actorSystem.dispatcher)
  }
}
