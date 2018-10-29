package com.evolutiongaming.conhub

import akka.actor.Address
import com.evolutiongaming.concurrent.CurrentThreadExecutionContext
import com.evolutiongaming.concurrent.sequentially.Sequentially
import com.evolutiongaming.nel.Nel
import com.evolutiongaming.test.ActorSpec
import org.scalatest.WordSpec

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class ConHubSpec extends WordSpec with ActorSpec {

  "ConHub" should {

    "don't fail with NPE in case of sending a message during initialization" in new Scope {
      val connect: ConHubImpl.Connect[Void, Void, Void, Void] = onMsg => {
        onMsg(Nel(void))
        (searchEngine, conStates, sendMsgs)
      }

      conHub
    }
  }

  private trait Scope extends ActorScope {

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
      import ConStates._
      private val result = Future.successful(UpdateResult.empty[Void])
      def values: collection.Map[Void, C] = Map.empty
      def update(id: Void, local: C.Local): Result = result
      def update(id: Void, version: Version, value: Array[Byte], address: Address): Result = result
      def update(id: Void, version: Version, conn: Void, address: Address): Result = result
      def disconnect(id: Void, version: Version, timeout: FiniteDuration, ctx: Ctx = Ctx.Local): Result = result
      def remove(id: Void, version: Version, ctx: Ctx = Ctx.Local): Result = result
      def checkConsistency(id: Void): Result = result
      def sync(id: Void): Result = result
    }

    val sendMsgs  = new SendMsgs[Void, Void, Void] {
      def apply(msg: Void, con: C.Connected): Unit = {}
      def remote(msgs: Nel[Void], addresses: Iterable[Address]): Unit = {}
      def local(msg: Void, cons: Iterable[C], remote: Boolean): Unit = {}
    }

    def conHub = ConHubImpl[Void, Void, Void, Void, Void](
      system,
      Sequentially.now[Void],
      MsgOps,
      EmptyConMetrics,
      connect)(CurrentThreadExecutionContext)
  }
}