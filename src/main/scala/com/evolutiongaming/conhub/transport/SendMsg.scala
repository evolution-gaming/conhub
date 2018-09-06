package com.evolutiongaming.conhub.transport

import akka.actor._
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, Member, MemberStatus}
import com.evolutiongaming.safeakka.actor.ActorLog

import scala.compat.Platform
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.control.NonFatal

trait SendMsg[-T] {

  def apply(msg: T, addresses: Iterable[Address]): Unit

  def map[TT](f: TT => T): SendMsg[TT] = SendMsg(this, f)
}

object SendMsg {

  val RetryInterval: FiniteDuration = 300.millis

  sealed trait Tell[T] {
    def apply(msg: T, to: ActorRef, from: ActorRef): Unit = to.tell(msg, from)
    def apply(msg: T, to: ActorSelection, from: ActorRef): Unit = to.tell(msg, from)
  }

  object Tell {
    implicit case object IdentifyTell extends Tell[Identify]
    implicit case object ActorIdentityTell extends Tell[ActorIdentity]
  }


  def apply[T](
    name: String,
    receive: ReceiveMsg[T],
    factory: ActorRefFactory,
    retryInterval: FiniteDuration = RetryInterval)(implicit
    tag: ClassTag[T],
    system: ActorSystem): SendMsg[T] = {

    if (system.hasExtension(Cluster)) {
      val cluster = Cluster(system)
      val log = ActorLog(system, classOf[SendMsg[_]]) prefixed name
      apply(name, receive, factory, retryInterval, cluster, log)
    } else {
      empty
    }
  }

  private def apply[T](
    name: String,
    receive: ReceiveMsg[T],
    factory: ActorRefFactory,
    retryInterval: FiniteDuration,
    cluster: Cluster,
    log: ActorLog)(implicit tag: ClassTag[T], system: ActorSystem): SendMsg[T] = {

    final case class Retry(address: Address)


    sealed trait Channel

    object Channel {

      final case class Connecting(id: Long) extends Channel

      final case class Connected(to: ActorRef, from: ActorRef) extends Channel {

        def apply[M](msg: M)(implicit tell: Tell[M]): Unit = tell(msg, to = to, from = from)

        override def toString: String = s"$productPrefix(${ to.path.address })"
      }
    }


    implicit val tell = new Tell[T] {}

    var state = Map.empty[Address, Channel]


    def safe(msg: => String)(f: => Unit): Unit = {
      try f catch {
        case NonFatal(failure) => log.error(s"$msg: $failure", failure)
      }
    }

    def disconnect(address: Address): Unit = {
      if (state contains address) {
        log.debug(s"onDisconnected $address")
        state = state - address
        safe(s"disconnected failed for $address") {
          receive.disconnected(address)
        }
      }
    }

    def onMsg(msg: T, address: Address): Unit = {
      if (address.hasGlobalScope) {
        log.debug(s"receive $msg from $address")
        safe(s"receive failed for $msg from $address") {
          receive(msg, address)
        }
      } else {
        log.warn(s"receive unexpected $msg from $address")
      }
    }

    def connected(address: Address, channel: Channel.Connected): Unit = {
      safe(s"connected failed for $address") {
        receive.connected(address)
      }
    }

    def actor() = new Actor {

      val scheduler = context.system.scheduler
      implicit val ec = context.dispatcher

      def identify(address: Address, id: Long): Unit = {
        log.debug(s"identify $address $id")
        val identify = Identify(id)
        self.tell(address, identify)
      }

      def connect(ref: ActorRef): Unit = {
        val address = ref.path.address

        def onConnected(): Unit = {
          log.debug(s"onConnected $address")
          val channel = Channel.Connected(to = ref, from = self)
          state = state + (address -> channel)
          context.watch(ref)
          val identity = ActorIdentity("ready", Some(self))
          channel(identity)
          connected(address, channel)
        }

        state.get(address) match {
          case Some(_: Channel.Connecting) => onConnected()
          case Some(_: Channel.Connected)  => log.debug(s"already connected to $address")
          case None                        =>
            log.warn(s"cannot find channel for $address")
            onConnected()
        }
      }

      def onMemberEvent(event: MemberEvent): Unit = {

        def onMemberUp(address: Address): Unit = {
          log.debug(s"receive MemberUp for $address")
          if (address != cluster.selfAddress && !(state contains address)) {
            val id = Platform.currentTime
            identify(address, id)
            val channel = Channel.Connecting(id)
            state = state + (address -> channel)
          }
        }

        def onMemberRemoved(address: Address): Unit = {
          log.debug(s"receive MemberRemoved from $address")
          disconnect(address)
        }

        event match {
          case event: MemberUp       => onMemberUp(event.member.address)
          case event: MemberWeaklyUp => onMemberUp(event.member.address)
          case event: MemberRemoved  => onMemberRemoved(event.member.address)
          case _                     =>
        }
      }

      def onClusterState(clusterState: CurrentClusterState): Unit = {
        val addresses = clusterState.addresses
        log.debug(s"receive CurrentClusterState ${ addresses mkString "," }")
        val now = Platform.currentTime
        val result = for {
          (address, idx) <- addresses.zipWithIndex
          if address != cluster.selfAddress
        } yield {
          val id = now + idx
          (address, Channel.Connecting(id))
        }

        state = result.toMap

        result.foreach { case (address, channel) => identify(address, channel.id) }
      }

      def onActorIdentity(id: Long, ref: Option[ActorRef]): Unit = {
        val address = ref map { _.path.address }
        log.debug(s"receive ActorIdentity $id from $address")
        ref match {
          case Some(ref) => connect(ref)
          case None      =>
            val address = state.collectFirst { case (address, Channel.Connecting(`id`)) => address }
            address match {
              case Some(address) =>
                log.debug(s"retrying in $retryInterval")
                scheduler.scheduleOnce(retryInterval, self, Retry(address))

              case None =>
                log.warn(s"cannot find address for $id")
            }
        }
      }

      def onRetry(address: Address): Unit = {
        log.debug(s"receive Retry $address")
        state.get(address) match {
          case Some(c: Channel.Connecting) => identify(address, c.id)
          case Some(_: Channel.Connected)  => log.debug(s"already connected to $address")
          case None                        => log.warn(s"cannot find io for $address")
        }
      }

      def onReady(ref: ActorRef): Unit = {
        val address = ref.path.address
        log.debug(s"receive Ready from $address")
        connect(ref)
      }

      def onTerminated(address: Address): Unit = {
        log.debug(s"receive Terminated from $address")
        disconnect(address)
      }

      def receive = {
        case x: MemberEvent                    => onMemberEvent(x)
        case x: CurrentClusterState            => onClusterState(x)
        case ActorIdentity(id: Long, ref)      => onActorIdentity(id, ref)
        case ActorIdentity("ready", Some(ref)) => onReady(ref)
        case Terminated(ref)                   => onTerminated(ref.path.address)
        case Retry(address)                    => onRetry(address)
        case tag(x)                            => onMsg(x, sender().path.address)
        case x                                 => log.warn(s"receive unexpected $x from ${ sender() }")
      }
    }

    val props = Props(actor())
    val ref = factory.actorOf(props, name)
    cluster.subscribe(ref, classOf[MemberEvent])

    new SendMsg[T] {

      def apply(msg: T, addresses: Iterable[Address]): Unit = {

        def broadcast() = {
          log.debug(s"broadcast $msg")
          for {
            (address, channel) <- state
          } channel match {
            case channel: Channel.Connected => channel(msg)
            case _                          => ref.tell(address, msg)
          }
        }

        def send() = {
          log.debug(s"send $msg to ${ addresses mkString "," }")
          for {
            address <- addresses
          } state.get(address) match {
            case Some(channel: Channel.Connected) => channel(msg)
            case _                                => ref.tell(address, msg)
          }
        }

        if (addresses.isEmpty) broadcast() else send()
      }
    }
  }

  def apply[A, B](sendMsg: SendMsg[A], f: B => A): SendMsg[B] = {
    new SendMsg[B] {
      def apply(msg: B, addresses: Iterable[Address]): Unit = {
        sendMsg(f(msg), addresses)
      }
    }
  }


  private val Empty = new SendMsg[Any] {
    def apply(msg: Any, addresses: Iterable[Address]): Unit = {}
  }

  def empty[T]: SendMsg[T] = Empty


  implicit class StatusOps(val self: CurrentClusterState) extends AnyVal {

    def addresses: Set[Address] = {

      def up(member: Member) = {
        val status = member.status
        status == MemberStatus.Up || status == MemberStatus.WeaklyUp
      }

      self.members.collect { case member if up(member) => member.address }
    }
  }


  implicit class ActorRefOps(val self: ActorRef) extends AnyVal {

    def path(address: Address): ActorPath = {
      val relative = self.path.toStringWithoutAddress
      val absolute = s"$address/$relative"
      ActorPath.fromString(absolute)
    }

    def remote(address: Address)(implicit system: ActorSystem): ActorSelection = {
      val remote = path(address)
      system.actorSelection(remote)
    }

    def tell[T](address: Address, msg: T)(implicit system: ActorSystem, tell: Tell[T]): Unit = {
      val remote = self.remote(address)
      tell(msg, to = remote, from = self)
    }
  }
}


trait ReceiveMsg[-T] {

  def connected(address: Address): Unit

  def disconnected(address: Address): Unit

  def apply(msg: T, address: Address): Unit

  def map[TT](f: TT => T): ReceiveMsg[TT] = ReceiveMsg(this, f)
}

object ReceiveMsg {

  private val Empty = new ReceiveMsg[Any] {
    def connected(address: Address): Unit = {}
    def disconnected(address: Address): Unit = {}
    def apply(msg: Any, address: Address): Unit = {}
  }

  def empty[T]: ReceiveMsg[T] = Empty


  def apply[T](onMsg: T => Unit): ReceiveMsg[T] = {
    new ReceiveMsg[T] {
      def connected(address: Address): Unit = {}
      def disconnected(address: Address): Unit = {}
      def apply(msg: T, address: Address): Unit = onMsg(msg)
    }
  }

  def apply[A, B](receiveMsg: ReceiveMsg[A], f: B => A): ReceiveMsg[B] = {
    new ReceiveMsg[B] {
      def connected(address: Address): Unit = receiveMsg.connected(address)
      def disconnected(address: Address): Unit = receiveMsg.disconnected(address)
      def apply(msg: B, address: Address): Unit = receiveMsg(f(msg), address)
    }
  }
}