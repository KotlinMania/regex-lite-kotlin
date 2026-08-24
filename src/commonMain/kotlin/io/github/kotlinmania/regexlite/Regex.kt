// port-lint: source string.rs
package io.github.kotlinmania.regexlite

import io.github.kotlinmania.regexlite.pikevm.Cache
import io.github.kotlinmania.regexlite.pikevm.PikeVM

/**
 * A compiled regular expression for searching strings.
 */
class Regex internal constructor(
    internal val pikevm: PikeVM,
    internal val pool: Pool<Cache>,
) {
    companion object {
        /**
         * Compiles a regular expression using default options.
         */
        fun from(pattern: String): Result<Regex> = RegexBuilder(pattern).build()
    }

    /**
     * Compiles a regular expression using default options, throwing [IllegalArgumentException]
     * if the pattern is invalid.
     */
    constructor(pattern: String) : this(
        RegexBuilder(pattern).build().getOrThrow(),
    )

    private constructor(regex: Regex) : this(regex.pikevm, regex.pool)

    /**
     * Returns true if and only if there is a match for the regex in the
     * haystack given.
     */
    fun isMatch(haystack: String): Boolean = isMatchAt(haystack, 0)

    /**
     * Returns the same as [isMatch], but starts the search at the given byte offset.
     */
    fun isMatchAt(haystack: String, start: Int): Boolean {
        val hayBytes = haystack.encodeToByteArray()
        require(start <= hayBytes.size) { "start offset $start out of bounds ${hayBytes.size}" }
        val guard = pool.get()
        try {
            return pikevm.search(
                cache = guard.value,
                haystack = hayBytes,
                start = start,
                end = hayBytes.size,
                earliest = true,
                slots = arrayOfNulls(0),
            )
        } finally {
            guard.release()
        }
    }

    /**
     * Returns the start and end byte range of the leftmost-first match in the
     * haystack given.
     */
    fun find(haystack: String): Match? = findAt(haystack, 0)

    /**
     * Returns the same as [find], but starts the search at the given byte offset.
     */
    fun findAt(haystack: String, start: Int): Match? {
        val hayBytes = haystack.encodeToByteArray()
        require(start <= hayBytes.size) { "start offset $start out of bounds ${hayBytes.size}" }
        val guard = pool.get()
        val slots = arrayOfNulls<Int>(2)
        try {
            val matched =
                pikevm.search(
                    cache = guard.value,
                    haystack = hayBytes,
                    start = start,
                    end = hayBytes.size,
                    earliest = false,
                    slots = slots,
                )
            if (!matched) return null
            return Match(haystack, slots[0]!!, slots[1]!!)
        } finally {
            guard.release()
        }
    }

    /**
     * Returns an iterator that yields successive non-overlapping matches in
     * the given haystack.
     */
    fun findIter(haystack: String): Sequence<Match> {
        val hayBytes = haystack.encodeToByteArray()
        return sequence {
            val guard = pool.get()
            try {
                val it = pikevm.findIter(guard.value, hayBytes)
                while (it.hasNext()) {
                    val m = it.next()
                    yield(Match(haystack, m.first, m.second))
                }
            } finally {
                guard.release()
            }
        }
    }

    /**
     * Returns the capture groups for the leftmost-first match in the
     * haystack given.
     */
    fun captures(haystack: String): Captures? = capturesAt(haystack, 0)

    /**
     * Returns the same as [captures], but starts the search at the given byte offset.
     */
    fun capturesAt(haystack: String, start: Int): Captures? {
        val hayBytes = haystack.encodeToByteArray()
        require(start <= hayBytes.size) { "start offset $start out of bounds ${hayBytes.size}" }
        val locs = captureLocations()
        val guard = pool.get()
        try {
            val matched =
                pikevm.search(
                    cache = guard.value,
                    haystack = hayBytes,
                    start = start,
                    end = hayBytes.size,
                    earliest = false,
                    slots = locs.slots,
                )
            if (!matched) return null
            return Captures(haystack, locs, pikevm)
        } finally {
            guard.release()
        }
    }

    /**
     * Returns an iterator that yields successive non-overlapping matches in
     * the given haystack. The iterator yields values of type [Captures].
     */
    fun capturesIter(haystack: String): Sequence<Captures> {
        val hayBytes = haystack.encodeToByteArray()
        return sequence {
            val guard = pool.get()
            try {
                val it = pikevm.capturesIter(guard.value, hayBytes)
                while (it.hasNext()) {
                    val slots = it.next()
                    yield(Captures(haystack, CaptureLocations(slots), pikevm))
                }
            } finally {
                guard.release()
            }
        }
    }

    /**
     * Returns an iterator of substrings of the haystack given, delimited by a
     * match of the regex.
     */
    fun split(haystack: String): Sequence<String> {
        val hayBytes = haystack.encodeToByteArray()
        return sequence {
            var last = 0
            for (m in findIter(haystack)) {
                yield(hayBytes.decodeToString(last, m.start))
                last = m.end
            }
            if (last <= hayBytes.size) {
                yield(hayBytes.decodeToString(last, hayBytes.size))
            }
        }
    }

    /**
     * Returns an iterator of at most [limit] substrings of the haystack given,
     * delimited by a match of the regex. (A [limit] of 0 returns no substrings.)
     */
    fun splitn(haystack: String, limit: Int): Sequence<String> {
        if (limit == 0) return emptySequence()
        val hayBytes = haystack.encodeToByteArray()
        return sequence {
            var remaining = limit
            var last = 0
            for (m in findIter(haystack)) {
                if (remaining == 1) break
                yield(hayBytes.decodeToString(last, m.start))
                last = m.end
                remaining--
            }
            if (last <= hayBytes.size) {
                yield(hayBytes.decodeToString(last, hayBytes.size))
            }
        }
    }

    /**
     * Replaces the leftmost-first match in the given haystack with the
     * replacement string.
     */
    fun replace(haystack: String, rep: String): String = replacen(haystack, 1, rep)

    /**
     * Replaces the leftmost-first match in the given haystack using a replacement function.
     */
    fun replace(haystack: String, rep: (Captures) -> String): String = replacen(haystack, 1, rep)

    /**
     * Replaces the leftmost-first match in the given haystack using a [Replacer].
     */
    fun replace(haystack: String, rep: Replacer): String = replacen(haystack, 1, rep)

    /**
     * Replaces all non-overlapping matches in the haystack with the replacement string.
     */
    fun replaceAll(haystack: String, rep: String): String = replacen(haystack, 0, rep)

    /**
     * Replaces all non-overlapping matches in the haystack using a replacement function.
     */
    fun replaceAll(haystack: String, rep: (Captures) -> String): String = replacen(haystack, 0, rep)

    /**
     * Replaces all non-overlapping matches in the haystack using a [Replacer].
     */
    fun replaceAll(haystack: String, rep: Replacer): String = replacen(haystack, 0, rep)

    /**
     * Replaces at most [limit] non-overlapping matches in the haystack with [rep].
     * If [limit] is 0, all matches are replaced.
     */
    fun replacen(haystack: String, limit: Int, rep: String): String = replacen(haystack, limit, StringReplacer(rep))

    /**
     * Replaces at most [limit] non-overlapping matches in the haystack with [rep].
     * If [limit] is 0, all matches are replaced.
     */
    fun replacen(haystack: String, limit: Int, rep: (Captures) -> String): String = replacen(haystack, limit, FnReplacer(rep))

    /**
     * Replaces at most [limit] non-overlapping matches in the haystack with [rep].
     * If [limit] is 0, all matches are replaced.
     */
    fun replacen(haystack: String, limit: Int, rep: Replacer): String {
        val noExp = rep.noExpansion()
        val hayBytes = haystack.encodeToByteArray()
        if (noExp != null) {
            val matches = findIter(haystack).toList()
            if (matches.isEmpty()) return haystack
            val sb = StringBuilder(haystack.length)
            var lastMatch = 0
            for ((i, m) in matches.withIndex()) {
                sb.append(hayBytes.decodeToString(lastMatch, m.start))
                sb.append(noExp)
                lastMatch = m.end
                if (limit > 0 && i >= limit - 1) break
            }
            sb.append(hayBytes.decodeToString(lastMatch, hayBytes.size))
            return sb.toString()
        }

        val capsList = capturesIter(haystack).toList()
        if (capsList.isEmpty()) return haystack
        val sb = StringBuilder(haystack.length)
        var lastMatch = 0
        for ((i, cap) in capsList.withIndex()) {
            val m0 = cap.get(0) ?: continue
            sb.append(hayBytes.decodeToString(lastMatch, m0.start))
            rep.replaceAppend(cap, sb)
            lastMatch = m0.end
            if (limit > 0 && i >= limit - 1) break
        }
        sb.append(hayBytes.decodeToString(lastMatch, hayBytes.size))
        return sb.toString()
    }

    /**
     * Returns the end byte offset of the first match in the haystack given.
     */
    fun shortestMatch(haystack: String): Int? = shortestMatchAt(haystack, 0)

    /**
     * Returns the same as [shortestMatch], but starts the search at the given byte offset.
     */
    fun shortestMatchAt(haystack: String, start: Int): Int? {
        val hayBytes = haystack.encodeToByteArray()
        require(start <= hayBytes.size) { "start offset $start out of bounds ${hayBytes.size}" }
        val guard = pool.get()
        val slots = arrayOfNulls<Int>(2)
        try {
            val matched =
                pikevm.search(
                    cache = guard.value,
                    haystack = hayBytes,
                    start = start,
                    end = hayBytes.size,
                    earliest = true,
                    slots = slots,
                )
            if (!matched) return null
            return slots[1]
        } finally {
            guard.release()
        }
    }

    /**
     * This is like [captures], but writes the byte offsets of each capture group
     * match into the locations given.
     */
    fun capturesRead(locs: CaptureLocations, haystack: String): Match? =
        capturesReadAt(locs, haystack, 0)

    /**
     * Returns the same as [capturesRead], but starts the search at the given offset.
     */
    fun capturesReadAt(locs: CaptureLocations, haystack: String, start: Int): Match? {
        val hayBytes = haystack.encodeToByteArray()
        require(start <= hayBytes.size) { "start offset $start out of bounds ${hayBytes.size}" }
        val guard = pool.get()
        try {
            val matched =
                pikevm.search(
                    cache = guard.value,
                    haystack = hayBytes,
                    start = start,
                    end = hayBytes.size,
                    earliest = false,
                    slots = locs.slots,
                )
            if (!matched) return null
            val (s, e) = locs.get(0) ?: return null
            return Match(haystack, s, e)
        } finally {
            guard.release()
        }
    }

    /**
     * Returns the original string of this regex.
     */
    fun asStr(): String = pikevm.nfa.pattern

    /**
     * Returns an iterator over the capture names in this regex.
     */
    fun captureNames(): List<String?> = pikevm.nfa.captureNames()

    /**
     * Returns the number of capture groups in this regex.
     */
    fun capturesLen(): Int = pikevm.nfa.groupLen()

    /**
     * Returns the total number of capturing groups that appear in every possible match.
     */
    fun staticCapturesLen(): Int? =
        pikevm.nfa.staticExplicitCapturesLen?.let { it + 1 }

    /**
     * Returns a fresh allocated set of capture locations that can be reused.
     */
    fun captureLocations(): CaptureLocations {
        val len = pikevm.nfa.groupLen() * 2
        return CaptureLocations(arrayOfNulls(len))
    }

    override fun toString(): String = asStr()
}

/**
 * Represents a single match of a regex in a haystack.
 */
class Match(
    val haystack: String,
    val start: Int,
    val end: Int,
) {
    init {
        require(start <= end) { "start ($start) must be <= end ($end)" }
    }

    val range: IntRange get() = start until end

    fun isEmpty(): Boolean = start == end

    fun len(): Int = end - start

    fun asStr(): String {
        val bytes = haystack.encodeToByteArray()
        return bytes.decodeToString(start, end)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Match && haystack == other.haystack && start == other.start && end == other.end)

    override fun hashCode(): Int =
        31 * (31 * haystack.hashCode() + start) + end

    override fun toString(): String = asStr()
}

/**
 * A low level representation of the byte offsets of each capture group.
 */
class CaptureLocations internal constructor(
    internal val slots: Array<Int?>,
) {
    fun get(i: Int): Pair<Int, Int>? {
        val slot = i * 2
        if (slot + 1 >= slots.size) return null
        val start = slots[slot] ?: return null
        val end = slots[slot + 1] ?: return null
        return Pair(start, end)
    }

    fun len(): Int = slots.size / 2

    override fun equals(other: Any?): Boolean =
        this === other || (other is CaptureLocations && slots.contentEquals(other.slots))

    override fun hashCode(): Int = slots.contentHashCode()
}

/**
 * Represents the capture groups for a single match.
 */
class Captures internal constructor(
    val haystack: String,
    val slots: CaptureLocations,
    internal val pikevm: PikeVM,
) {
    operator fun get(i: Int): Match? {
        val (s, e) = slots.get(i) ?: return null
        return Match(haystack, s, e)
    }

    fun name(name: String): Match? {
        val i = pikevm.nfa.toIndex(name) ?: return null
        return get(i)
    }

    operator fun get(name: String): Match? = name(name)

    fun extract(): Pair<String, List<String>> {
        val staticLen =
            pikevm.nfa.staticExplicitCapturesLen
                ?: throw IllegalStateException("number of capture groups can vary in a match")
        val wholeMatch = get(0)?.asStr() ?: throw IllegalStateException("a match")
        val groupMatches =
            (1..staticLen).map { idx ->
                get(idx)?.asStr() ?: throw IllegalStateException("too few matching groups")
            }
        return Pair(wholeMatch, groupMatches)
    }

    fun expand(replacement: String, dst: StringBuilder) {
        interpolateString(
            replacement = replacement,
            append = { index, target ->
                val m = get(index) ?: return@interpolateString
                target.append(m.asStr())
            },
            nameToIndex = { name -> pikevm.nfa.toIndex(name) },
            dst = dst,
        )
    }

    fun iter(): List<Match?> {
        val count = len()
        return (0 until count).map { get(it) }
    }

    fun len(): Int = slots.len()

    override fun toString(): String {
        val sb = StringBuilder("Captures({")
        val names = pikevm.nfa.captureNames()
        for (i in 0 until len()) {
            if (i > 0) sb.append(", ")
            sb.append(i)
            val n = names.getOrNull(i)
            if (n != null) {
                sb.append("/\"").append(n).append('"')
            }
            sb.append(": ")
            val m = get(i)
            if (m == null) {
                sb.append("None")
            } else {
                sb.append("${m.start}..${m.end}/\"${m.asStr()}\"")
            }
        }
        sb.append("})")
        return sb.toString()
    }
}

/**
 * A trait for types that can be used to replace matches in a haystack.
 */
fun interface Replacer {
    fun replaceAppend(caps: Captures, dst: StringBuilder)

    fun noExpansion(): String? = null
}

internal class StringReplacer(
    private val rep: String,
) : Replacer {
    override fun replaceAppend(caps: Captures, dst: StringBuilder) {
        caps.expand(rep, dst)
    }

    override fun noExpansion(): String? = if ('$' in rep) null else rep
}

internal class FnReplacer(
    private val fn: (Captures) -> String,
) : Replacer {
    override fun replaceAppend(caps: Captures, dst: StringBuilder) {
        dst.append(fn(caps))
    }
}

/**
 * A helper type for forcing literal string replacement without expanding `$name`.
 */
class NoExpand(
    val text: String,
) : Replacer {
    override fun replaceAppend(caps: Captures, dst: StringBuilder) {
        dst.append(text)
    }

    override fun noExpansion(): String = text
}
