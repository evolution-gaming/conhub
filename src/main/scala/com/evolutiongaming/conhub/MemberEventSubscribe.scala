package com.evolutiongaming.conhub

import akka.actor.{Actor, ActorRefFactory, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import com.evolutiongaming.safeakka.actor.ActorLog

object MemberEventSubscribe {

  type Unsubscribe = () => Unit

  def apply(
    cluster: Cluster,
    factor: ActorRefFactory,
    onState: CurrentClusterState => Unit,
    onEvent: MemberEvent => Unit
  ): Unsubscribe = {

    def actor() = new Actor {
      lazy val log = ActorLog(context.system, MemberEventSubscribe.getClass)
      def receive = {
        case x: CurrentClusterState => onState(x)
        case x: MemberEvent         => onEvent(x)
        case x                      => log.warn(s"unexpected $x")
      }
    }

    val props = Props(actor())
    val ref   = factor.actorOf(props)
    cluster.subscribe(ref, classOf[MemberEvent])
    () => factor.stop(ref)
  }
}
