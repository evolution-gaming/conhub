package com.evolutiongaming.conhub

import scala.concurrent.Future

trait SearchEngine[Id, A, M, L] extends ConnTypes[A, M] {

  type Cons = collection.Map[Id, C]

  /**
    * @param localCall true when origin of search is on current node, false otherwise.
    *                  might be used to find remote connection regarding the origin of call
    */
  def apply(l: L, cons: Cons, localCall: Boolean): Iterable[C]

  def update(diff: ConStates.Diff[Id, C], cons: Cons): Future[Unit]
}
