package dregex

import org.scalatest.FunSuite
import TestUtil.using

class UnicodeTest extends FunSuite {

  test("astral planes") {
    using(Regex.compile(".")) { r =>
      assertResult(true)(r.matches("a"))
      assertResult(true)(r.matches("𐐷"))
      assertResult(true)(r.matches("\uD801\uDC37"))
    }
    using(Regex.compile("𐐷")) { r =>
      assertResult(false)(r.matches("a"))
      assertResult(true)(r.matches("𐐷"))
      assertResult(true)(r.matches("\uD801\uDC37"))
    }
  }

  test("escapes") {
    
    /* 
     * Note that Unicode escaping still happens at the source code level even inside triple quotes, so 
     * have to double escape in those cases.
     */
    
    using(Regex.compile("""\x41""")) { r =>
      assertResult(true)(r.matches("A"))
    }
    using(Regex.compile("\\u0041")) { r =>
      assertResult(true)(r.matches("A"))
    }
    using(Regex.compile("""\x{41}""")) { r =>
      assertResult(true)(r.matches("A"))
    }
    using(Regex.compile("""\x{10437}""")) { r =>
      assertResult(true)(r.matches("𐐷"))
    }

    // double Unicode escaping
    using(Regex.compile("\\uD801\\uDC37")) { r =>
      assertResult(true)(r.matches("𐐷"))
    }
    
    // high surrogate alone, works like a normal character
    using(Regex.compile("\\uD801")) { r =>
      assertResult(false)(r.matches("A"))
      assertResult(true)(r.matches("\uD801"))
    }
    
    // high surrogate followed by normal char, works like two normal characters
    using(Regex.compile("\\uD801\\u0041")) { r =>
      assertResult(false)(r.matches("A"))
      assertResult(true)(r.matches("\uD801\u0041"))
      assertResult(true)(r.matches("\uD801" + "\u0041"))
    }
    
  }

}