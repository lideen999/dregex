package dregex.impl

object NormTree {

  trait Node

  trait Char extends Node
  trait SglChar extends Char
  
  case object Other extends SglChar {
    override def toString = "other"
  }

  case class Lit(char: scala.Char) extends SglChar {
    override def toString = char.toString
  }

  case object Epsilon extends Char {
    override def toString = "ε"
  }

  case class Disj(values: Seq[Node]) extends Node {
    override def toString = s"Disj(${values.mkString(", ")})"
  }

  /**
   * A repetition of at least {@link #min} up to {@link #max}. 
   * If {@link #max} is {@link Option#None} it means infinite.
   */
  case class Rep(min: Int, max: Option[Int], value: Node) extends Node

  case class Juxt(values: Seq[Node]) extends Node {
    override def toString = s"Juxt(${values.mkString(", ")})"
  }

}