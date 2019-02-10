package dregex

import java.util.regex.Pattern

import dregex.impl.RegexParser
import dregex.impl.Util
import dregex.impl.SimpleState
import dregex.impl.DfaAlgorithms
import dregex.impl.Dfa
import dregex.impl.RegexParser.DotMatch
import org.slf4j.LoggerFactory

import scala.collection.immutable.Seq
import scala.collection.JavaConverters._

/**
  * A regular expression, ready to be tested against strings, or to take part in an operation against another.
  * Internally, instances of this type have a DFA (Deterministic Finite Automaton).
  */
trait Regex {

  private[this] val logger = LoggerFactory.getLogger(classOf[Regex])

  private[dregex] def dfa: Dfa[SimpleState]

  /**
    * Return this regex's [[Universe]]. Only regexes of the same universe can be operated together.
    */
  def universe: Universe

  private def checkUniverse(other: Regex): Unit = {
    if (other.universe != universe)
      throw new Exception("cannot make operations between regex from different universes")
  }

  /**
    * Return whether a string is matched by the regular expression (i.e. whether the string is included in the language
    * generated by the expression).
    * As the match is done using a DFA, its complexity is O(n), where n is the length of the string. It is constant
    * with respect to the length of the expression.
    */
  def matches(string: CharSequence): Boolean = {
    val (result, _) = matchAndReport(string)
    result
  }

  /**
    * Similar to method [[matches]], except that also return how many characters were successfully matched in case of
    * failure.
    */
  def matchAndReport(string: CharSequence): (Boolean, Int) = {
    DfaAlgorithms.matchString(dfa, universe.normalization.normalize(string))
  }

  /**
    * Intersect this regular expression with another. The resulting expression will match the strings that are
    * matched by the operands, and only those. Intersections take O(n⋅m) time, where n and m are the number of states of
    * the DFA of the operands.
    */
  def intersect(other: Regex): Regex = {
    val (res, time) = Util.time {
      checkUniverse(other)
      new SynteticRegex(DfaAlgorithms.rewriteWithSimpleStates(DfaAlgorithms.intersect(this.dfa, other.dfa)), universe)
    }
    logger.trace("{} and {} intersected in {}", this, other, time)
    res
  }

  /**
    * Subtract other regular expression from this one. The resulting expression will match the strings that are
    * matched this expression and are not matched by the other, and only those. Differences take O(n⋅m) time, where n
    * and m are the number of states of the DFA of the operands.
    */
  def diff(other: Regex): Regex = {
    val (res, time) = Util.time {
      checkUniverse(other)
      new SynteticRegex(DfaAlgorithms.rewriteWithSimpleStates(DfaAlgorithms.diff(this.dfa, other.dfa)), universe)
    }
    logger.trace("{} and {} diffed in {}", this, other, time)
    res
  }

  /**
    * Unite this regular expression with another. The resulting expression will match the strings that are matched by
    * either of the operands, and only those. Unions take O(n⋅m) time, where n and m are the number of states of the DFA
    * of the operands.
    */
  def union(other: Regex): Regex = {
    val (res, time) = Util.time {
      checkUniverse(other)
      new SynteticRegex(DfaAlgorithms.rewriteWithSimpleStates(DfaAlgorithms.union(this.dfa, other.dfa)), universe)
    }
    logger.trace("{} and {} unioned in {}", this, other, time)
    res
  }

  /**
    * Return whether this expression matches at least one string in common with another. Intersections take O(n⋅m) time,
    * where n and m are the number of states of the DFA of the operands.
    */
  def doIntersect(other: Regex): Boolean = {
    checkUniverse(other)
    DfaAlgorithms.isIntersectionNotEmpty(this.dfa, other.dfa)
  }

  /**
    * Return whether this expressions matches every expression that is matched by another. An [[diff]] between the
    * two operands is done internally.
    */
  def isSubsetOf(other: Regex): Boolean = {
    checkUniverse(other)
    DfaAlgorithms.isSubsetOf(this.dfa, other.dfa)
  }

  /**
    * Return whether this expressions matches every expression that is matched by another, but the expressions are not
    * equal. Two [[diff]] between the two operands are done internally.
    */
  def isProperSubsetOf(other: Regex): Boolean = {
    checkUniverse(other)
    DfaAlgorithms.isProperSubset(this.dfa, other.dfa)
  }

  /**
    * Return whether this regular expression is equivalent to other. Two regular expressions are equivalent if they
    * match exactly the same set of strings. This operation takes O(n⋅m) time, where n and m are the number of states of
    * the DFA of the operands.
    */
  def equiv(other: Regex): Boolean = {
    checkUniverse(other)
    DfaAlgorithms.equivalent(this.dfa, other.dfa)
  }

  /**
    * Return whether this regular expression matches anything. Note that the empty string is a valid match.
    */
  def matchesAtLeastOne(): Boolean = DfaAlgorithms.matchesAtLeastOne(dfa)

}

/**
  * @define flagsDesc match flags, a bit mask that may include `java.util.regex.Pattern.LITERAL`, and `java.util.regex.Pattern.COMMENTS`.
  */
object Regex {

  private[this] val logger = LoggerFactory.getLogger(Regex.getClass)

  private[this] def flagsFromBits(bits: Int): RegexParser.Flags = {
    RegexParser.Flags(
      dotMatch = dotMatcherFromFlags(bits),
      literal = (bits & Pattern.LITERAL) != 0,
      comments = (bits & Pattern.COMMENTS) != 0,
      unicodeClasses = (bits & Pattern.UNICODE_CHARACTER_CLASS) != 0,
      caseInsensitive = (bits & Pattern.CASE_INSENSITIVE) != 0,
      unicodeCase = (bits & Pattern.UNICODE_CASE) != 0,
      canonicalEq = (bits & Pattern.CANON_EQ) != 0
    )
  }

  /**
    * Compile a regex from a string, using it's own [[Universe]], with the given flags.
    *
    * @param flags $flagsDesc
    */
  def compile(regex: String, flags: Int): CompiledRegex = {
    val (tree, norm) = RegexParser.parse(regex, flagsFromBits(flags))
    val (compiled, time) = Util.time {
      new CompiledRegex(regex, tree, new Universe(Seq(tree), norm))
    }
    logger.trace("{} compiled in {}", compiled, time: Any)
    compiled
  }

  private def dotMatcherFromFlags(flags: Int): DotMatch = {
    if ((flags & Pattern.DOTALL) != 0) {
      DotMatch.All
    } else {
      if ((flags & Pattern.UNIX_LINES) != 0) {
        DotMatch.UnixLines
      } else {
        DotMatch.JavaLines
      }
    }
  }

  /**
    * Compiles a set of regular expressions in the same [[Universe]].
    */
  def compile(regex: String): CompiledRegex = compile(regex, 0)

  /**
    * Compiles a set of regular expressions in the same [[Universe]], with the given flags. Java version.
    *
    * @param flags $flagsDesc
    */
  def compile(regexs: java.util.List[String], flags: Int): java.util.List[CompiledRegex] = {
    compile(regexs.asScala.to[Seq], flags).asJava
  }

  /**
    * Compiles a set of regular expressions in the same [[Universe]]. Java version.
    */
  def compile(regexs: java.util.List[String]): java.util.List[CompiledRegex] = compile(regexs, 0)

  /**
    * Compiles a set of regular expressions in the same [[Universe]], with the given flags. Scala version.
    *
    * @param flags $flagsDesc
    */
  def compile(regexs: Seq[String], flags: Int = 0): Seq[CompiledRegex] = {
    val compilation = regexs.map { r =>
      RegexParser.parse(r, flagsFromBits(flags))
    }
    val (trees, norms) = compilation.unzip
    // TODO: head?
    val universe = new Universe(trees, norms.head)
    for ((regex, tree) <- regexs zip trees) yield {
      val (res, time) = Util.time {
        new CompiledRegex(regex, tree, universe)
      }
      logger.trace("{} compiled in {}", regex, time: Any)
      res
    }
  }

  /**
    * Create a regular expression that does not match anything. Note that that is different from matching the empty
    * string. Despite the theoretical equivalence of automata and regular expressions, in practice there is no regular
    * expression that does not match anything.
    */
  def nullRegex(u: Universe): Regex = new SynteticRegex(Dfa.NothingDfa, u)

}
