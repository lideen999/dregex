package dregex

import dregex.impl.RegexTree
import dregex.impl.CharInterval
import dregex.impl.Normalization

import scala.collection.immutable.Seq

/**
  * The purpose of this class is to enforce that set operation between regular expressions are only done when it is
  * legal to do so, that is, when the regex are compatible.
  *
  * The way this is enforced is that every compiled regular expression contains a reference to a [[Universe]], and
  * only expressions with the same universe are allowed to mix in set operation.
  *
  * The same [[Universe]] ensures the same "alphabet" and [[Normalization]] rules. Regular expressions compiled as a
  * group will always have the same universe.
  *
  * In general, dealing with this class or calling the constructor is not necessary; a call to one of the `compile`
  * methods is simpler and more direct. However, there are cases in which the intermediate [[ParsedRegex]]s are
  * needed. Most notably, when caching [[CompiledRegex]] instances (which are in general more expensive to create).
  */
class Universe(parsedTrees: Seq[RegexTree.Node], val normalization: Normalization) {

  import RegexTree._

  private[dregex] val alphabet: Map[AbstractRange, Seq[CharInterval]] = {
    CharInterval.calculateNonOverlapping(parsedTrees.flatMap(t => collect(t)))
  }

  /**
    * Regular expressions can have character classes and wildcards. In order to produce a NFA, they should be expanded
    * to disjunctions. As the base alphabet is Unicode, just adding a wildcard implies a disjunction of more than one
    * million code points. Same happens with negated character classes or normal classes with large ranges.
    *
    * To prevent this, the sets are not expanded to all characters individually, but only to disjoint intervals.
    *
    * Example:
    *
    * [abc]     -> a-c
    * [^efg]    -> 0-c|h-MAX
    * mno[^efg] -> def(0-c|h-l|m|n|o|p-MAX)
    * .         -> 0-MAX
    *
    * Care must be taken when the regex is meant to be used for an operation with another regex (such as intersection
    * or difference). In this case, the sets must be disjoint across all the "universe"
    *
    * This method collects the interval, so they can then be made disjoint.
    */
  private[dregex] def collect(ast: Node): Seq[AbstractRange] = ast match {
    // Lookaround is also a ComplexPart, order important
    case Lookaround(dir, cond, value) => collect(value) :+ Wildcard
    case complex: ComplexPart         => complex.values.flatMap(collect)
    case range: AbstractRange         => Seq(range)
    case CharSet(ranges)              => ranges
  }

}

object Universe {
  val Empty = new Universe(Seq(), Normalization.NoNormalization)
}
