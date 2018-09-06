package com.evolutiongaming.conhub

import com.evolutiongaming.nel.Nel

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait ConHub[Id, T, M, L] extends ConHub.Send[T, M]
  with ConHub.View[Id, T, M, L]
  with ConHub.Manage[Id, T, M]

object ConHub {

  trait Send[T, M] extends ConnTypes[T, M] {
    type SR = Future[SendResult[C]]

    def !(msg: M): SR

    def !(msgs: Nel[M]): SR
  }


  trait View[Id, T, M, L] extends ConnTypes[T, M] {

    def searchEngine: SearchEngine[Id, T, M, L]

    def conStates: ConStates[Id, T, M]

    def cons(l: L, localCall: Boolean = true): Iterable[C] = searchEngine(l, conStates.values, localCall)

    def cons: Iterable[C] = conStates.values.values

    def consLocal: Iterable[C.Local] = cons.collect { case x: C.Local => x }

    def consRemote: Iterable[C.Remote] = cons.collect { case x: C.Remote => x }
  }


  trait Manage[Id, T, M] {
    type Result = Future[UpdateResult[T]]

    /**
      *
      * @param id connection ID
      * @param version operations against previous version will be ignored
      * @param con new connection
      * @param send function which will be used to forward messages for particular connection
      */
    def update(id: Id, version: Version, con: T, send: Conn.Send[M]): Result

    /**
      * Disconnect a connection with instance tracking, to check later for successful disconnect or failure
      * @param id connection Id
      * @param version operations against previous version will be ignored
      * @param reconnectTimeout time to live for disconnected state
      */
    def disconnect(id: Id, version: Version, reconnectTimeout: FiniteDuration): Result

    /** Remove a connection from registry, triggering eventual listeners.
      * Can be called directly when connection is not expected to reconnect, or for connections
      * without instance tracking.
      * @param id connection ID
      * @param version operations against previous version will be ignored
      */
    def remove(id: Id, version: Version): Result
  }
}


trait ConnTypes[T, M] {
  type C = Conn[T, M]

  object C {
    type Local = Conn.Local[T, M]
    type Remote = Conn.Remote[T]
    type Disconnected = Conn.Disconnected[T]
    type Connected = Conn.Connected[T, M]
  }
}