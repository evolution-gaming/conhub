package com.evolutiongaming.conhub

import java.io.NotSerializableException
import java.lang.{Integer => IntJ, Long => LongJ}
import java.nio.ByteBuffer

import akka.serialization.SerializerWithStringManifest
import com.evolutiongaming.conhub.{RemoteEvent => R}
import com.evolutiongaming.nel.Nel
import com.evolutiongaming.serialization.SerializerHelper._

import scala.concurrent.duration._

class ConHubSerializer extends SerializerWithStringManifest {
  import ConHubSerializer._

  private val EventManifest = "A"
  private val MsgsManifest = "C"

  def identifier: Int = 1869692879

  def manifest(x: AnyRef): String = {
    x match {
      case _: RemoteEvent => EventManifest
      case _: RemoteMsgs  => MsgsManifest
      case _              => illegalArgument(s"Cannot serialize message of ${ x.getClass } in ${ getClass.getName }")
    }
  }

  def toBinary(x: AnyRef): Array[Byte] = {
    x match {
      case x: RemoteEvent => eventToBinary(x)
      case x: RemoteMsgs  => msgsNewToBinary(x)
      case _              => illegalArgument(s"Cannot serialize message of ${ x.getClass } in ${ getClass.getName }")
    }
  }

  def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    manifest match {
      case EventManifest => eventFromBinary(bytes)
      case MsgsManifest  => msgsNewFromBinary(bytes)
      case _             => notSerializable(s"Cannot deserialize message for manifest $manifest in ${ getClass.getName }")
    }
  }

  private def eventFromBinary(bytes: Array[Byte]) = {

    val buffer = ByteBuffer.wrap(bytes)

    def valueFromBinary(buffer: ByteBuffer) = {
      val id = buffer.readString
      val value = buffer.readBytes
      val version = buffer.readVersion
      R.Value(id, value, version)
    }

    def removedFromBinary = {
      val id = buffer.readString
      val version = buffer.readVersion
      R.Event.Removed(id, version)
    }

    def disconnectedFromBinary = {
      val id = buffer.readString
      val timeout = buffer.getLong.millis
      val version = buffer.readVersion
      R.Event.Disconnected(id, timeout, version)
    }

    def syncFromBinary = {
      val values = buffer.readNel {
        valueFromBinary(buffer)
      }
      R.Event.Sync(values)
    }

    val event: R.Event = buffer.getInt match {
      case 0 => R.Event.Updated(valueFromBinary(buffer))
      case 1 => removedFromBinary
      case 2 => disconnectedFromBinary
      case 3 => syncFromBinary
      case 4 => R.Event.ConHubJoined
      case x => notSerializable(s"Cannot deserialize event for id $x in ${ getClass.getName }")
    }

    RemoteEvent(event)
  }

  private def eventToBinary(x: RemoteEvent) = {

    def updatedToBinary(x: R.Event.Updated) = {
      val idBytes = x.value.id.getBytes(Utf8)
      val bytes = x.value.bytes
      val buffer = ByteBuffer.allocate(IntJ.BYTES + IntJ.BYTES + idBytes.length + IntJ.BYTES + bytes.length + LongJ.BYTES)
      buffer.putInt(0)
      buffer.writeBytes(idBytes)
      buffer.writeBytes(bytes)
      buffer.putLong(x.value.version.value)
      buffer
    }

    def removedToBinary(x: R.Event.Removed) = {
      val idBytes = x.id.getBytes(Utf8)
      val buffer = ByteBuffer.allocate(IntJ.BYTES + IntJ.BYTES + idBytes.length + LongJ.BYTES)
      buffer.putInt(1)
      buffer.writeBytes(idBytes)
      buffer.writeVersion(x.version)
      buffer
    }

    def disconnectedToBinary(x: R.Event.Disconnected) = {
      val idBytes = x.id.getBytes(Utf8)
      val buffer = ByteBuffer.allocate(IntJ.BYTES + IntJ.BYTES + idBytes.length + LongJ.BYTES + LongJ.BYTES)
      buffer.putInt(2)
      buffer.writeBytes(idBytes)
      buffer.putLong(x.timeout.toMillis)
      buffer.writeVersion(x.version)
      buffer
    }

    def syncToBinary(x: RemoteEvent.Event.Sync) = {
      val bytes = x.values map { value =>
        val idBytes = value.id.getBytes(Utf8)
        val bytes = value.bytes
        val buffer = ByteBuffer.allocate(IntJ.BYTES + idBytes.length + IntJ.BYTES + bytes.length + LongJ.BYTES)
        buffer.writeBytes(idBytes)
        buffer.writeBytes(bytes)
        buffer.writeVersion(value.version)
        buffer.array()
      }

      val length = bytes.foldLeft(0) { case (length, bytes) => length + IntJ.BYTES + bytes.length }
      val buffer = ByteBuffer.allocate(IntJ.BYTES + IntJ.BYTES + length)
      buffer.putInt(3)
      buffer.writeNel(bytes)
      buffer
    }

    def joinedToBinary = {
      val buffer = ByteBuffer.allocate(4)
      buffer.putInt(4)
      buffer
    }

    val buffer: ByteBuffer = x.event match {
      case x: R.Event.Updated      => updatedToBinary(x)
      case x: R.Event.Removed      => removedToBinary(x)
      case x: R.Event.Disconnected => disconnectedToBinary(x)
      case x: R.Event.Sync         => syncToBinary(x)
      case R.Event.ConHubJoined    => joinedToBinary
    }

    buffer.array()
  }

  private def msgsNewToBinary(x: RemoteMsgs) = {
    val bytes = x.values.map { bytes =>
      val buffer = ByteBuffer.allocate(IntJ.BYTES + bytes.length)
      buffer.writeBytes(bytes)
      buffer.array()
    }
    val length = bytes.foldLeft(0) { (length, bytes) =>
      length + IntJ.BYTES + bytes.length
    }
    val buffer = ByteBuffer.allocate(IntJ.BYTES + length)
    buffer.writeNel(bytes)
    buffer.array()
  }

  private def msgsNewFromBinary(bytes: Bytes) = {
    val buffer = ByteBuffer.wrap(bytes)
    val msgs = buffer.readNel {
      buffer.readBytes
    }
    RemoteMsgs(msgs)
  }

  private def notSerializable(msg: String) = throw new NotSerializableException(msg)

  private def illegalArgument(msg: String) = throw new IllegalArgumentException(msg)
}

object ConHubSerializer {

  implicit class ByteBufferOpsConHub(val self: ByteBuffer) extends AnyVal {

    def readNel[T](f: => T): Nel[T] = {
      val length = self.getInt()
      val list = List.fill(length) {
        val length = self.getInt
        val position = self.position()
        val value = f
        self.position(position + length)
        value
      }
      Nel.unsafe(list)
    }

    def writeNel(bytes: Nel[Array[Byte]]): Unit = {
      self.putInt(bytes.length)
      bytes.foreach { bytes => self.writeBytes(bytes) }
    }

    def writeVersion(version: Version): Unit = {
      val _ = self.putLong(version.value)
    }

    def readVersion: Version = Version(self.getLong)
  }
}