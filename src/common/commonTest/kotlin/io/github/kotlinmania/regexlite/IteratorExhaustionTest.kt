package io.github.kotlinmania.regexlite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IteratorExhaustionTest {
    @Test
    fun allExportedIteratorAdaptersRejectEmptyNext() {
        val iterators: List<Iterator<*>> =
            listOf(
                Matches(emptySequence()),
                CaptureMatches(emptySequence()),
                Split(emptySequence()),
                SplitN(emptySequence()),
                CaptureNames(emptySequence()),
                SubCaptureMatches(emptySequence()),
            )
        for (iterator in iterators) {
            assertFalse(iterator.hasNext())
            assertFailsWith<NoSuchElementException> { iterator.next() }
        }
    }

    @Test
    fun nullablePayloadsAreNotTreatedAsExhaustion() {
        val names = CaptureNames(sequenceOf(null))
        assertTrue(names.hasNext())
        assertTrue(names.hasNext())
        assertNull(names.next())
        assertFalse(names.hasNext())
        assertFailsWith<NoSuchElementException> { names.next() }

        val matches = SubCaptureMatches(sequenceOf(null))
        assertTrue(matches.hasNext())
        assertTrue(matches.hasNext())
        assertNull(matches.next())
        assertFalse(matches.hasNext())
        assertFailsWith<NoSuchElementException> { matches.next() }
    }

    @Test
    fun splitAdaptersPreserveOrderAndRepeatedHasNext() {
        val iterators: List<Iterator<String>> =
            listOf(
                Split(sequenceOf("first", "second")),
                SplitN(sequenceOf("first", "second")),
            )
        for (iterator in iterators) {
            assertTrue(iterator.hasNext())
            assertTrue(iterator.hasNext())
            assertEquals("first", iterator.next())
            assertTrue(iterator.hasNext())
            assertEquals("second", iterator.next())
            assertFalse(iterator.hasNext())
            assertFailsWith<NoSuchElementException> { iterator.next() }
        }
    }
}
