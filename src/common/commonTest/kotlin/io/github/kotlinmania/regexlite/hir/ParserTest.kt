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

    private fun `class`(vararg ranges: Pair<Char, Char>): Hir = classNode(*ranges)

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
        assertEquals(Hir.concat(mutableListOf(Hir.char('\uD83D'), Hir.char('\uDCA9'))), p("💩"))
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
    fun okGroupUnnamed() {
        assertEquals(cap(1u, Hir.char('a')), p("(a)"))
        assertEquals(
            cap(1u, Hir.concat(mutableListOf(Hir.char('a'), Hir.char('b')))),
            p("(ab)"),
        )
    }

    @Test
    fun okGroupNamed() {
        assertEquals(namedCap(1u, "foo", Hir.char('a')), p("(?P<foo>a)"))
        assertEquals(namedCap(1u, "foo", Hir.char('a')), p("(?<foo>a)"))

        assertEquals(
            namedCap(
                1u,
                "foo",
                Hir.concat(mutableListOf(Hir.char('a'), Hir.char('b'))),
            ),
            p("(?P<foo>ab)"),
        )
        assertEquals(
            namedCap(
                1u,
                "foo",
                Hir.concat(mutableListOf(Hir.char('a'), Hir.char('b'))),
            ),
            p("(?<foo>ab)"),
        )

        assertEquals(namedCap(1u, "a", Hir.char('z')), p("(?<a>z)"))
        assertEquals(namedCap(1u, "a", Hir.char('z')), p("(?P<a>z)"))

        assertEquals(namedCap(1u, "a_1", Hir.char('z')), p("(?<a_1>z)"))
        assertEquals(namedCap(1u, "a_1", Hir.char('z')), p("(?P<a_1>z)"))

        assertEquals(namedCap(1u, "a.1", Hir.char('z')), p("(?<a.1>z)"))
        assertEquals(namedCap(1u, "a.1", Hir.char('z')), p("(?P<a.1>z)"))

        assertEquals(namedCap(1u, "a[1]", Hir.char('z')), p("(?<a[1]>z)"))
        assertEquals(namedCap(1u, "a[1]", Hir.char('z')), p("(?P<a[1]>z)"))

        assertEquals(namedCap(1u, "a¾", Hir.char('z')), p("(?<a¾>z)"))
        assertEquals(namedCap(1u, "a¾", Hir.char('z')), p("(?P<a¾>z)"))

        assertEquals(namedCap(1u, "名字", Hir.char('z')), p("(?<名字>z)"))
        assertEquals(namedCap(1u, "名字", Hir.char('z')), p("(?P<名字>z)"))
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
    fun okVerbatim() {
        assertEquals(
            Hir.repetition(
                Repetition(
                    min = 5u,
                    max = 9u,
                    greedy = false,
                    sub = Hir.char('a'),
                ),
            ),
            p("""(?x)a{5,9} ?"""),
        )
        assertEquals(singles('a'), p("""(?x)[   a]"""))
        assertEquals(
            classNode('\u0000' to '`', 'b' to '\uFFFF'),
            p("""(?x)[ ^  a]"""),
        )
        assertEquals(singles('a', '-'), p("""(?x)[ - a]"""))
        assertEquals(singles(']', 'a'), p("""(?x)[ ] a]"""))

        assertEquals(
            Hir.concat(mutableListOf(Hir.char('a'), Hir.char('b'))),
            p("""(?x)a b"""),
        )
        assertEquals(
            Hir.concat(
                mutableListOf(
                    Hir.char('a'),
                    Hir.char('b'),
                    Hir.char('a'),
                    Hir.char(' '),
                    Hir.char('b'),
                ),
            ),
            p("""(?x)a b(?-x)a b"""),
        )
        assertEquals(
            Hir.concat(
                mutableListOf(
                    Hir.char('a'),
                    Hir.char(' '),
                    Hir.char('a'),
                    Hir.char('a'),
                    Hir.char(' '),
                ),
            ),
            p("""a (?x:a )a """),
        )
        assertEquals(
            namedCap(1u, "foo", Hir.char('a')),
            p("""(?x)( ?P<foo> a )"""),
        )
        assertEquals(cap(1u, Hir.char('a')), p("""(?x)(  a )"""))
        assertEquals(Hir.char('a'), p("""(?x)(   ?:  a )"""))
        assertEquals(Hir.char('\u0053'), p("""(?x)\x { 53 }"""))
        assertEquals(Hir.char(' '), p("""(?x)\ """))
    }

    @Test
    fun okComments() {
        val pat = """(?x)
# This is comment 1.
foo # This is comment 2.
  # This is comment 3.
bar
# This is comment 4."""
        assertEquals(
            Hir.concat(
                mutableListOf(
                    Hir.char('f'),
                    Hir.char('o'),
                    Hir.char('o'),
                    Hir.char('b'),
                    Hir.char('a'),
                    Hir.char('r'),
                ),
            ),
            p(pat),
        )
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

    @Test
    fun errVerbatim() {
        assertEquals(ERR_CLASS_UNCLOSED_AFTER_DASH, perr("""(?x)[-#]"""))
        assertEquals(ERR_CLASS_UNCLOSED_AFTER_ITEM, perr("""(?x)[a """))
        assertEquals(ERR_CLASS_UNCLOSED_AFTER_DASH, perr("""(?x)[a- """))
        assertEquals(ERR_CLASS_UNCLOSED, perr("""(?x)[         """))
    }

    @Test
    fun regression454NestTooBig() {
        val pattern = """
        2(?:
          [45]\d{3}|
          7(?:
            1[0-267]|
            2[0-289]|
            3[0-29]|
            4[01]|
            5[1-3]|
            6[013]|
            7[0178]|
            91
          )|
          8(?:
            0[125]|
            [139][1-6]|
            2[0157-9]|
            41|
            6[1-35]|
            7[1-5]|
            8[1-8]|
            90
          )|
          9(?:
            0[0-2]|
            1[0-4]|
            2[568]|
            3[3-6]|
            5[5-7]|
            6[0167]|
            7[15]|
            8[0146-9]
          )
        )\d{4}
        """
        p(pattern)
    }

    @Test
    fun regression455TrailingDashIgnoreWhitespace() {
        p("(?x)[ / - ]")
        p("(?x)[ a - ]")
        p(
            """(?x)[
            a
            - ]
        """,
        )
        p(
            """(?x)[
            a # wat
            - ]
        """,
        )

        perr("(?x)[ / -")
        perr("(?x)[ / - ")
        perr(
            """(?x)[
            / -
        """,
        )
        perr(
            """(?x)[
            / - # wat
        """,
        )
    }

    @Test
    fun regressionCaptureIndices() {
        val got = p("""(a|ab|c|bcd){4,10}(d*)""")
        assertEquals(
            Hir.concat(
                mutableListOf(
                    Hir.repetition(
                        Repetition(
                            min = 4u,
                            max = 10u,
                            greedy = true,
                            sub =
                                cap(
                                    1u,
                                    Hir.alternation(
                                        mutableListOf(
                                            Hir.char('a'),
                                            Hir.concat(mutableListOf(Hir.char('a'), Hir.char('b'))),
                                            Hir.char('c'),
                                            Hir.concat(
                                                mutableListOf(
                                                    Hir.char('b'),
                                                    Hir.char('c'),
                                                    Hir.char('d'),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                        ),
                    ),
                    cap(
                        2u,
                        Hir.repetition(
                            Repetition(
                                min = 0u,
                                max = null,
                                greedy = true,
                                sub = Hir.char('d'),
                            ),
                        ),
                    ),
                ),
            ),
            got,
        )
    }
}
