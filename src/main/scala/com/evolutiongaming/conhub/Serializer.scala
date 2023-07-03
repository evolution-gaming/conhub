package com.evolutiongaming.conhub

import scodec.bits.ByteVector

trait Serializer[A, B] {

  def to(a: A): B

  def from(b: B): A
}

object Serializer {

  type Bin[A] = Serializer[A, ByteVector]

  type Str[A] = Serializer[A, String]

  def identity[A]: Serializer[A, A] = new Serializer[A, A] {
    def to(a: A): A   = a
    def from(b: A): A = b
  }
}
