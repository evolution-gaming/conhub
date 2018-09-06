package com.evolutiongaming.conhub

trait Serializer[A, B] {

  def to(a: A): B

  def from(b: B): A
}

object Serializer {

  type Bin[T] = Serializer[T, Array[Byte]]

  type Str[T] = Serializer[T, String]


  def identity[T]: Serializer[T, T] = new Serializer[T, T] {
    def to(a: T): T = a
    def from(b: T): T = b
  }
}