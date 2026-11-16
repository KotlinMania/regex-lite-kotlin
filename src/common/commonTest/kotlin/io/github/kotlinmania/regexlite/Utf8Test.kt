// port-lint: tests utf8.rs
package io.github.kotlinmania.regexlite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Utf8Test {
    @Test
    fun decodeValid() {
        fun d(start: String): List<Int> {
            var bytes = start.encodeToByteArray()
            val chars = mutableListOf<Int>()
            while (bytes.isNotEmpty()) {
                val (ch, size) = decode(bytes)
                bytes = bytes.copyOfRange(size, bytes.size)
                chars.add(ch!!)
            }
            return chars
        }

        assertEquals(listOf(0x2603), d("☃"))
        assertEquals(listOf(0x2603, 0x2603), d("☃☃"))
        assertEquals(
            listOf(0x03B1, 0x03B2, 0x03B3, 0x03B4, 0x03B5),
            d("αβγδε"),
        )
        assertEquals(listOf(0x2603, 0x26C4, 0x26C7), d("☃⛄⛇"))
        assertEquals(
            listOf(0x1D5EE, 0x1D5EF, 0x1D5F0, 0x1D5F1, 0x1D5F2),
            d("𝗮𝗯𝗰𝗱𝗲"),
        )
    }

    @Test
    fun decodeInvalid() {
        run {
            val (ch, size) = decode(byteArrayOf())
            assertNull(ch)
            assertEquals(0, size)
        }

        run {
            val (ch, size) = decode(bytesOf(0xFF))
            assertNull(ch)
            assertEquals(1, size)
        }

        run {
            val (ch, size) = decode(bytesOf(0xCE, 0xF0))
            assertNull(ch)
            assertEquals(1, size)
        }

        run {
            val (ch, size) = decode(bytesOf(0xE2, 0x98, 0xF0))
            assertNull(ch)
            assertEquals(2, size)
        }

        run {
            val (ch, size) = decode(bytesOf(0xF0, 0x9D, 0x9D))
            assertNull(ch)
            assertEquals(3, size)
        }

        run {
            val (ch, size) = decode(bytesOf(0xF0, 0x9D, 0x9D, 0xF0))
            assertNull(ch)
            assertEquals(3, size)
        }

        run {
            val (ch, size) = decode(bytesOf(0xF0, 0x82, 0x82, 0xAC))
            assertNull(ch)
            assertEquals(1, size)
        }

        run {
            val (ch, size) = decode(bytesOf(0xED, 0xA0, 0x80))
            assertNull(ch)
            assertEquals(1, size)
        }

        run {
            val (ch, size) = decode(bytesOf(0xCE, 'a'.code))
            assertNull(ch)
            assertEquals(1, size)
        }

        run {
            val (ch, size) = decode(bytesOf(0xE2, 0x98, 'a'.code))
            assertNull(ch)
            assertEquals(2, size)
        }

        run {
            val (ch, size) = decode(bytesOf(0xF0, 0x9D, 0x9C, 'a'.code))
            assertNull(ch)
            assertEquals(3, size)
        }
    }

    @Test
    fun decodeLossily() {
        run {
            val (ch, size) = decodeLossy(byteArrayOf())
            assertEquals(0xFFFD, ch)
            assertEquals(0, size)
        }

        run {
            val (ch, size) = decodeLossy(bytesOf(0xFF))
            assertEquals(0xFFFD, ch)
            assertEquals(1, size)
        }

        run {
            val (ch, size) = decodeLossy(bytesOf(0xCE, 0xF0))
            assertEquals(0xFFFD, ch)
            assertEquals(1, size)
        }

        run {
            val (ch, size) = decodeLossy(bytesOf(0xE2, 0x98, 0xF0))
            assertEquals(0xFFFD, ch)
            assertEquals(2, size)
        }

        run {
            val (ch, size) = decodeLossy(bytesOf(0xF0, 0x9D, 0x9D, 0xF0))
            assertEquals(0xFFFD, ch)
            assertEquals(3, size)
        }

        run {
            val (ch, size) = decodeLossy(bytesOf(0xF0, 0x82, 0x82, 0xAC))
            assertEquals(0xFFFD, ch)
            assertEquals(1, size)
        }

        run {
            val (ch, size) = decodeLossy(bytesOf(0xED, 0xA0, 0x80))
            assertEquals(0xFFFD, ch)
            assertEquals(1, size)
        }

        run {
            val (ch, size) = decodeLossy(bytesOf(0xCE, 'a'.code))
            assertEquals(0xFFFD, ch)
            assertEquals(1, size)
        }

        run {
            val (ch, size) = decodeLossy(bytesOf(0xE2, 0x98, 'a'.code))
            assertEquals(0xFFFD, ch)
            assertEquals(2, size)
        }

        run {
            val (ch, size) = decodeLossy(bytesOf(0xF0, 0x9D, 0x9C, 'a'.code))
            assertEquals(0xFFFD, ch)
            assertEquals(3, size)
        }
    }

    private fun bytesOf(vararg bytes: Int): ByteArray =
        ByteArray(bytes.size) { bytes[it].toByte() }
}
