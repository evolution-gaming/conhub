package com.evolutiongaming.conhub

import com.evolutiongaming.concurrent.CurrentThreadExecutionContext
import com.evolutiongaming.concurrent.sequentially.Sequentially
import com.evolutiongaming.nel.Nel
import com.evolutiongaming.test.ActorSpec
import org.scalatest.mockito.MockitoSugar
import org.scalatest.WordSpec

class ConHubSpec extends WordSpec with ActorSpec with MockitoSugar {

  "ConHub" should {

    "don't fail with NPE in case of sending a message during initialization" in new Scope {
      conHub
    }
  }

  private trait Scope extends ActorScope {

    case class Void()
    val void = Void()

    object MsgOps extends ConHubImpl.MsgOps[Void, Void, Void] {
      def lookup(x: Void): Void = x
      def key(x: Void): Option[Void] = Some(x)
    }

    object EmptyConMetrics extends ConMetrics[Void, Void, Void] {
      def onTell(m: Void): Unit = {}
      def registerGauges(cons: => Iterable[Conn[Void, Void]]): Unit = {}
      def onChanged(diff: ConStates.Diff[Void, Conn[Void, Void]]): Unit = {}
    }

    val nelConnect: ConHubImpl.Connect[Void, Void, Void, Void] = onMsg => {
      onMsg(Nel(void))
      (mock[SearchEngine[Void, Void, Void, Void]], mock[ConStates[Void, Void, Void]], mock[SendMsgs[Void, Void, Void]])
    }

    val conHub = ConHubImpl[Void, Void, Void, Void, Void](
      system,
      Sequentially.now[Void],
      MsgOps,
      EmptyConMetrics,
      nelConnect)(CurrentThreadExecutionContext)
  }
}