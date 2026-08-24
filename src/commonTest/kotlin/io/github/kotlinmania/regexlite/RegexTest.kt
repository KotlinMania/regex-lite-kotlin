// port-lint: tests string.rs
package io.github.kotlinmania.regexlite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RegexTest {
    @Test
    fun testBasicMatch() {
        val re = Regex("""\d+""")
        assertTrue(re.isMatch("abc 123 def"))
        assertFalse(re.isMatch("abc def"))
    }

    @Test
    fun testFind() {
        val re = Regex("""\d+""")
        val m = re.find("abc 123 def")
        assertNotNull(m)
        assertEquals(4, m.start)
        assertEquals(7, m.end)
        assertEquals(4 until 7, m.range)
        assertEquals("123", m.asStr())
        assertEquals(3, m.len())
        assertFalse(m.isEmpty())
    }

    @Test
    fun testFindIter() {
        val re = Regex("""\w+""")
        val matches = re.findIter("foo bar baz").map { it.asStr() }.toList()
        assertEquals(listOf("foo", "bar", "baz"), matches)
    }

    @Test
    fun testCaptures() {
        val re = Regex("""(?<year>\d{4})-(?<month>\d{2})-(?<day>\d{2})""")
        val caps = re.captures("2023-08-25")
        assertNotNull(caps)
        assertEquals("2023-08-25", caps[0]?.asStr())
        assertEquals("2023", caps[1]?.asStr())
        assertEquals("08", caps[2]?.asStr())
        assertEquals("25", caps[3]?.asStr())

        assertEquals("2023", caps.name("year")?.asStr())
        assertEquals("08", caps["month"]?.asStr())
        assertEquals("25", caps["day"]?.asStr())

        assertEquals(4, caps.len())
        val (whole, groups) = caps.extract()
        assertEquals("2023-08-25", whole)
        assertEquals(listOf("2023", "08", "25"), groups)
    }

    @Test
    fun testCapturesIter() {
        val re = Regex("""(\w+):(\d+)""")
        val text = "alice:100 bob:200 charlie:300"
        val results =
            re
                .capturesIter(text)
                .map {
                    Pair(it[1]?.asStr(), it[2]?.asStr()?.toInt())
                }.toList()
        assertEquals(
            listOf(
                Pair("alice", 100),
                Pair("bob", 200),
                Pair("charlie", 300),
            ),
            results,
        )
    }

    @Test
    fun testReplace() {
        val re = Regex("""\d+""")
        assertEquals("abc NUM def 456", re.replace("abc 123 def 456", "NUM"))
        assertEquals("abc NUM def NUM", re.replaceAll("abc 123 def 456", "NUM"))
    }

    @Test
    fun testReplaceWithExpansion() {
        val re = Regex("""(?<first>\w+)\s+(?<last>\w+)""")
        val result = re.replaceAll("John Doe, Jane Smith", "\$last, \$first")
        assertEquals("Doe, John, Smith, Jane", result)
    }

    @Test
    fun testReplaceWithFunction() {
        val re = Regex("""\d+""")
        val result =
            re.replaceAll("1 2 3 4") { caps ->
                val num = caps[0]!!.asStr().toInt()
                (num * 2).toString()
            }
        assertEquals("2 4 6 8", result)
    }

    @Test
    fun testReplaceNoExpand() {
        val re = Regex("""\w+""")
        val result = re.replaceAll("foo bar", NoExpand("\$1"))
        assertEquals("\$1 \$1", result)
    }

    @Test
    fun testSplit() {
        val re = Regex("""\s+""")
        val parts = re.split("Mary had a little lamb").toList()
        assertEquals(listOf("Mary", "had", "a", "little", "lamb"), parts)
    }

    @Test
    fun testSplitN() {
        val re = Regex("""\s+""")
        assertEquals(
            listOf("Mary", "had", "a little lamb"),
            re.splitn("Mary had a little lamb", 3).toList(),
        )
        assertEquals(
            emptyList<String>(),
            re.splitn("Mary had a little lamb", 0).toList(),
        )
        assertEquals(
            listOf("Mary had a little lamb"),
            re.splitn("Mary had a little lamb", 1).toList(),
        )
    }

    @Test
    fun testBuilderFlags() {
        val ci = RegexBuilder("abc").caseInsensitive(true).build().getOrThrow()
        assertTrue(ci.isMatch("ABC"))
        assertTrue(ci.isMatch("abc"))
        assertFalse(ci.isMatch("abd"))

        val ml = RegexBuilder("^foo").multiLine(true).build().getOrThrow()
        val m = ml.find("bar\nfoo")
        assertNotNull(m)
        assertEquals(4, m.start)

        val dot = RegexBuilder("a.b").dotMatchesNewLine(true).build().getOrThrow()
        assertTrue(dot.isMatch("a\nb"))

        val ws = RegexBuilder("a  b").ignoreWhitespace(true).build().getOrThrow()
        assertTrue(ws.isMatch("ab"))

        val sg = RegexBuilder("a+").swapGreed(true).build().getOrThrow()
        assertEquals("a", sg.find("aaaa")?.asStr())
    }

    @Test
    fun testEscape() {
        assertEquals("""\[a\-z\]\+""", escape("[a-z]+"))
        val re = Regex(escape("a.b*c?"))
        assertTrue(re.isMatch("a.b*c?"))
        assertFalse(re.isMatch("axbxc"))
    }

    @Test
    fun testLimits() {
        val sizeRes = RegexBuilder("a{10000}").sizeLimit(100).build()
        assertTrue(sizeRes.isFailure)

        val nestRes = RegexBuilder("((((((((((a))))))))))").nestLimit(3u).build()
        assertTrue(nestRes.isFailure)
    }

    @Test
    fun testUtf8MultiByteMatching() {
        val re = Regex("☃")
        val haystack = "hello ☃ world"
        val m = re.find(haystack)
        assertNotNull(m)
        assertEquals(6, m.start)
        assertEquals(9, m.end)
        assertEquals("☃", m.asStr())

        val split = re.split(haystack).toList()
        assertEquals(listOf("hello ", " world"), split)
    }

    @Test
    fun testShortestMatch() {
        val re = Regex("a+")
        assertEquals(1, re.shortestMatch("aaaa"))
    }

    @Test
    fun testCaptureLocations() {
        val re = Regex("""(\w+)\s+(\d+)""")
        val locs = re.captureLocations()
        val m = re.capturesRead(locs, "foo 123")
        assertNotNull(m)
        assertEquals(0, m.start)
        assertEquals(7, m.end)
        assertEquals(Pair(0, 7), locs.get(0))
        assertEquals(Pair(0, 3), locs.get(1))
        assertEquals(Pair(4, 7), locs.get(2))
    }

    @Test
    fun testCaptureNamesAndLen() {
        val re = Regex("""(?<name>\w+)\s+(?<age>\d+)""")
        assertEquals(3, re.capturesLen())
        assertEquals(listOf(null, "name", "age"), re.captureNames())
        assertEquals(3, re.staticCapturesLen())
    }

    @Test
    fun testEmptyRegex() {
        val re = Regex("")
        val matches = re.findIter("abc").map { it.start to it.end }.toList()
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2, 3 to 3), matches)
    }
}
