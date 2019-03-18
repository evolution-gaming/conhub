package com.evolutiongaming.conhub

import akka.actor.{ActorRefFactory, ActorSystem, Address}
import com.evolutiongaming.conhub.transport.{ReceiveMsg, SendMsg}
import com.evolutiongaming.conhub.{RemoteEvent => R}
import com.evolutiongaming.nel.Nel

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

trait SendEvent[Id, T] {

  def updated(id: Id, con: T, version: Version): Unit

  def disconnected(id: Id, timeout: FiniteDuration, version: Version): Unit

  def removed(id: Id, version: Version): Unit

  def sync(id: Id, con: T, version: Version): Unit
}

object SendEvent {

  def apply[Id, T](
    send: R.Event => Unit,
    idSerializer: Serializer.Str[Id],
    conSerializer: Serializer.Bin[T]): SendEvent[Id, T] = {

    new SendEvent[Id, T] {

      def updated(id: Id, con: T, version: Version): Unit = {
        val idStr = idSerializer.to(id)
        val conBytes = conSerializer.to(con)
        val value = R.Value(idStr, conBytes, version)
        val updated = R.Event.Updated(value)
        send(updated)
      }

      def disconnected(id: Id, timeout: FiniteDuration, version: Version): Unit = {
        val idStr = idSerializer.to(id)
        val disconnected = R.Event.Disconnected(idStr, timeout, version)
        send(disconnected)
      }

      def removed(id: Id, version: Version): Unit = {
        val removed = R.Event.Removed(idSerializer.to(id), version)
        send(removed)
      }

      def sync(id: Id, con: T, version: Version): Unit = {
        val idStr = idSerializer.to(id)
        val conBytes = conSerializer.to(con)
        val value = R.Value(idStr, conBytes, version)
        val sync = R.Event.Sync(Nel(value))
        send(sync)
      }
    }
  }

  def apply[Id, T](
    sendMsg: SendMsg[RemoteEvent],
    idSerializer: Serializer.Str[Id],
    conSerializer: Serializer.Bin[T]): SendEvent[Id, T] = {

    val send = (event: R.Event) => sendMsg(RemoteEvent(event), Nil)
    apply(send, idSerializer, conSerializer)
  }

  def apply[Id, T, M](
    name: String,
    conStates: ConStates[Id, T, M],
    reconnectTimeout: FiniteDuration,
    idSerializer: Serializer.Str[Id],
    conSerializer: Serializer.Bin[T],
    factory: ActorRefFactory,
    conhubRole: String)(implicit system: ActorSystem, ec: ExecutionContext): SendEvent[Id, T] = {

    val receive = ReceiveEvent(conStates, reconnectTimeout, idSerializer)
    val send = SendMsg(name, receive, factory, conhubRole)
    apply(send, idSerializer, conSerializer)
  }


  def empty[Id, T]: SendEvent[Id, T] = new SendEvent[Id, T] {
    def updated(id: Id, con: T, version: Version): Unit = {}
    def disconnected(id: Id, timeout: FiniteDuration, version: Version): Unit = {}
    def removed(id: Id, version: Version): Unit = {}
    def sync(id: Id, con: T, version: Version): Unit = {}
  }
}


object ReceiveEvent {

  def apply[Id, T, M](
    conStates: ConStates[Id, T, M],
    reconnectTimeout: FiniteDuration,
    idSerializer: Serializer.Str[Id])(implicit
    ec: ExecutionContext
  ): ReceiveMsg[RemoteEvent] = {

    new ReceiveMsg[RemoteEvent] with ConnTypes[T, M] {

      def connected(address: Address) = {
        val ids = for {
          id <- conStates.values.keys
        } yield {
          conStates
            .sync(id)
            .map { _ => id }
            .recover { case _ => id }
        }

        for {
          ids <- Future.sequence(ids)
          id <- conStates.values.keySet -- ids
        } {
          conStates.sync(id)
        }
      }

      def disconnected(address: Address): Unit = {
        val ctx = ConStates.Ctx.Remote(address)
        conStates.values foreach {
          case (id, c: C.Remote) if c.address == address =>
            conStates.disconnect(id, c.version, reconnectTimeout, ctx)

          case _ =>
        }
      }

      def apply(msg: R, address: Address): Unit = {
        val ctx = ConStates.Ctx.Remote(address)

        def onUpdated(value: R.Value): Unit = {
          val id = idSerializer.from(value.id)
          val _ = conStates.update(id, value.version, value.bytes, address)
        }

        def onSync(values: Nel[R.Value]): Unit = {
          for {
            value <- values
          } onUpdated(value)
        }

        def onDisconnected(event: R.Event.Disconnected): Unit = {
          val id = idSerializer.from(event.id)
          val _ = conStates.disconnect(id, event.version, event.timeout, ctx)
        }

        def onRemoved(event: R.Event.Removed): Unit = {
          val id = idSerializer.from(event.id)
          val _ = conStates.remove(id, event.version, ctx)
        }


        msg.event match {
          case event: R.Event.Updated      => onUpdated(event.value)
          case R.Event.Sync(values)        => onSync(values)
          case event: R.Event.Disconnected => onDisconnected(event)
          case event: R.Event.Removed      => onRemoved(event)
          case R.Event.ConHubJoined        =>
        }
      }
    }
  }
}
