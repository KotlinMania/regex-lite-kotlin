// port-lint: source hir/mod.rs
package io.github.kotlinmania.regexlite.hir

import io.github.kotlinmania.regexlite.isWordByte

/**
 * Escapes all regular expression meta characters in [pattern].
 *
 * The string returned may be safely used as a literal in a regular
 * expression.
 */
public fun escape(pattern: String): String {
    val buf = StringBuilder(pattern.length)
    for (ch in pattern) {
        if (isMetaCharacter(ch)) {
            buf.append('\\')
        }
        buf.append(ch)
    }
    return buf.toString()
}

/**
 * Returns true if the given character has significance in a regex.
 */
internal fun isMetaCharacter(c: Char): Boolean =
    when (c) {
        '\\', '.', '+', '*', '?', '(', ')', '|', '[', ']', '{', '}', '^', '$', '#', '&', '-', '~' -> true
        else -> false
    }

/**
 * Returns true if the given character can be escaped in a regex.
 */
internal fun isEscapableCharacter(c: Char): Boolean {
    if (isMetaCharacter(c)) {
        return true
    }
    if (c.code > 0x7F) {
        return false
    }
    return when (c) {
        in '0'..'9', in 'A'..'Z', in 'a'..'z', '<', '>' -> false
        else -> true
    }
}

/**
 * The configuration for a regex parser.
 */
internal data class Config(
    var nestLimit: UInt = 50u,
    var flags: Flags = Flags(),
)

/**
 * Various flags that control the interpretation of the pattern.
 */
internal data class Flags(
    var caseInsensitive: Boolean = false,
    var multiLine: Boolean = false,
    var dotMatchesNewLine: Boolean = false,
    var swapGreed: Boolean = false,
    var crlf: Boolean = false,
    var ignoreWhitespace: Boolean = false,
)

internal class Hir(
    val kind: HirKind,
    val isStartAnchored: Boolean,
    val isMatchEmpty: Boolean,
    val staticExplicitCapturesLen: Int?,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is Hir &&
                    kind == other.kind &&
                    isStartAnchored == other.isStartAnchored &&
                    isMatchEmpty == other.isMatchEmpty &&
                    staticExplicitCapturesLen == other.staticExplicitCapturesLen
            )

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + isStartAnchored.hashCode()
        result = 31 * result + isMatchEmpty.hashCode()
        result = 31 * result + (staticExplicitCapturesLen?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "Hir(kind=$kind, isStartAnchored=$isStartAnchored, isMatchEmpty=$isMatchEmpty)"

    companion object {
        fun parse(config: Config, pattern: String): Result<Hir> =
            Parser(config, pattern).parse()

        fun fail(): Hir =
            Hir(
                kind = HirKind.Class(Class(mutableListOf())),
                isStartAnchored = false,
                isMatchEmpty = false,
                staticExplicitCapturesLen = 0,
            )

        fun empty(): Hir =
            Hir(
                kind = HirKind.Empty,
                isStartAnchored = false,
                isMatchEmpty = true,
                staticExplicitCapturesLen = 0,
            )

        fun char(ch: Char): Hir =
            Hir(
                kind = HirKind.Char(ch),
                isStartAnchored = false,
                isMatchEmpty = false,
                staticExplicitCapturesLen = 0,
            )

        fun classNode(cls: Class): Hir =
            Hir(
                kind = HirKind.Class(cls),
                isStartAnchored = false,
                isMatchEmpty = false,
                staticExplicitCapturesLen = 0,
            )

        fun look(look: Look): Hir =
            Hir(
                kind = HirKind.Look(look),
                isStartAnchored = look == Look.Start,
                isMatchEmpty = true,
                staticExplicitCapturesLen = 0,
            )

        fun repetition(rep: Repetition): Hir {
            if (rep.min == 0u && rep.max == 0u) {
                return empty()
            } else if (rep.min == 1u && rep.max == 1u) {
                return rep.sub
            }
            val isStartAnchored = rep.min > 0u && rep.sub.isStartAnchored
            val isMatchEmpty = rep.min == 0u || rep.sub.isMatchEmpty
            var staticExplicitCapturesLen = rep.sub.staticExplicitCapturesLen
            if (rep.min == 0u && (staticExplicitCapturesLen ?: 0) > 0) {
                staticExplicitCapturesLen = if (rep.max == 0u) 0 else null
            }
            return Hir(
                kind = HirKind.Repetition(rep),
                isStartAnchored = isStartAnchored,
                isMatchEmpty = isMatchEmpty,
                staticExplicitCapturesLen = staticExplicitCapturesLen,
            )
        }

        fun capture(cap: Capture): Hir {
            val isStartAnchored = cap.sub.isStartAnchored
            val isMatchEmpty = cap.sub.isMatchEmpty
            val staticExplicitCapturesLen =
                cap.sub.staticExplicitCapturesLen?.let {
                    if (it == Int.MAX_VALUE) Int.MAX_VALUE else it + 1
                }
            return Hir(
                kind = HirKind.Capture(cap),
                isStartAnchored = isStartAnchored,
                isMatchEmpty = isMatchEmpty,
                staticExplicitCapturesLen = staticExplicitCapturesLen,
            )
        }

        fun concat(subs: MutableList<Hir>): Hir {
            if (subs.isEmpty()) {
                return empty()
            } else if (subs.size == 1) {
                return subs.removeAt(0)
            }
            val isStartAnchored = subs[0].isStartAnchored
            var isMatchEmpty = true
            var staticExplicitCapturesLen: Int? = 0
            for (sub in subs) {
                isMatchEmpty = isMatchEmpty && sub.isMatchEmpty
                staticExplicitCapturesLen =
                    staticExplicitCapturesLen?.let { len1 ->
                        sub.staticExplicitCapturesLen?.let { len2 ->
                            if (len1 > Int.MAX_VALUE - len2) Int.MAX_VALUE else len1 + len2
                        }
                    }
            }
            return Hir(
                kind = HirKind.Concat(subs),
                isStartAnchored = isStartAnchored,
                isMatchEmpty = isMatchEmpty,
                staticExplicitCapturesLen = staticExplicitCapturesLen,
            )
        }

        fun alternation(subs: MutableList<Hir>): Hir {
            if (subs.isEmpty()) {
                return fail()
            } else if (subs.size == 1) {
                return subs.removeAt(0)
            }
            var isStartAnchored = subs.firstOrNull()?.isStartAnchored ?: false
            var isMatchEmpty = subs.firstOrNull()?.isMatchEmpty ?: false
            var staticExplicitCapturesLen: Int? = subs.firstOrNull()?.staticExplicitCapturesLen
            for (sub in subs) {
                isStartAnchored = isStartAnchored && sub.isStartAnchored
                isMatchEmpty = isMatchEmpty || sub.isMatchEmpty
                if (staticExplicitCapturesLen != sub.staticExplicitCapturesLen) {
                    staticExplicitCapturesLen = null
                }
            }
            return Hir(
                kind = HirKind.Alternation(subs),
                isStartAnchored = isStartAnchored,
                isMatchEmpty = isMatchEmpty,
                staticExplicitCapturesLen = staticExplicitCapturesLen,
            )
        }
    }
}

internal sealed class HirKind {
    data object Empty : HirKind()

    data class Char(
        val ch: kotlin.Char,
    ) : HirKind()

    data class Class(
        val classDef: io.github.kotlinmania.regexlite.hir.Class,
    ) : HirKind()

    data class Look(
        val look: io.github.kotlinmania.regexlite.hir.Look,
    ) : HirKind()

    data class Repetition(
        val rep: io.github.kotlinmania.regexlite.hir.Repetition,
    ) : HirKind()

    data class Capture(
        val cap: io.github.kotlinmania.regexlite.hir.Capture,
    ) : HirKind()

    data class Concat(
        val subs: List<Hir>,
    ) : HirKind()

    data class Alternation(
        val subs: List<Hir>,
    ) : HirKind()

    fun subs(): List<Hir> =
        when (this) {
            is Empty, is Char, is Class, is Look -> emptyList()
            is Repetition -> listOf(rep.sub)
            is Capture -> listOf(cap.sub)
            is Concat -> subs
            is Alternation -> subs
        }
}

internal class Class(
    val ranges: MutableList<ClassRange>,
) {
    init {
        canonicalize()
    }

    fun asciiCaseFold() {
        val len = ranges.size
        for (i in 0 until len) {
            val folded = ranges[i].asciiCaseFold()
            if (folded != null) {
                ranges.add(folded)
            }
        }
        canonicalize()
    }

    fun negate() {
        val minChar = '\u0000'
        val maxChar = '\uFFFF'

        if (ranges.isEmpty()) {
            ranges.add(ClassRange(minChar, maxChar))
            return
        }

        val drainEnd = ranges.size
        if (ranges[0].start > minChar) {
            ranges.add(
                ClassRange(
                    start = minChar,
                    end = prevChar(ranges[0].start)!!,
                ),
            )
        }
        for (i in 1 until drainEnd) {
            ranges.add(
                ClassRange(
                    start = nextChar(ranges[i - 1].end)!!,
                    end = prevChar(ranges[i].start)!!,
                ),
            )
        }
        if (ranges[drainEnd - 1].end < maxChar) {
            ranges.add(
                ClassRange(
                    start = nextChar(ranges[drainEnd - 1].end)!!,
                    end = maxChar,
                ),
            )
        }
        for (i in 0 until drainEnd) {
            ranges.removeAt(0)
        }
    }

    fun canonicalize() {
        if (isCanonical()) {
            return
        }
        ranges.sort()
        if (ranges.isEmpty()) return

        val drainEnd = ranges.size
        for (oldi in 0 until drainEnd) {
            if (ranges.size > drainEnd) {
                val lastIdx = ranges.size - 1
                val last = ranges[lastIdx]
                val union = last.union(ranges[oldi])
                if (union != null) {
                    ranges[lastIdx] = union
                    continue
                }
            }
            ranges.add(ranges[oldi])
        }
        for (i in 0 until drainEnd) {
            ranges.removeAt(0)
        }
    }

    fun isCanonical(): Boolean {
        for (i in 0 until ranges.size - 1) {
            val current = ranges[i]
            val next = ranges[i + 1]
            if (current >= next) {
                return false
            }
            if (current.isContiguous(next)) {
                return false
            }
        }
        return true
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Class && ranges == other.ranges)

    override fun hashCode(): Int = ranges.hashCode()

    override fun toString(): String = "Class(ranges=$ranges)"
}

internal class ClassRange(
    val start: Char,
    val end: Char,
) : Comparable<ClassRange> {
    fun asciiCaseFold(): ClassRange? {
        val lowerA = 'a'
        val lowerZ = 'z'
        val upperA = 'A'
        val upperZ = 'Z'

        if (!ClassRange(lowerA, lowerZ).isIntersectionEmpty(this)) {
            val s = maxOf(start, lowerA)
            val e = minOf(end, lowerZ)
            return ClassRange(
                start = (s.code - 32).toChar(),
                end = (e.code - 32).toChar(),
            )
        }
        if (!ClassRange(upperA, upperZ).isIntersectionEmpty(this)) {
            val s = maxOf(start, upperA)
            val e = minOf(end, upperZ)
            return ClassRange(
                start = (s.code + 32).toChar(),
                end = (e.code + 32).toChar(),
            )
        }
        return null
    }

    fun union(other: ClassRange): ClassRange? {
        if (!isContiguous(other)) {
            return null
        }
        val s = minOf(start, other.start)
        val e = maxOf(end, other.end)
        return ClassRange(s, e)
    }

    fun isContiguous(other: ClassRange): Boolean {
        val s1 = start.code
        val e1 = end.code
        val s2 = other.start.code
        val e2 = other.end.code
        return maxOf(s1, s2) <= minOf(e1, e2) + 1
    }

    fun isIntersectionEmpty(other: ClassRange): Boolean {
        val s1 = start.code
        val e1 = end.code
        val s2 = other.start.code
        val e2 = other.end.code
        return maxOf(s1, s2) > minOf(e1, e2)
    }

    override fun compareTo(other: ClassRange): Int {
        val cmp = start.compareTo(other.start)
        return if (cmp != 0) cmp else end.compareTo(other.end)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is ClassRange && start == other.start && end == other.end)

    override fun hashCode(): Int = 31 * start.hashCode() + end.hashCode()

    override fun toString(): String = "ClassRange($start..$end)"
}

internal enum class Look(
    val mask: Int,
) {
    Start(1 shl 0),
    End(1 shl 1),
    StartLF(1 shl 2),
    EndLF(1 shl 3),
    StartCRLF(1 shl 4),
    EndCRLF(1 shl 5),
    Word(1 shl 6),
    WordNegate(1 shl 7),
    WordStart(1 shl 8),
    WordEnd(1 shl 9),
    WordStartHalf(1 shl 10),
    WordEndHalf(1 shl 11),
    ;

    fun isMatch(haystack: ByteArray, at: Int): Boolean =
        when (this) {
            Start -> at == 0
            End -> at == haystack.size
            StartLF -> at == 0 || haystack[at - 1] == '\n'.code.toByte()
            EndLF -> at == haystack.size || haystack[at] == '\n'.code.toByte()
            StartCRLF ->
                at == 0 ||
                    haystack[at - 1] == '\n'.code.toByte() ||
                    (haystack[at - 1] == '\r'.code.toByte() && (at >= haystack.size || haystack[at] != '\n'.code.toByte()))
            EndCRLF ->
                at == haystack.size ||
                    haystack[at] == '\r'.code.toByte() ||
                    (haystack[at] == '\n'.code.toByte() && (at == 0 || haystack[at - 1] != '\r'.code.toByte()))
            Word -> {
                val wordBefore = at > 0 && isWordByte(haystack[at - 1])
                val wordAfter = at < haystack.size && isWordByte(haystack[at])
                wordBefore != wordAfter
            }
            WordNegate -> {
                val wordBefore = at > 0 && isWordByte(haystack[at - 1])
                val wordAfter = at < haystack.size && isWordByte(haystack[at])
                wordBefore == wordAfter
            }
            WordStart -> {
                val wordBefore = at > 0 && isWordByte(haystack[at - 1])
                val wordAfter = at < haystack.size && isWordByte(haystack[at])
                !wordBefore && wordAfter
            }
            WordEnd -> {
                val wordBefore = at > 0 && isWordByte(haystack[at - 1])
                val wordAfter = at < haystack.size && isWordByte(haystack[at])
                wordBefore && !wordAfter
            }
            WordStartHalf -> {
                val wordBefore = at > 0 && isWordByte(haystack[at - 1])
                !wordBefore
            }
            WordEndHalf -> {
                val wordAfter = at < haystack.size && isWordByte(haystack[at])
                !wordAfter
            }
        }
}

internal class Repetition(
    val min: UInt,
    val max: UInt?,
    val greedy: Boolean,
    val sub: Hir,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is Repetition &&
                    min == other.min &&
                    max == other.max &&
                    greedy == other.greedy &&
                    sub == other.sub
            )

    override fun hashCode(): Int {
        var result = min.hashCode()
        result = 31 * result + (max?.hashCode() ?: 0)
        result = 31 * result + greedy.hashCode()
        result = 31 * result + sub.hashCode()
        return result
    }

    override fun toString(): String = "Repetition(min=$min, max=$max, greedy=$greedy, sub=$sub)"
}

internal class Capture(
    val index: UInt,
    val name: String?,
    val sub: Hir,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is Capture &&
                    index == other.index &&
                    name == other.name &&
                    sub == other.sub
            )

    override fun hashCode(): Int {
        var result = index.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + sub.hashCode()
        return result
    }

    override fun toString(): String = "Capture(index=$index, name=$name, sub=$sub)"
}

private fun nextChar(ch: Char): Char? {
    if (ch == '\uD7FF') {
        return '\uE000'
    }
    if (ch == '\uFFFF') {
        return null
    }
    return (ch.code + 1).toChar()
}

private fun prevChar(ch: Char): Char? {
    if (ch == '\uE000') {
        return '\uD7FF'
    }
    if (ch == '\u0000') {
        return null
    }
    return (ch.code - 1).toChar()
}
