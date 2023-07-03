package com.evolutiongaming.conhub

import scala.concurrent.Future

final case class SendResult[+A](cons: Iterable[A])

object SendResult {

  private val Empty = SendResult(Nil)

  private val EmptyFuture = Future.successful(Empty)

  def empty[A]: SendResult[A] = Empty

  def emptyFuture[A]: Future[SendResult[A]] = EmptyFuture
}
