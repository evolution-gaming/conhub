package com.evolutiongaming.conhub

import java.time.Instant

import akka.actor.Address
import com.evolutiongaming.concurrent.sequentially.{MapDirective, SequentialMap}
import com.evolutiongaming.concurrent.FutureHelper._
import com.evolutiongaming.conhub.SequentialMapHelper._
import com.evolutiongaming.util.Scheduler
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait ConStates[Id, T, M] extends ConnTypes[T, M] {
  import ConStates._

  type Result = Future[UpdateResult[T]]

  def values: collection.Map[Id, C]

  def update(id: Id, local: C.Local): Result

  def update(id: Id, version: Version, value: Array[Byte], address: Address): Result

  def update(id: Id, version: Version, conn: T, address: Address): Result

  def disconnect(id: Id, version: Version, timeout: FiniteDuration, ctx: Ctx = Ctx.Local): Result

  def remove(id: Id, version: Version, ctx: Ctx = Ctx.Local): Result

  def checkConsistency(id: Id): Result

  def sync(id: Id): Result
}


object ConStates {

  type Connect[Id, T, M] = ConStates[Id, T, M] => SendEvent[Id, T]


  def apply[Id, T, M](
    states: SequentialMap[Id, Conn[T, M]],
    checkConsistencyInterval: FiniteDuration,
    scheduler: Scheduler,
    conSerializer: Serializer.Bin[T],
    onChanged: Diff[Id, Conn[T, M]] => Future[Unit],
    now: () => Instant,
    connect: Connect[Id, T, M])(implicit ec: ExecutionContext): ConStates[Id, T, M] = {

    val conStates = apply(states, scheduler, conSerializer, onChanged, now, connect)

    scheduler.schedule(checkConsistencyInterval, checkConsistencyInterval) {
      for {id <- states.values.keys} conStates.checkConsistency(id)
    }

    conStates
  }

  def apply[Id, T, M](
    states: SequentialMap[Id, Conn[T, M]],
    scheduler: Scheduler,
    conSerializer: Serializer.Bin[T],
    onChanged: Diff[Id, Conn[T, M]] => Future[Unit],
    now: () => Instant,
    connect: Connect[Id, T, M])(implicit ec: ExecutionContext): ConStates[Id, T, M] = {

    new ConStates[Id, T, M] with LazyLogging {

      private val send: SendEvent[Id, T] = connect(this)

      def values = states.values

      def update(id: Id, con: C.Local): Result = {
        updatePf(id, Some(con.version), "update") { case before =>
          update(id, con, before, local = true)
        }
      }

      def update(id: Id, version: Version, value: Array[Byte], address: Address): Result = {
        updatePf(id, Some(version), s"update $address") { case before =>
          val con = conSerializer.from(value)
          update(id, Conn.Remote(con, address, version), before, local = false)
        }
      }

      def update(id: Id, version: Version, con: T, address: Address): Result = {
        updatePf(id, Some(version), s"update $address") { case before =>
          update(id, Conn.Remote(con, address, version), before, local = false)
        }
      }

      def disconnect(id: Id, version: Version, timeout: FiniteDuration, ctx: Ctx): Result = {
        updatePf(id, Some(version), s"disconnect $ctx") { case Some(c) =>

          def disconnect(local: Boolean): R = {
            val timestamp = now()
            val disconnected = Conn.Disconnected(c.value, timeout, timestamp, version, local)
            R.update(disconnected) {
              if (local) send.disconnected(id, timeout, version)

              val timeoutFinal = if (local) timeout else (timeout * 1.2).asInstanceOf[FiniteDuration]

              scheduler.scheduleOnce(timeoutFinal, runOnShutdown = false) {
                updatePf(id, Some(version), "timeout") { case Some(before: C.Disconnected) if before.timestamp == timestamp =>
                  remove(id, version, local = local)
                }
              }
            }
          }

          (ctx, c) match {
            case (Ctx.Local, _: C.Local)                                    => disconnect(local = true)
            case (ctx: Ctx.Remote, c: C.Remote) if c.address == ctx.address => disconnect(local = false)
            case _                                                          => R.Ignore
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
            case _                                                          => R.Ignore
          }
        }
      }

      def sync(id: Id) = {
        updatePf(id, None, "sync") { case Some(c: C.Local) =>
          send.sync(id, c.value, c.version)
          R.Ignore
        }
      }

      private def updatePf(id: Id, version: Option[Version], name: => String)(pf: PartialFunction[Option[C], R]): Result = {

        val future = states.updateAndRun(id) { before =>
          val R(directive, callback) = {
            val older = version.exists { version =>
              before.exists { _.version > version }
            }

            if (older) R.Ignore
            else pf.applyOrElse(before, (_: Option[C]) => R.Ignore)
          }

          val run = () => {

            def result(updated: Boolean, future: Future[Unit]) = {
              val updateResult = UpdateResult(updated, before map { _.value })
              (updateResult, future)
            }

            def run(after: Option[C]) = {
              if (before != after) {
                callback()
                val diff = Diff(id, before = before, after = after)
                val future = try onChanged(diff) catch {
                  case NonFatal(x) => Future.failed(x)
                }
                future.failed.foreach { failure =>
                  logger.error(s"onChanged failed for $id $failure", failure)
                }
                result(true, future)
              } else {
                result(false, Future.unit)
              }
            }

            directive match {
              case MapDirective.Update(after) => run(Some(after))
              case MapDirective.Remove        => run(None)
              case MapDirective.Ignore        => result(false, Future.unit)
            }
          }

          logger.debug(s"$id $name $before $directive")

          (directive, run)
        }

        future.failed.foreach { failure =>
          logger.error(s"connection $id update failed $failure", failure)
        }

        for {
          (result, future) <- future
          _ <- future
        } yield result
      }

      private def update(id: Id, con: C, before: Option[C], local: Boolean) = {
        R.update(con) {
          if (local && !before.contains(con)) {
            send.updated(id, con.value, con.version)
          }
        }
      }

      private def remove(id: Id, version: Version, local: Boolean): R = {
        R.remove {
          if (local) send.removed(id, version)
        }
      }

      case class R(directive: MapDirective[C], callback: () => Unit = () => ())

      object R {
        lazy val Ignore: R = R(MapDirective.ignore)

        def update(value: C)(callback: => Unit = ()): R = {
          R(MapDirective.Update(value), () => callback)
        }

        def remove(callback: => Unit): R = R(MapDirective.remove, () => callback)
      }
    }
  }

  final case class Diff[Id, +T](id: Id, before: Option[T], after: Option[T])


  sealed trait Ctx

  object Ctx {
    case object Local extends Ctx
    final case class Remote(address: Address) extends Ctx
  }
}