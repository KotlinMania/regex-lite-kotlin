package io.github.kotlinmania.regexlite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureStorageRegressionTest {
    @Test
    fun capturePresenceDistinguishesEmptyAtZero() {
        val captures = assertNotNull(Regex("(a)?()").captures(""))
        assertNull(captures[1])
        val empty = assertNotNull(captures[2])
        assertEquals(0, empty.start)
        assertEquals(0, empty.end)
        assertEquals("", empty.asStr())
        val alternative = assertNotNull(Regex("(a)|(b)").captures("b"))
        assertNull(alternative[1])
        assertEquals("b", alternative[2]?.asStr())
    }

    @Test
    fun captureSnapshotsAndReusedLocationsKeepTheirBoundaries() {
        val matches = Regex("(a)|(b)").capturesIter("ab").iterator()
        val first = matches.next()
        val second = matches.next()
        assertEquals("a", first[1]?.asStr())
        assertNull(first[2])
        assertNull(second[1])
        assertEquals("b", second[2]?.asStr())
        val regex = Regex("(a)?b")
        val locations = regex.captureLocations()
        assertNotNull(regex.capturesRead(locations, "ab"))
        assertEquals(0 to 1, locations.get(1))
        assertNotNull(regex.capturesRead(locations, "b"))
        assertNull(locations.get(1))
        assertEquals(0 to 1, locations.get(0))
        val unicode = assertNotNull(Regex("(☃)").captures("x☃"))[1]
        assertEquals(1, unicode?.start)
        assertEquals(4, unicode?.end)
    }

    @Test
    fun nullableStoragePreservesCopiesAndArrayHashSemantics() {
        val slots = CaptureSlots(3)
        slots[1] = 7
        slots[2] = 0
        assertEquals(30008, slots.contentHashCode())
        val copy = slots.copyOf()
        val range = slots.copyOfRange(1, 3)
        slots[1] = null
        slots[2] = Int.MIN_VALUE
        assertEquals(7, copy[1])
        assertEquals(0, copy[2])
        assertEquals(7, range[0])
        assertEquals(0, range[1])
        slots[2] = null
        assertTrue(slots.contentEquals(CaptureSlots(3)))
        assertEquals(CaptureSlots(3).contentHashCode(), slots.contentHashCode())
        assertEquals(1, CaptureSlots(0).contentHashCode())
        val absent = CaptureSlots(1)
        val zero = CaptureSlots(1)
        zero[0] = 0
        assertFalse(absent.contentEquals(zero))
        assertEquals(absent.contentHashCode(), zero.contentHashCode())
        for (value in listOf(Int.MIN_VALUE, -1, 0, Int.MAX_VALUE)) {
            zero[0] = value
            assertEquals(value, zero[0])
        }
    }

    @Test
    fun publicLocationsKeepEqualityAndHashBehavior() {
        val regex = Regex("(a)?")
        val first = regex.captureLocations()
        val second = regex.captureLocations()
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotNull(regex.capturesRead(first, "a"))
        assertNotNull(regex.capturesRead(second, "a"))
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun concatenationAlternationAndRepetitionsRemainIndependent() {
        val alternatives = Regex("ab|cd")
        assertEquals("ab", alternatives.find("ab")?.asStr())
        assertEquals("cd", alternatives.find("cd")?.asStr())
        assertEquals("aaa", Regex("a+").find("aaa")?.asStr())
        assertEquals("aa", Regex("a{2}").find("aaa")?.asStr())
        assertEquals("abab", Regex("(ab){2}|cd+").find("abab")?.asStr())
        assertEquals("cdd", Regex("(ab){2}|cd+").find("cdd")?.asStr())
    }
}
