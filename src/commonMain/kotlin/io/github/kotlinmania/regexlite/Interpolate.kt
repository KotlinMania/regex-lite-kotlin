// port-lint: source interpolate.rs
package io.github.kotlinmania.regexlite

/**
 * Accepts a replacement string and interpolates capture references with their
 * corresponding values.
 *
 * [append] should be a function that appends the string value of a capture
 * group at a particular index to the string builder given. If the capture group
 * index is invalid, then nothing should be appended.
 *
 * [nameToIndex] should be a function that maps a capture group name to a
 * capture group index. If the given name doesn't exist, then `null` should
 * be returned.
 *
 * Finally, [dst] is where the final interpolated contents should be written.
 * If [replacement] contains no capture group references, then [dst] will be
 * equivalent to [replacement].
 */
internal fun interpolateString(
    replacement: String,
    append: (Int, StringBuilder) -> Unit,
    nameToIndex: (String) -> Int?,
    dst: StringBuilder,
) {
    var repBytes = replacement.encodeToByteArray()
    while (repBytes.isNotEmpty()) {
        val dollarIndex = repBytes.indexOf('$'.code.toByte())
        if (dollarIndex == -1) {
            dst.append(repBytes.decodeToString())
            return
        }
        if (dollarIndex > 0) {
            dst.append(repBytes.decodeToString(0, dollarIndex))
            repBytes = repBytes.copyOfRange(dollarIndex, repBytes.size)
        }
        // Handle escaping of '$'.
        if (repBytes.size > 1 && repBytes[1] == '$'.code.toByte()) {
            dst.append('$')
            repBytes = repBytes.copyOfRange(2, repBytes.size)
            continue
        }
        val capRef = findCapRef(repBytes)
        if (capRef == null) {
            dst.append('$')
            repBytes = repBytes.copyOfRange(1, repBytes.size)
            continue
        }
        repBytes = repBytes.copyOfRange(capRef.end, repBytes.size)
        when (val ref = capRef.cap) {
            is Ref.Number -> append(ref.value, dst)
            is Ref.Named -> {
                val index = nameToIndex(ref.name)
                if (index != null) {
                    append(index, dst)
                }
            }
        }
    }
}

/**
 * `CaptureRef` represents a reference to a capture group inside some text.
 * The reference is either a capture group name or a number.
 *
 * It is also tagged with the position in the text following the
 * capture reference.
 */
internal class CaptureRef(
    val cap: Ref,
    val end: Int,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is CaptureRef && cap == other.cap && end == other.end)

    override fun hashCode(): Int = 31 * cap.hashCode() + end

    override fun toString(): String = "CaptureRef(cap=$cap, end=$end)"
}

/**
 * A reference to a capture group in some text.
 *
 * e.g., `$2`, `$foo`, `${foo}`.
 */
internal sealed class Ref {
    data class Named(
        val name: String,
    ) : Ref()

    data class Number(
        val value: Int,
    ) : Ref()
}

/**
 * Parses a possible reference to a capture group name in the given text,
 * starting at the beginning of [replacement].
 *
 * If no such valid reference could be found, `null` is returned.
 */
internal fun findCapRef(replacement: ByteArray): CaptureRef? {
    if (replacement.size <= 1 || replacement[0] != '$'.code.toByte()) {
        return null
    }
    var i = 1
    if (replacement[i] == '{'.code.toByte()) {
        return findCapRefBraced(replacement, i + 1)
    }
    var capEnd = i
    while (capEnd < replacement.size && isValidCapByte(replacement[capEnd])) {
        capEnd += 1
    }
    if (capEnd == i) {
        return null
    }
    val cap = replacement.decodeToString(i, capEnd)
    val num = cap.toIntOrNull()
    val ref =
        if (num != null && num >= 0 && cap.all { it in '0'..'9' }) {
            Ref.Number(num)
        } else {
            Ref.Named(cap)
        }
    return CaptureRef(ref, capEnd)
}

/**
 * Looks for a braced reference, e.g., `${foo1}`. This assumes that an opening
 * brace has been found at `start - 1` in [rep]. This then looks for a closing
 * brace and returns the capture reference within the brace.
 */
private fun findCapRefBraced(rep: ByteArray, start: Int): CaptureRef? {
    var i = start
    while (i < rep.size && rep[i] != '}'.code.toByte()) {
        i += 1
    }
    if (i >= rep.size || rep[i] != '}'.code.toByte()) {
        return null
    }
    val cap =
        try {
            rep.decodeToString(start, i)
        } catch (_: Exception) {
            return null
        }
    val num = cap.toIntOrNull()
    val ref =
        if (num != null && num >= 0 && cap.all { it in '0'..'9' }) {
            Ref.Number(num)
        } else {
            Ref.Named(cap)
        }
    return CaptureRef(ref, i + 1)
}

/**
 * Returns true if and only if the given byte is allowed in a capture name
 * written in non-brace form.
 */
private fun isValidCapByte(b: Byte): Boolean {
    val v = b.toInt() and 0xFF
    return (v in '0'.code..'9'.code) ||
        (v in 'a'.code..'z'.code) ||
        (v in 'A'.code..'Z'.code) ||
        (v == '_'.code)
}
