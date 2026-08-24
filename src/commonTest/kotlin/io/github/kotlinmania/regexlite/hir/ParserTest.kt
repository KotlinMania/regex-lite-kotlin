// port-lint: tests hir/parse.rs
package io.github.kotlinmania.regexlite.hir

import kotlin.test.Test
import kotlin.test.assertEquals

class ParserTest {
    private fun p(pattern: String): Hir =
        Parser(Config(), pattern).parseInner().getOrThrow()

    private fun perr(pattern: String): String =
        Parser(Config(), pattern).parseInner().exceptionOrNull()!!.message!!

    private fun classNode(vararg ranges: Pair<Char, Char>): Hir =
        Hir.classNode(Class(ranges.map { ClassRange(it.first, it.second) }.toMutableList()))

    private fun singles(vararg chars: Char): Hir =
        Hir.classNode(Class(chars.map { ClassRange(it, it) }.toMutableList()))

    private fun posix(name: String): Hir =
        Hir.classNode(Class(posixClass(name).getOrThrow().toMutableList()))

    private fun cap(index: UInt, sub: Hir): Hir =
        Hir.capture(Capture(index = index, name = null, sub = sub))

    private fun namedCap(index: UInt, name: String, sub: Hir): Hir =
        Hir.capture(Capture(index = index, name = name, sub = sub))

    @Test
    fun okLiteral() {
        assertEquals(Hir.char('a'), p("a"))
        assertEquals(Hir.concat(mutableListOf(Hir.char('a'), Hir.char('b'))), p("ab"))
        assertEquals(Hir.char('\uD83D'), p("\uD83D"))
    }

    @Test
    fun okMetaEscapes() {
        assertEquals(Hir.char('*'), p("""\*"""))
        assertEquals(Hir.char('+'), p("""\+"""))
        assertEquals(Hir.char('?'), p("""\?"""))
        assertEquals(Hir.char('|'), p("""\|"""))
        assertEquals(Hir.char('('), p("""\("""))
        assertEquals(Hir.char(')'), p("""\)"""))
        assertEquals(Hir.char('^'), p("""\^"""))
        assertEquals(Hir.char('$'), p("""\$"""))
        assertEquals(Hir.char('['), p("""\["""))
        assertEquals(Hir.char(']'), p("""\]"""))
    }

    @Test
    fun okSpecialEscapes() {
        assertEquals(Hir.char('\u0007'), p("""\a"""))
        assertEquals(Hir.char('\u000C'), p("""\f"""))
        assertEquals(Hir.char('\t'), p("""\t"""))
        assertEquals(Hir.char('\n'), p("""\n"""))
        assertEquals(Hir.char('\r'), p("""\r"""))
        assertEquals(Hir.char('\u000B'), p("""\v"""))
        assertEquals(Hir.look(Look.Start), p("""\A"""))
        assertEquals(Hir.look(Look.End), p("""\z"""))
        assertEquals(Hir.look(Look.Word), p("""\b"""))
        assertEquals(Hir.look(Look.WordNegate), p("""\B"""))
    }

    @Test
    fun okHex() {
        // fixed length
        assertEquals(Hir.char('A'), p("""\x41"""))
        assertEquals(Hir.char('☃'), p("""\u2603"""))
        // braces
        assertEquals(Hir.char('A'), p("""\x{41}"""))
        assertEquals(Hir.char('☃'), p("""\u{2603}"""))
    }

    @Test
    fun okPerl() {
        assertEquals(posix("digit"), p("""\d"""))
        assertEquals(posix("space"), p("""\s"""))
        assertEquals(posix("word"), p("""\w"""))

        val negated = { name: String ->
            val cls = Class(posixClass(name).getOrThrow().toMutableList())
            cls.negate()
            Hir.classNode(cls)
        }
        assertEquals(negated("digit"), p("""\D"""))
        assertEquals(negated("space"), p("""\S"""))
        assertEquals(negated("word"), p("""\W"""))
    }

    @Test
    fun okFlagsAndPrimitives() {
        assertEquals(Hir.char('a'), p("""a"""))
        assertEquals(singles('A', 'a'), p("""(?i:a)"""))

        assertEquals(Hir.look(Look.Start), p("""^"""))
        assertEquals(Hir.look(Look.StartLF), p("""(?m:^)"""))
        assertEquals(Hir.look(Look.StartCRLF), p("""(?mR:^)"""))

        assertEquals(Hir.look(Look.End), p("""$"""))
        assertEquals(Hir.look(Look.EndLF), p("""(?m:$)"""))
        assertEquals(Hir.look(Look.EndCRLF), p("""(?mR:$)"""))

        assertEquals(classNode('\u0000' to '\u0009', '\u000B' to '\uFFFF'), p("""."""))
        assertEquals(
            classNode(
                '\u0000' to '\u0009',
                '\u000B' to '\u000C',
                '\u000E' to '\uFFFF',
            ),
            p("""(?R:.)"""),
        )
        assertEquals(classNode('\u0000' to '\uFFFF'), p("""(?s:.)"""))
        assertEquals(classNode('\u0000' to '\uFFFF'), p("""(?sR:.)"""))
    }

    @Test
    fun okAlternate() {
        assertEquals(
            Hir.alternation(mutableListOf(Hir.char('a'), Hir.char('b'))),
            p("""a|b"""),
        )
        assertEquals(
            Hir.alternation(mutableListOf(Hir.char('a'), Hir.char('b'))),
            p("""(?:a|b)"""),
        )
        assertEquals(
            cap(1u, Hir.alternation(mutableListOf(Hir.char('a'), Hir.char('b')))),
            p("""(a|b)"""),
        )
        assertEquals(
            namedCap(1u, "foo", Hir.alternation(mutableListOf(Hir.char('a'), Hir.char('b')))),
            p("""(?<foo>a|b)"""),
        )
        assertEquals(
            Hir.alternation(mutableListOf(Hir.char('a'), Hir.char('b'), Hir.char('c'))),
            p("""a|b|c"""),
        )
        assertEquals(
            Hir.alternation(
                mutableListOf(
                    Hir.concat(mutableListOf(Hir.char('a'), Hir.char('x'))),
                    Hir.concat(mutableListOf(Hir.char('b'), Hir.char('y'))),
                    Hir.concat(mutableListOf(Hir.char('c'), Hir.char('z'))),
                ),
            ),
            p("""ax|by|cz"""),
        )
        assertEquals(
            Hir.alternation(mutableListOf(Hir.empty(), Hir.empty())),
            p("""|"""),
        )
        assertEquals(
            Hir.alternation(mutableListOf(Hir.empty(), Hir.empty(), Hir.empty())),
            p("""||"""),
        )
        assertEquals(
            Hir.alternation(mutableListOf(Hir.char('a'), Hir.empty())),
            p("""a|"""),
        )
        assertEquals(
            Hir.alternation(mutableListOf(Hir.empty(), Hir.char('a'))),
            p("""|a"""),
        )
    }

    @Test
    fun okFlagGroup() {
        assertEquals(
            Hir.concat(mutableListOf(Hir.char('a'), singles('B', 'b'))),
            p("a(?i:b)"),
        )
    }

    @Test
    fun okFlagDirective() {
        assertEquals(singles('A', 'a'), p("(?i)a"))
        assertEquals(Hir.char('a'), p("a(?i)"))
        assertEquals(
            Hir.concat(mutableListOf(Hir.char('a'), singles('B', 'b'))),
            p("a(?i)b"),
        )
    }

    @Test
    fun okUncountedRepetition() {
        assertEquals(
            Hir.repetition(Repetition(min = 0u, max = 1u, greedy = true, sub = Hir.char('a'))),
            p("""a?"""),
        )
        assertEquals(
            Hir.repetition(Repetition(min = 0u, max = null, greedy = true, sub = Hir.char('a'))),
            p("""a*"""),
        )
        assertEquals(
            Hir.repetition(Repetition(min = 1u, max = null, greedy = true, sub = Hir.char('a'))),
            p("""a+"""),
        )
        assertEquals(
            Hir.repetition(Repetition(min = 0u, max = 1u, greedy = false, sub = Hir.char('a'))),
            p("""a??"""),
        )
    }

    @Test
    fun okCountedRepetition() {
        assertEquals(
            Hir.repetition(Repetition(min = 5u, max = 5u, greedy = true, sub = Hir.char('a'))),
            p("""a{5}"""),
        )
        assertEquals(
            Hir.repetition(Repetition(min = 5u, max = 5u, greedy = false, sub = Hir.char('a'))),
            p("""a{5}?"""),
        )
        assertEquals(
            Hir.repetition(Repetition(min = 5u, max = null, greedy = true, sub = Hir.char('a'))),
            p("""a{5,}"""),
        )
        assertEquals(
            Hir.repetition(Repetition(min = 5u, max = 9u, greedy = true, sub = Hir.char('a'))),
            p("""a{5,9}"""),
        )
    }

    @Test
    fun okClass() {
        assertEquals(singles('a'), p("""[a]"""))
        assertEquals(singles('a', ']'), p("""[a\]]"""))
        assertEquals(singles('a', '-', 'z'), p("""[a\-z]"""))
        assertEquals(classNode('a' to 'b'), p("""[ab]"""))
        assertEquals(singles('a', '-'), p("""[a-]"""))
        assertEquals(singles('a', '-'), p("""[-a]"""))
        assertEquals(posix("alnum"), p("""[[:alnum:]]"""))
        assertEquals(posix("word"), p("""[\w]"""))
        assertEquals(classNode('\u0000' to '\uFFFF'), p("""[\s\S]"""))
        assertEquals(Hir.fail(), p("""[^\s\S]"""))
        assertEquals(classNode('a' to 'c', 'x' to 'z'), p("""[a-cx-z]"""))
        assertEquals(singles(']'), p("""[]]"""))
        assertEquals(singles(']', 'a'), p("""[]a]"""))
    }

    @Test
    fun errStandard() {
        assertEquals(
            ERR_TOO_MUCH_NESTING,
            perr("(((((((((((((((((((((((((((((((((((((((((((((((((((a)))))))))))))))))))))))))))))))))))))))))))))))))))"),
        )
        assertEquals(ERR_DUPLICATE_CAPTURE_NAME, perr("""(?P<a>y)(?P<a>z)"""))
        assertEquals(ERR_UNCLOSED_GROUP, perr("("))
        assertEquals(ERR_UNCLOSED_GROUP_QUESTION, perr("(?"))
        assertEquals(ERR_UNOPENED_GROUP, perr(")"))
        assertEquals(ERR_LOOK_UNSUPPORTED, perr("""(?=a)"""))
        assertEquals(ERR_LOOK_UNSUPPORTED, perr("""(?!a)"""))
        assertEquals(ERR_LOOK_UNSUPPORTED, perr("""(?<=a)"""))
        assertEquals(ERR_LOOK_UNSUPPORTED, perr("""(?<!a)"""))
        assertEquals(ERR_EMPTY_FLAGS, perr("""(?)"""))
        assertEquals(ERR_MISSING_GROUP_NAME, perr("""(?P<"""))
        assertEquals(ERR_MISSING_GROUP_NAME, perr("""(?<"""))
        assertEquals(ERR_INVALID_GROUP_NAME, perr("""(?P<1abc>z)"""))
        assertEquals(ERR_INVALID_GROUP_NAME, perr("""(?<1abc>z)"""))
        assertEquals(ERR_INVALID_GROUP_NAME, perr("""(?<¾>z)"""))
        assertEquals(ERR_INVALID_GROUP_NAME, perr("""(?<¾a>z)"""))
        assertEquals(ERR_INVALID_GROUP_NAME, perr("""(?<☃>z)"""))
        assertEquals(ERR_INVALID_GROUP_NAME, perr("""(?<a☃>z)"""))
        assertEquals(ERR_UNCLOSED_GROUP_NAME, perr("""(?P<foo"""))
        assertEquals(ERR_UNCLOSED_GROUP_NAME, perr("""(?<foo"""))
        assertEquals(ERR_EMPTY_GROUP_NAME, perr("""(?P<>z)"""))
        assertEquals(ERR_EMPTY_GROUP_NAME, perr("""(?<>z)"""))
        assertEquals(ERR_FLAG_UNRECOGNIZED, perr("""(?z:foo)"""))
        assertEquals(ERR_FLAG_REPEATED_NEGATION, perr("""(?s-i-R)"""))
        assertEquals(ERR_FLAG_DUPLICATE, perr("""(?isi)"""))
        assertEquals(ERR_FLAG_DUPLICATE, perr("""(?is-i)"""))
        assertEquals(ERR_FLAG_UNEXPECTED_EOF, perr("""(?is"""))
        assertEquals(ERR_FLAG_DANGLING_NEGATION, perr("""(?is-:foo)"""))
        assertEquals(ERR_HEX_BRACE_INVALID_DIGIT, perr("""\x{Z}"""))
        assertEquals(ERR_HEX_BRACE_UNEXPECTED_EOF, perr("""\x{"""))
        assertEquals(ERR_HEX_BRACE_EMPTY, perr("""\x{}"""))
        assertEquals(ERR_HEX_FIXED_UNEXPECTED_EOF, perr("""\xA"""))
        assertEquals(ERR_HEX_FIXED_INVALID_DIGIT, perr("""\xZ"""))
        assertEquals(ERR_HEX_UNEXPECTED_EOF, perr("""\x"""))
        assertEquals(ERR_ESCAPE_UNEXPECTED_EOF, perr("""\"""))
        assertEquals(ERR_BACKREF_UNSUPPORTED, perr("""\0"""))
        assertEquals(ERR_BACKREF_UNSUPPORTED, perr("""\1"""))
        assertEquals(ERR_BACKREF_UNSUPPORTED, perr("""\8"""))
        assertEquals(ERR_UNICODE_CLASS_UNSUPPORTED, perr("""\pL"""))
        assertEquals(ERR_UNICODE_CLASS_UNSUPPORTED, perr("""\p{L}"""))
        assertEquals(ERR_ESCAPE_UNRECOGNIZED, perr("""\i"""))
        assertEquals(ERR_UNCOUNTED_REP_SUB_MISSING, perr("?"))
        assertEquals(ERR_UNCOUNTED_REP_SUB_MISSING, perr("*"))
        assertEquals(ERR_UNCOUNTED_REP_SUB_MISSING, perr("+"))
        assertEquals(ERR_COUNTED_REP_SUB_MISSING, perr("{5}"))
        assertEquals(ERR_COUNTED_REP_UNCLOSED, perr("a{"))
        assertEquals(ERR_COUNTED_REP_MIN_UNCLOSED, perr("a{5"))
        assertEquals(ERR_COUNTED_REP_COMMA_UNCLOSED, perr("a{5,"))
        assertEquals(ERR_COUNTED_REP_MIN_MAX_UNCLOSED, perr("a{5,6"))
        assertEquals(ERR_COUNTED_REP_INVALID, perr("a{5,6Z"))
        assertEquals(ERR_COUNTED_REP_INVALID_RANGE, perr("a{6,5}"))
        assertEquals(ERR_DECIMAL_NO_DIGITS, perr("a{}"))
        assertEquals(ERR_CLASS_UNCLOSED_AFTER_ITEM, perr("[a"))
        assertEquals(ERR_CLASS_INVALID_RANGE_ITEM, perr("""[\w-a]"""))
        assertEquals(ERR_CLASS_INVALID_RANGE_ITEM, perr("""[a-\w]"""))
        assertEquals(ERR_CLASS_INVALID_ITEM, perr("""[\b]"""))
        assertEquals(ERR_CLASS_UNCLOSED_AFTER_DASH, perr("[a-"))
        assertEquals(ERR_CLASS_UNCLOSED_AFTER_NEGATION, perr("[^"))
        assertEquals(ERR_CLASS_UNCLOSED_AFTER_CLOSING, perr("[]"))
        assertEquals(ERR_CLASS_INVALID_RANGE, perr("[z-a]"))
        assertEquals(ERR_CLASS_UNCLOSED, perr("["))
        assertEquals(ERR_CLASS_UNCLOSED, perr("[a-z"))
        assertEquals(ERR_CLASS_NEST_UNSUPPORTED, perr("[a-z[A-Z]]"))
        assertEquals(ERR_CLASS_NEST_UNSUPPORTED, perr("[[:alnum]]"))
        assertEquals(ERR_CLASS_INTERSECTION_UNSUPPORTED, perr("[a&&b]"))
        assertEquals(ERR_CLASS_DIFFERENCE_UNSUPPORTED, perr("[a--b]"))
        assertEquals(ERR_CLASS_SYMDIFFERENCE_UNSUPPORTED, perr("[a~~b]"))
        assertEquals(ERR_SPECIAL_WORD_BOUNDARY_UNCLOSED, perr("""\b{foo"""))
        assertEquals(ERR_SPECIAL_WORD_BOUNDARY_UNCLOSED, perr("""\b{foo!}"""))
        assertEquals(ERR_SPECIAL_WORD_BOUNDARY_UNRECOGNIZED, perr("""\b{foo}"""))
        assertEquals(ERR_SPECIAL_WORD_OR_REP_UNEXPECTED_EOF, perr("""\b{"""))
    }
}
