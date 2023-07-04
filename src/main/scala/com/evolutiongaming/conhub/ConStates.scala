package com.evolutiongaming.conhub

import java.time.Instant
import akka.actor.{Address, Scheduler}
import com.evolutiongaming.concurrent.sequentially.{MapDirective, SequentialMap}
import com.evolutiongaming.conhub.SequentialMapHelper._
import com.evolutiongaming.conhub.UpdateResult.NotUpdated
import com.evolutiongaming.conhub.UpdateResult.NotUpdated.Reason
import com.typesafe.scalalogging.LazyLogging
import scodec.bits.ByteVector

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

trait ConStates[Id, A, M] extends ConnTypes[A, M] {
  type Result = Future[UpdateResult[C]]

  def values: collection.Map[Id, C]

  def update(id: Id, local: C.Local): Result

  def update(id: Id, version: Version, value: ByteVector, address: Address): Result

  def update(id: Id, version: Version, conn: A, address: Address): Result

  def disconnect(id: Id, version: Version, timeout: FiniteDuration, ctx: ConStates.Ctx = ConStates.Ctx.Local): Result

  def remove(id: Id, version: Version, ctx: ConStates.Ctx = ConStates.Ctx.Local): Result

  def checkConsistency(id: Id): Result

  def sync(id: Id): Result
}


object ConStates {

  type Connect[Id, A, M] = ConStates[Id, A, M] => SendEvent[Id, A]

  def apply[Id, A, M](
    states: SequentialMap[Id, Conn[A, M]],
    checkConsistencyInterval: FiniteDuration,
    scheduler: Scheduler,
    conSerializer: Serializer.Bin[A],
    onChanged: Diff[Id, Conn[A, M]] => Future[Unit],
    now: () => Instant,
    connect: Connect[Id, A, M])(implicit
    ec: ExecutionContext
  ): ConStates[Id, A, M] = {

    val conStates = apply(states, scheduler, conSerializer, onChanged, now, connect)

    scheduler.scheduleWithFixedDelay(checkConsistencyInterval, checkConsistencyInterval) {
      () => for {id <- states.values.keys} conStates.checkConsistency(id)
    }

    conStates
  }

  def apply[Id, A, M](
    states: SequentialMap[Id, Conn[A, M]],
    scheduler: Scheduler,
    conSerializer: Serializer.Bin[A],
    onChanged: Diff[Id, Conn[A, M]] => Future[Unit],
    now: () => Instant,
    connect: Connect[Id, A, M])(implicit ec: ExecutionContext): ConStates[Id, A, M] = {

    new ConStates[Id, A, M] with LazyLogging {

      private val send: SendEvent[Id, A] = connect(this)

      def values = states.values

      def update(id: Id, con: C.Local): Result = {
        updatePf(id, Some(con.version), "update") { case before =>
          update(id, con, before, local = true)
        }
      }

      def update(id: Id, version: Version, value: ByteVector, address: Address): Result = {
        updatePf(id, Some(version), s"update $address") { case before =>
          val con = conSerializer.from(value)
          update(id, Conn.Remote(con, address, version), before, local = false)
        }
      }

      def update(id: Id, version: Version, con: A, address: Address): Result = {
        updatePf(id, Some(version), s"update $address") { case before =>
          update(id, Conn.Remote(con, address, version), before, local = false)
        }
      }

      def disconnect(id: Id, version: Version, timeout: FiniteDuration, ctx: Ctx): Result = {
        updatePf(id, Some(version), s"disconnect $ctx") { case Some(c) =>

          def disconnect(local: Boolean): UpdateStrategy[C] = {
            val timestamp = now()
            val disconnected = Conn.Disconnected(c.value, timeout, timestamp, version, local)
            UpdateStrategy.update(disconnected) {
              if (local) send.disconnected(id, timeout, version)

              val timeoutFinal = if (local) timeout else (timeout * 1.2).asInstanceOf[FiniteDuration]

              val _ = scheduler.scheduleOnce(timeoutFinal) {
                val _ = updatePf(id, Some(version), "timeout") { case Some(before: C.Disconnected) if before.timestamp == timestamp =>
                  remove(id, version, local = local)
                }
              }
            }
          }

          (ctx, c) match {
            case (Ctx.Local, _: C.Local)                                    => disconnect(local = true)
            case (ctx: Ctx.Remote, c: C.Remote) if c.address == ctx.address => disconnect(local = false)
            case _                                                          => UpdateStrategy.ignore(Reason.WrongContext(ctx.toString, c))
          }
        }
      }

      def checkConsistency(id: Id): Result = {
        updatePf(id, None, "checkConsistency") { case Some(before: C.Disconnected) if before.expired(now()) =>
          remove(id, before.version, local = true)
        }
      }

      def remove(id: Id, version: Version, ctx: Ctx): Result = {
        updatePf(id, Some(version), s"remove $ctx") { case Some(c) =>

          def remove(local: Boolean) = this.remove(id, version, local)

          (ctx, c) match {
            case (Ctx.Local, _: C.Local)                                    => remove(local = true)
            case (ctx: Ctx.Remote, c: C.Remote) if c.address == ctx.address => remove(local = false)
            case (_, _: C.Disconnected)                                     => remove(local = ctx == Ctx.Local)
            case _                                                          => UpdateStrategy.ignore(Reason.WrongContext(ctx.toString, c))
          }
        }
      }

      def sync(id: Id) = {
        updatePf(id, None, "sync") { case Some(c: C.Local) =>
          send.sync(id, c.value, c.version)
          UpdateStrategy.ignore(Reason.UpdateNotRequiredByMethod)
        }
      }

      private def updatePf(id: Id, version: Option[Version], name: => String)(pf: PartialFunction[Option[C], UpdateStrategy[C]]): Result = {

        val future = states.updateAndRun(id) { before =>
          val strategy = {
            val older = version.exists { version =>
              before.exists { _.version > version }
            }

            if (older) UpdateStrategy.ignore(Reason.VersionConflict)
            else pf.applyOrElse(before, (c: Option[C]) => UpdateStrategy.ignore(Reason.UpdateNotDefinedForValue(c)))
          }

          val run = () => {

            def run(after: Option[C], callback: () => Unit): (UpdateResult[C], Future[Unit]) = {
              if (before != after) {
                callback()
                val diff = Diff(id, before = before, after = after)
                val future = try onChanged(diff) catch {
                  case NonFatal(x) => Future.failed(x)
                }

                future.onComplete {
                  case Success(_)     =>
                  case Failure(error) => logger.error(s"onChanged failed for $id $error", error)
                }
                (UpdateResult.Updated(before), future)
              } else {
                (UpdateResult.NotUpdated(Reason.SameValue), Future.unit)
              }
            }

            strategy match {
              case UpdateStrategy.Update(newValue, callback) => run(Some(newValue), callback)
              case UpdateStrategy.Remove(callback)           => run(None, callback)
              case UpdateStrategy.Ignore(reason)             => (UpdateResult.NotUpdated(reason), Future.unit)
            }
          }

          logger.debug(s"$id $name $before ${strategy.directive}")

          (strategy.directive, run)
        }

        future.onComplete {
          case Success(_)     =>
          case Failure(error) => logger.error(s"connection $id update failed $error", error)
        }

        for {
          (result, future) <- future
          _ <- future
        } yield result
      }

      private def update(id: Id, con: C, before: Option[C], local: Boolean) = {
        UpdateStrategy.update(con) {
          if (local && !before.contains(con)) {
            send.updated(id, con.value, con.version)
          }
        }
      }

      private def remove(id: Id, version: Version, local: Boolean) =
        UpdateStrategy.remove {
          if (local) send.removed(id, version)
        }
    }
  }

  private[conhub] sealed trait UpdateStrategy[+C] {
    val directive: MapDirective[C]
  }

  private[conhub] object UpdateStrategy {
    final case class Update[C](newValue: C, callback: () => Unit) extends UpdateStrategy[C] {
      override val directive = MapDirective.update(newValue)
    }

    final case class Ignore[C](reason: NotUpdated.Reason[C]) extends UpdateStrategy[C] {
      override val directive = MapDirective.ignore
    }

    final case class Remove(callback: () => Unit) extends UpdateStrategy[Nothing] {
      override val directive = MapDirective.remove
    }

    def update[C](newValue: C)(callback: => Unit = ()) = Update(newValue, () => callback)

    def remove(callback: => Unit) = Remove(() => callback)

    def ignore[C](reason: NotUpdated.Reason[C]) = Ignore(reason)
  }

  final case class Diff[Id, +A](id: Id, before: Option[A], after: Option[A])


  sealed trait Ctx

  object Ctx {
    case object Local extends Ctx
    final case class Remote(address: Address) extends Ctx
  }
}