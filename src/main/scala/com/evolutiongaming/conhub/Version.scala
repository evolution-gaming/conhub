package com.evolutiongaming.conhub

import java.time.Instant

final case class Version(value: Long) extends Ordered[Version] {

  def inc: Version = copy(value + 1)

  def dec: Version = copy(value - 1)

  def compare(that: Version): Int = this.value compare that.value
}

object Version {

  lazy val Zero: Version = Version(0)

  def timestamp(): Version = Version(System.currentTimeMillis())

  def apply(instant: Instant): Version = Version(instant.toEpochMilli)
}
