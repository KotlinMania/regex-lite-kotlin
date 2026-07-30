// port-lint: source src/utf8.rs
package io.github.kotlinmania.regexlite

/**
 * Returns true if and only if the given byte is considered a word character.
 * This only applies to ASCII.
 */
internal fun isWordByte(b: Byte): Boolean = WORD[b.toInt() and 0xFF]

private val WORD: BooleanArray =
    run {
        // FIXME: Use asUsize() once const functions in traits are stable.
        val set = BooleanArray(256)
        set['_'.code] = true

        var byte = '0'.code
        while (byte <= '9'.code) {
            set[byte] = true
            byte += 1
        }
        byte = 'A'.code
        while (byte <= 'Z'.code) {
            set[byte] = true
            byte += 1
        }
        byte = 'a'.code
        while (byte <= 'z'.code) {
            set[byte] = true
            byte += 1
        }
        set
    }

/** The accept state index. When we enter this state, we know we've found a
 * valid Unicode scalar value. */
private const val ACCEPT: Int = 12

/** The reject state index. When we enter this state, we know that we've found
 * invalid UTF-8. */
private const val REJECT: Int = 0

/**
 * Like [decode], but automatically converts the `null` case to the
 * replacement codepoint.
 *
 * The returned codepoint is a Unicode scalar value packed into an [Int].
 */
internal fun decodeLossy(slice: ByteArray): Pair<Int, Int> {
    val (ch, size) = decode(slice)
    return if (ch != null) Pair(ch, size) else Pair(0xFFFD, size)
}

/**
 * UTF-8 decode a single Unicode scalar value from the beginning of a slice.
 *
 * When successful, the corresponding Unicode scalar value (as an [Int]
 * codepoint) is returned along with the number of bytes it was encoded with.
 * The number of bytes consumed for a successful decode is always between 1
 * and 4, inclusive.
 *
 * When unsuccessful, `null` is returned along with the number of bytes that
 * make up a maximal prefix of a valid UTF-8 code unit sequence. In this case,
 * the number of bytes consumed is always between 0 and 3, inclusive, where
 * 0 is only returned when [slice] is empty.
 */
internal fun decode(slice: ByteArray): Pair<Int?, Int> {
    if (slice.isEmpty()) {
        return Pair(null, 0)
    }
    val b0 = slice[0].toInt() and 0xFF
    if (b0 <= 0x7F) {
        return Pair(b0, 1)
    }

    var state = ACCEPT
    var cp = 0
    var i = 0
    while (i < slice.size) {
        val step = decodeStep(state, cp, slice[i])
        state = step.first
        cp = step.second
        i += 1

        if (state == ACCEPT) {
            // OK since decodeStep guarantees that cp is a valid Unicode
            // scalar value in an ACCEPT state.
            //
            // We don't have to use safe code here, but do so because perf
            // isn't our primary objective in regex-lite.
            return Pair(cp, i)
        } else if (state == REJECT) {
            // At this point, we always want to advance at least one byte.
            return Pair(null, maxOf(1, i - 1))
        }
    }
    return Pair(null, i)
}

/** Transitions to the next state and updates `cp` while it does. */
private fun decodeStep(state: Int, cp: Int, b: Byte): Pair<Int, Int> {
    // Splits the space of all bytes into equivalence classes, such that
    // any byte in the same class can never discriminate between whether a
    // particular sequence is valid UTF-8 or not.
    val byte = b.toInt() and 0xFF
    val cls = CLASSES[byte].toInt() and 0xFF
    val newCp =
        if (state == ACCEPT) {
            (0xFF ushr cls) and byte
        } else {
            (byte and 0b111111) or (cp shl 6)
        }
    val newState = STATES_FORWARD[state + cls].toInt() and 0xFF
    return Pair(newState, newCp)
}

private val CLASSES: ByteArray =
    byteArrayOf(
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        1,
        1,
        1,
        1,
        1,
        1,
        1,
        1,
        1,
        1,
        1,
        1,
        1,
        1,
        1,
        1,
        9,
        9,
        9,
        9,
        9,
        9,
        9,
        9,
        9,
        9,
        9,
        9,
        9,
        9,
        9,
        9,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        7,
        8,
        8,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        2,
        10,
        3,
        3,
        3,
        3,
        3,
        3,
        3,
        3,
        3,
        3,
        3,
        3,
        4,
        3,
        3,
        11,
        6,
        6,
        6,
        5,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
        8,
    )

// A state machine taken from `bstr` which was in turn adapted from:
// https://bjoern.hoehrmann.de/utf-8/decoder/dfa/
private val STATES_FORWARD: ByteArray =
    byteArrayOf(
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        12,
        0,
        24,
        36,
        60,
        96,
        84,
        0,
        0,
        0,
        48,
        72,
        0,
        12,
        0,
        0,
        0,
        0,
        0,
        12,
        0,
        12,
        0,
        0,
        0,
        24,
        0,
        0,
        0,
        0,
        0,
        24,
        0,
        24,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        24,
        0,
        0,
        0,
        0,
        0,
        24,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        24,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        36,
        0,
        36,
        0,
        0,
        0,
        36,
        0,
        0,
        0,
        0,
        0,
        36,
        0,
        36,
        0,
        0,
        0,
        36,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
    )
