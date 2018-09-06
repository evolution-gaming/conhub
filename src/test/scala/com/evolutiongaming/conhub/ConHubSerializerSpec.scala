package com.evolutiongaming.conhub

import akka.actor.ExtendedActorSystem
import com.evolutiongaming.conhub.{RemoteEvent => R}
import com.evolutiongaming.nel.Nel
import com.evolutiongaming.serialization.SerializerHelper._
import com.evolutiongaming.test.ActorSpec
import org.scalatest.{FunSuite, Matchers}

import scala.concurrent.duration._

class ConHubSerializerSpec extends FunSuite with ActorSpec with Matchers {

  private val serializer = new ConHubSerializer(system.asInstanceOf[ExtendedActorSystem])

  private val version = Version.Zero

  test("toBinary & fromBinary for Event.Updated") {
    val value = "value".getBytes(Utf8)
    val expected = R.Event.Updated(R.Value("id", value, version))
    val actual = toAndFromBinaryEvent(expected)
    new String(actual.value.bytes, Utf8) shouldEqual "value"
    actual.copy(value = actual.value.copy(bytes = value)) shouldEqual expected
  }

  test("toBinary & fromBinary for Event.Removed") {
    val expected = R.Event.Removed("id", version)
    toAndFromBinaryEvent(expected) shouldEqual expected
  }

  test("toBinary & fromBinary for Event.Disconnected") {
    val expected = R.Event.Disconnected("id", 3.seconds, version)
    toAndFromBinaryEvent(expected) shouldEqual expected
  }

  test("toBinary & fromBinary for Event.Sync") {
    val values = Nel(1, 2, 3) map { x => x.toString }
    val expected = R.Event.Sync(values map { value => R.Value(value, value.getBytes(Utf8), version) })
    val actual = toAndFromBinaryEvent(expected)
    actual.values.foreach { value =>
      new String(value.bytes, Utf8) shouldEqual value.id
    }
    actual.copy(values = expected.values) shouldEqual expected
  }

  test("toBinary & fromBinary for Event.Joined") {
    toAndFromBinaryEvent(R.Event.ConHubJoined) shouldEqual R.Event.ConHubJoined
  }

  test("toBinary & fromBinary for Msgs ") {
    val msgs = Nel("msg1", "msg2")
    val remoteMsgs = RemoteMsgs(msgs.map { _.getBytes(Utf8) })
    val actual = toAndFromBinary(remoteMsgs)
    actual.values.map { new String(_, Utf8) } shouldEqual msgs
  }

  private def toAndFromBinaryEvent[T <: R.Event](event: T): T = {
    val remoteEvent = R(event)
    val deserialized = toAndFromBinary(remoteEvent)
    deserialized.event.asInstanceOf[T]
  }

  private def toAndFromBinary[T <: AnyRef](value: T): T = {
    val manifest = serializer.manifest(value)
    val bytes = serializer.toBinary(value)
    val deserialized = serializer.fromBinary(bytes, manifest)
    deserialized.asInstanceOf[T]
  }
}
