package com.evolutiongaming.conhub

/**
  *
  * @param updated whether action did update the value
  * @param value   value before the update if it was updated or current value otherwise
  */
final case class UpdateResult[+A](updated: Boolean = false, value: Option[A] = None)

object UpdateResult {

  private val Empty = UpdateResult()

  private val Created = UpdateResult(updated = true)


  def empty[A]: UpdateResult[A] = Empty

  def created[A]: UpdateResult[A] = Created

  def apply[A](updated: Boolean, value: A): UpdateResult[A] = UpdateResult(updated, Some(value))

  def apply[A](value: A): UpdateResult[A] = UpdateResult(value = Some(value))
}