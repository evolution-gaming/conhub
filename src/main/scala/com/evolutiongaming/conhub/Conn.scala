package com.evolutiongaming.conhub

import java.time.Instant

import akka.actor.Address

import scala.concurrent.duration.FiniteDuration

/**
  * Named Conn rather than Con just for sake of avoiding Windows limitations
  * http://kizu514.com/blog/forbidden-file-names-on-windows-10/
  */
sealed trait Conn[A, +M] {
  def value: A
  def version: Version
  def isLocal: Boolean
}

object Conn {
  type Send[M] = MsgAndRemote[M] => Unit

  sealed trait Connected[A, +M] extends Conn[A, M]

  object Connected {

    def unapply[A, M](x: Conn[A, M]): Option[Connected[A, M]] = PartialFunction.condOpt(x) {
      case x: Connected[A, M] => x
    }
  }

  final case class Local[A, M](value: A, send: Send[M], version: Version) extends Connected[A, M] {

    def withConnection(value: A): Local[A, M] = copy(value = value)

    def withSend(send: Send[M]): Local[A, M] = copy(send = send)

    def isLocal: Boolean = true
  }

  final case class Remote[A](value: A, address: Address, version: Version) extends Connected[A, Nothing] {
    def isLocal: Boolean = false
  }

  final case class Disconnected[A](
    value: A,
    timeout: FiniteDuration,
    timestamp: Instant,
    version: Version,
    isLocal: Boolean = true
  ) extends Conn[A, Nothing] {

    def expired(now: Instant = Instant.now()): Boolean = {
      val duration = java.time.Duration.ofSeconds(timeout.toSeconds)
      (timestamp plus duration) isBefore now
    }
  }
}

final case class MsgAndRemote[M](msg: M, remote: Boolean = false)
