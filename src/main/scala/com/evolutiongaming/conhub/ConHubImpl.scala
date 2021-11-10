package com.evolutiongaming.conhub

import java.util.concurrent.atomic.AtomicBoolean

import com.evolutiongaming.concurrent.sequentially.Sequentially
import com.evolutiongaming.nel.Nel
import com.evolutiongaming.concurrent.FutureHelper._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}


object ConHubImpl extends LazyLogging {

  type OnMsgs[M] = Nel[M] => Unit

  type Connect[Id, T, L, M] = OnMsgs[M] => (SearchEngine[Id, T, M, L], ConStates[Id, T, M], SendMsgs[Id, T, M])

  def apply[Id, A, L, K, M](
    sequentially: Sequentially[K],
    msgOps: ConHubImpl.MsgOps[M, L, K],
    metrics: ConMetrics[Id, A, M],
    connect: Connect[Id, A, L, M])(implicit
    ec: ExecutionContext): ConHub[Id, A, M, L] = {

    new ConHub[Id, A, M, L] {

      private val initialized = new AtomicBoolean(false)

      val onMsgs: OnMsgs[M] = msgs => {

        if (initialized.get()) {
          val msgsAndCons = for {
            msg <- msgs.toList
            cons = this.cons(msg.lookup, localCall = false)
            if cons.nonEmpty
          } yield (msg, cons)

          for {
            msgsAndCons <- Nel.opt(msgsAndCons)
          } sendManyToLocal(msgsAndCons, remote = true)
        }
      }

      val (searchEngine, conStates, sendMsgs) = connect(onMsgs)

      initialized.set(true)

      metrics.registerGauges(cons)

      def !(msg: M): SR = {
        logAndMeter(msg)

        def execute = {
          val lookup = msg.lookup
          val cons = this.cons(lookup)

          if (cons.nonEmpty) {
            sendMsgs.local(msg, cons, remote = false)
            val addresses = this.addresses(cons)
            if (addresses.nonEmpty) sendMsgs.remote(Nel(msg), addresses)
          }
          SendResult(cons)
        }

        msg.key match {
          case None      => execute.future
          case Some(key) => sequentially(key) { execute }
        }
      }

      def !(msgs: Nel[M]): SR = {
        for {msg <- msgs} logAndMeter(msg)

        val msgsAndCons = for {
          msg <- msgs.toList
          cons = this.cons(msg.lookup)
          if cons.nonEmpty
        } yield (msg, cons)

        val future = Nel.opt(msgsAndCons) match {
          case Some(msgsAndCons) => sendManyToLocal(msgsAndCons, remote = false)
          case None              => Future.unit
        }

        val remoteMsgs = for {
          (msg, cons) <- msgsAndCons if addresses(cons).nonEmpty
        } yield msg

        for {remoteMsgs <- Nel.opt(remoteMsgs)} sendMsgs.remote(remoteMsgs, Nil)

        future map { _ => SendResult(cons) }
      }


      def update(id: Id, version: Version, con: A, send: Conn.Send[M]): Result = {
        conStates.update(id, Conn.Local(con, send, version))
      }

      def disconnect(id: Id, version: Version, reconnectTimeout: FiniteDuration): Result = {
        conStates.disconnect(id, version, reconnectTimeout)
      }

      def remove(id: Id, version: Version): Result = {
        conStates.remove(id, version)
      }

      private def addresses(cons: Iterable[C]) = {
        cons collect { case c: C.Remote => c.address }
      }

      private def sendManyToLocal(msgsAndConnections: Nel[(M, Iterable[Conn[A, M]])], remote: Boolean) = {
        Future.traverseSequentially(msgsAndConnections.toList) { case (msg, connections) =>
          msg.key match {
            case None => sendMsgs.local(msg, connections, remote).future
            case Some(key) => sequentially(key) { sendMsgs.local(msg, connections, remote) }
          }
        }
      }

      private implicit class MsgOpsProxy(self: M) {
        def key: Option[K] = msgOps key self
        def lookup: L = msgOps lookup self
      }

      private def logAndMeter(msg: M) = {
        logger.debug(s"<<< ${ msg.toString take 1000 }")
        metrics.onTell(msg)
      }
    }
  }


  trait MsgOps[A, L, K] {
    def lookup(x: A): L
    def key(x: A): Option[K]
  }
}
