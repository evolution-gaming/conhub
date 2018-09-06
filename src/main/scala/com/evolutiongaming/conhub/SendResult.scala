package com.evolutiongaming.conhub

import scala.concurrent.Future

final case class SendResult[+T](cons: Iterable[T])

object SendResult {

  private val Empty = SendResult(Nil)

  private val EmptyFuture = Future.successful(Empty)
  

  def empty[T]: SendResult[T] = Empty

  def emptyFuture[T]: Future[SendResult[T]] = EmptyFuture
}
