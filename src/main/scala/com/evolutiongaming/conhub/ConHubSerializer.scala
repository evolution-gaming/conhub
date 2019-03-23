package com.evolutiongaming.conhub

import java.io.NotSerializableException
import java.lang.{Byte => ByteJ}

import akka.serialization.SerializerWithStringManifest
import com.evolutiongaming.conhub.{RemoteEvent => R}
import com.evolutiongaming.nel.Nel
import scodec.bits.{BitVector, ByteVector}
import scodec.{Attempt, Codec, DecodeResult, Err, SizeBound, codecs}

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
      case x: RemoteEvent => eventToBinary(x).require.toArray
      case x: RemoteMsgs  => msgsToBinary(x).require.toByteArray
      case _              => illegalArgument(s"Cannot serialize message of ${ x.getClass } in ${ getClass.getName }")
    }
  }

  def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    manifest match {
      case EventManifest => eventFromBinary(BitVector.view(bytes))
      case MsgsManifest  => msgsFromBinary(BitVector.view(bytes))
      case _             => notSerializable(s"Cannot deserialize message for manifest $manifest in ${ getClass.getName }")
    }
  }
}

object ConHubSerializer {

  private val codecBytes: Codec[ByteVector] = codecCustom(codecs.bytes)

  def codecCustom[A](codec: Codec[A], sizeCodec: Codec[Int] = codecs.int32): Codec[A] = new Codec[A] {

    def decode(bits: BitVector) = {
      for {
        result <- sizeCodec.decode(bits)
        size    = result.value
        _      <- Attempt.guard(size >= 0, Err(s"requires positive size, got $size"))
        bits    = result.remainder
        (bits1, remainder) = bits.splitAt(size * ByteJ.SIZE.toLong)
        result <- codec.decode(bits1)
      } yield {
        DecodeResult(result.value, remainder)
      }
    }

    def encode(value: A) = {
      for {
        bits     <- codec.encode(value)
        size      = bits.size.toInt / ByteJ.SIZE
        sizeBits <- sizeCodec.encode(size)
      } yield {
        sizeBits ++ bits
      }
    }

    val sizeBound = SizeBound.atLeast(ByteJ.SIZE.toLong)
  }

  private def codecsNel[A](codec: Codec[A]): Codec[Nel[A]] = {
    val codec1 = codecCustom(codec)
    codecs.listOfN(codecs.int32, codec1).xmap[Nel[A]](Nel.unsafe, _.toList)
  }

  private val codecMsgs = codecsNel(codecBytes).as[RemoteMsgs]

  private val codecVersion = codecs.int64.as[Version]

  private val codecFiniteDuration = codecs.int64.xmap[FiniteDuration](_.millis, _.toMillis)

  private val codecValue = (codecs.utf8_32 :: codecBytes :: codecVersion).as[R.Value]

  private val codecUpdated = codecValue.as[R.Event.Updated]

  private val codecRemoved = (codecs.utf8_32 :: codecVersion).as[R.Event.Removed]

  private val codecDisconnected = (codecs.utf8_32 :: codecFiniteDuration :: codecVersion).as[R.Event.Disconnected]

  private val codecSync = codecsNel(codecValue).as[RemoteEvent.Event.Sync]

  private def notSerializable(msg: String) = throw new NotSerializableException(msg)

  private def illegalArgument(msg: String) = throw new IllegalArgumentException(msg)


  private def eventFromBinary(bits: BitVector) = {
    val result = for {
      result <- codecs.int32.decode(bits)
      bits    = result.remainder
      result <- result.value match {
        case 0 => codecUpdated.decode(bits)
        case 1 => codecRemoved.decode(bits)
        case 2 => codecDisconnected.decode(bits)
        case 3 => codecSync.decode(bits)
        case 4 => Attempt.successful(DecodeResult(R.Event.ConHubJoined, bits))
        case x => notSerializable(s"Cannot deserialize event for id $x in ${ getClass.getName }")
      }
    } yield {
      RemoteEvent(result.value)
    }
    result.require
  }

  private def eventToBinary(x: RemoteEvent) = {

    def withMark(mark: Int, bits: Attempt[BitVector]) = {
      for {
        bits <- bits
      } yield {
        val markBits = BitVector.fromInt(mark)
        (markBits ++ bits).bytes
      }
    }

    x.event match {
      case a: R.Event.Updated      => withMark(0, codecUpdated.encode(a))
      case a: R.Event.Removed      => withMark(1, codecRemoved.encode(a))
      case a: R.Event.Disconnected => withMark(2, codecDisconnected.encode(a))
      case a: R.Event.Sync         => withMark(3, codecSync.encode(a))
      case R.Event.ConHubJoined    => withMark(4, Attempt.successful(BitVector.empty))
    }
  }

  private def msgsToBinary(a: RemoteMsgs) = {
    codecMsgs.encode(a)
  }

  private def msgsFromBinary(bits: BitVector) = {
    codecMsgs.decode(bits).require.value
  }
}