package com.evolutiongaming.conhub

import akka.actor.Address
import com.evolutiongaming.concurrent.CurrentThreadExecutionContext
import com.evolutiongaming.concurrent.sequentially.Sequentially
import com.evolutiongaming.concurrent.FutureHelper._
import com.evolutiongaming.nel.Nel
import org.scalatest.WordSpec
import scodec.bits.ByteVector

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class ConHubSpec extends WordSpec {

  "ConHub" should {

    "don't fail with NPE in case of sending a message during initialization" in new Scope {
      val connect: ConHubImpl.Connect[Void, Void, Void, Void] = onMsg => {
        onMsg(Nel(void))
        (searchEngine, conStates, sendMsgs)
      }

      conHub
    }
  }

  private trait Scope {

    case class Void()
    val void = Void()

    def connect: ConHubImpl.Connect[Void, Void, Void, Void]

    object MsgOps extends ConHubImpl.MsgOps[Void, Void, Void] {
      def lookup(x: Void): Void = x
      def key(x: Void): Option[Void] = Some(x)
    }

    object EmptyConMetrics extends ConMetrics[Void, Void, Void] {
      def onTell(m: Void): Unit = {}
      def registerGauges(cons: => Iterable[Conn[Void, Void]]): Unit = {}
      def onChanged(diff: ConStates.Diff[Void, Conn[Void, Void]]): Unit = {}
    }

    val searchEngine = new SearchEngine[Void, Void, Void, Void] {
      def apply(l: Void, cons: Cons, localCall: Boolean): Iterable[C] = Iterable.empty
      def update(diff: ConStates.Diff[Void, C], cons: Cons): Future[Unit] = Future.successful({})
    }

    val conStates = new ConStates[Void, Void, Void] {
      private val result = UpdateResult.empty[Void].future
      def values: collection.Map[Void, C] = Map.empty
      def update(id: Void, local: C.Local): Result = result
      def update(id: Void, version: Version, value: ByteVector, address: Address): Result = result
      def update(id: Void, version: Version, conn: Void, address: Address): Result = result
      def disconnect(id: Void, version: Version, timeout: FiniteDuration, ctx: ConStates.Ctx = ConStates.Ctx.Local): Result = result
      def remove(id: Void, version: Version, ctx: ConStates.Ctx = ConStates.Ctx.Local): Result = result
      def checkConsistency(id: Void): Result = result
      def sync(id: Void): Result = result
    }

    val sendMsgs  = new SendMsgs[Void, Void, Void] {
      def apply(msg: Void, con: C.Connected): Unit = {}
      def remote(msgs: Nel[Void], addresses: Iterable[Address]): Unit = {}
      def local(msg: Void, cons: Iterable[C], remote: Boolean): Unit = {}
    }

    def conHub = ConHubImpl[Void, Void, Void, Void, Void](
      Sequentially.now[Void],
      MsgOps,
      EmptyConMetrics,
      connect)(CurrentThreadExecutionContext)
  }
}