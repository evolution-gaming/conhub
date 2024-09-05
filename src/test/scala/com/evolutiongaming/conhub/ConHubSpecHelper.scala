package com.evolutiongaming.conhub

import java.util.UUID

import com.evolutiongaming.conhub.ConHubSpecHelper.{Msg, Send}
import scodec.bits.ByteVector

trait ConHubSpecHelper extends ConnTypes[Connection, ConHubSpecHelper.Id] {

  val send: Send = new Send

  val version: Version = Version.Zero

  def newLocal(connection: Connection, send: Send = send): Conn.Local[Connection, Msg] = {
    Conn.Local[Connection, Msg](connection, send, version)
  }
}

object ConHubSpecHelper {
  type Id = String
  type Msg = String

  def newId(): String = UUID.randomUUID().toString

  object ConnectionSerializer extends Serializer.Bin[Connection] {
    def to(x: Connection): ByteVector = ByteVector.encodeUtf8(x.id).fold(throw _, identity)
    def from(bytes: ByteVector): Connection = Connection(bytes.decodeUtf8.fold(throw _, identity))
  }


  class Send extends Conn.Send[Msg] {
    def apply(x: MsgAndRemote[Msg]): Unit = ()
  }
}

final case class Connection(id: String = ConHubSpecHelper.newId())