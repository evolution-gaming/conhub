package com.evolutiongaming.conhub

import com.evolutiongaming.conhub.UpdateResult.NotUpdated.Reason

sealed trait UpdateResult[+C]

object UpdateResult {

  final case class Updated[C](previousValue: Option[C]) extends UpdateResult[C]

  final case class NotUpdated[C](reason: Reason[C]) extends UpdateResult[C]

  object NotUpdated {
    sealed trait Reason[+C]

    object Reason {
      final case object VersionConflict extends Reason[Nothing]

      final case object SameValue extends Reason[Nothing]

      final case object UpdateNotRequiredByMethod extends Reason[Nothing]

      final case class UpdateNotDefinedForValue[C](previousValue: Option[C]) extends Reason[C]

      final case class WrongContext[C](context: String, connection: C) extends Reason[C]
    }
  }
}
