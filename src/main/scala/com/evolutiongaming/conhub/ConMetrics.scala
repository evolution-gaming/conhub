package com.evolutiongaming.conhub

trait ConMetrics[Id, T, M] {

  def onTell(m: M): Unit

  def registerGauges(cons: => Iterable[Conn[T, M]]): Unit

  def onChanged(diff: ConStates.Diff[Id, Conn[T, M]]): Unit
}