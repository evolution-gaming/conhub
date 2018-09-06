package com.evolutiongaming.conhub

import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

import com.evolutiongaming.conhub.ConHubSpecHelper.{Msg, Send}

trait ConHubSpecHelper extends ConnTypes[Connection, ConHubSpecHelper.Id] {

  val send: Send = new Send

  val version = Version.Zero

  def newLocal(connection: Connection, send: Send = send) = {
    Conn.Local[Connection, Msg](connection, send, version)
  }
}

object ConHubSpecHelper {
  type Id = String
  type Msg = String

  def newId(): String = UUID.randomUUID().toString

  object ConnectionSerializer extends Serializer.Bin[Connection] {
    def to(x: Connection): Array[Byte] = x.id getBytes UTF_8
    def from(bytes: Array[Byte]): Connection = Connection(new String(bytes, UTF_8))
  }


  class Send extends Conn.Send[Msg] {
    def apply(x: MsgAndRemote[Msg]): Unit = ()
  }
}

final case class Connection(id: String = ConHubSpecHelper.newId())