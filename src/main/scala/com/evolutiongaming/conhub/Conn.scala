package com.evolutiongaming.conhub

import java.time.Instant

import akka.actor.Address

import scala.concurrent.duration.FiniteDuration

/**
  * Named Conn rather than Con just for sake of avoiding Windows limitations
  * http://kizu514.com/blog/forbidden-file-names-on-windows-10/
  */
sealed trait Conn[T, +M] {
  def value: T
  def version: Version
  def isLocal: Boolean
}

object Conn {
  type Send[M] = MsgAndRemote[M] => Unit

  sealed trait Connected[T, +M] extends Conn[T, M]

  object Connected {

    def unapply[T, M](x: Conn[T, M]): Option[Connected[T, M]] = PartialFunction.condOpt(x) {
      case x: Connected[T, M] => x
    }
  }

  final case class Local[T, M](value: T, send: Send[M], version: Version) extends Connected[T, M] {

    def withConnection(value: T): Local[T, M] = copy(value = value)

    def withSend(send: Send[M]): Local[T, M] = copy(send = send)

    def isLocal: Boolean = true
  }

  final case class Remote[T](value: T, address: Address, version: Version) extends Connected[T, Nothing] {
    def isLocal: Boolean = false
  }

  final case class Disconnected[T](
    value: T,
    timeout: FiniteDuration,
    timestamp: Instant,
    version: Version,
    isLocal: Boolean = true) extends Conn[T, Nothing] {

    def expired(now: Instant = Instant.now()): Boolean = {
      val duration = java.time.Duration.ofSeconds(timeout.toSeconds)
      (timestamp plus duration) isBefore now
    }
  }
}

final case class MsgAndRemote[M](msg: M, remote: Boolean = false)