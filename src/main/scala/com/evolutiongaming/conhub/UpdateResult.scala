package com.evolutiongaming.conhub

/**
  *
  * @param updated whether action did update the value
  * @param value   value before the update if it was updated or current value otherwise
  */
final case class UpdateResult[+T](updated: Boolean = false, value: Option[T] = None)

object UpdateResult {

  private val Empty = UpdateResult()

  private val Created = UpdateResult(updated = true)


  def empty[T]: UpdateResult[T] = Empty

  def created[T]: UpdateResult[T] = Created

  def apply[T](updated: Boolean, value: T): UpdateResult[T] = UpdateResult(updated, Some(value))

  def apply[T](value: T): UpdateResult[T] = UpdateResult(value = Some(value))
}