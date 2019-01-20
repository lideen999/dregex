package dregex

import dregex.impl.Compiler
import dregex.impl.Dfa
import dregex.impl.SimpleState

/**
 * A fully-compiled regular expression that was generated from a string literal.
 */
/*
 * Private parameters used to work around Scaladoc limitation for val's in private
 * constructors.
 */
class CompiledRegex private[dregex] (
  _originalString: String,
  _parsedRegex: ParsedRegex, val universe: Universe
) extends Regex {

  /**
    * The original regex string, before parsing.
    */
  val originalString: String = _originalString

  /**
    * Object representing the parsed tree
    */
  val parsedRegex: ParsedRegex = _parsedRegex

  private[dregex] val dfa: Dfa[SimpleState] = {
    new Compiler(universe.alphabet).fromTree(parsedRegex.tree)
  }

  override def toString = s"⟪$originalString⟫ (DFA states: ${dfa.stateCount})"

}
