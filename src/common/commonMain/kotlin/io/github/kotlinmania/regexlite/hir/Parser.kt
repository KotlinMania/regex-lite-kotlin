// port-lint: source hir/parse.rs
package io.github.kotlinmania.regexlite.hir

import io.github.kotlinmania.regexlite.Error

internal const val ERR_TOO_MUCH_NESTING = "pattern has too much nesting"
internal const val ERR_TOO_MANY_CAPTURES = "too many capture groups"
internal const val ERR_DUPLICATE_CAPTURE_NAME = "duplicate capture group name"
internal const val ERR_UNCLOSED_GROUP = "found open group without closing ')'"
internal const val ERR_UNCLOSED_GROUP_QUESTION = "expected closing ')', but got end of pattern"
internal const val ERR_UNOPENED_GROUP = "found closing ')' without matching '('"
internal const val ERR_LOOK_UNSUPPORTED = "look-around is not supported"
internal const val ERR_EMPTY_FLAGS = "empty flag directive '(?)' is not allowed"
internal const val ERR_MISSING_GROUP_NAME = "expected capture group name, but got end of pattern"
internal const val ERR_INVALID_GROUP_NAME = "invalid group name"
internal const val ERR_UNCLOSED_GROUP_NAME = "expected end of capture group name, but got end of pattern"
internal const val ERR_EMPTY_GROUP_NAME = "empty capture group names are not allowed"
internal const val ERR_FLAG_UNRECOGNIZED = "unrecognized inline flag"
internal const val ERR_FLAG_REPEATED_NEGATION = "inline flag negation cannot be repeated"
internal const val ERR_FLAG_DUPLICATE = "duplicate inline flag is not allowed"
internal const val ERR_FLAG_UNEXPECTED_EOF = "expected ':' or ')' to end inline flags, but got end of pattern"
internal const val ERR_FLAG_DANGLING_NEGATION = "inline flags cannot end with negation directive"
internal const val ERR_DECIMAL_NO_DIGITS = "expected decimal number, but found no digits"
internal const val ERR_DECIMAL_INVALID = "got invalid decimal number"
internal const val ERR_HEX_BRACE_INVALID_DIGIT = "expected hexadecimal number in braces, but got non-hex digit"
internal const val ERR_HEX_BRACE_UNEXPECTED_EOF = "expected hexadecimal number, but saw end of pattern before closing brace"
internal const val ERR_HEX_BRACE_EMPTY = "expected hexadecimal number in braces, but got no digits"
internal const val ERR_HEX_BRACE_INVALID = "got invalid hexadecimal number in braces"
internal const val ERR_HEX_FIXED_UNEXPECTED_EOF = "expected fixed length hexadecimal number, but saw end of pattern first"
internal const val ERR_HEX_FIXED_INVALID_DIGIT = "expected fixed length hexadecimal number, but got non-hex digit"
internal const val ERR_HEX_FIXED_INVALID = "got invalid fixed length hexadecimal number"
internal const val ERR_HEX_UNEXPECTED_EOF = "expected hexadecimal number, but saw end of pattern first"
internal const val ERR_ESCAPE_UNEXPECTED_EOF = "saw start of escape sequence, but saw end of pattern before it finished"
internal const val ERR_BACKREF_UNSUPPORTED = "backreferences are not supported"
internal const val ERR_UNICODE_CLASS_UNSUPPORTED = "Unicode character classes are not supported"
internal const val ERR_ESCAPE_UNRECOGNIZED = "unrecognized escape sequence"
internal const val ERR_POSIX_CLASS_UNRECOGNIZED = "unrecognized POSIX character class"
internal const val ERR_UNCOUNTED_REP_SUB_MISSING = "uncounted repetition operator must be applied to a sub-expression"
internal const val ERR_COUNTED_REP_SUB_MISSING = "counted repetition operator must be applied to a sub-expression"
internal const val ERR_COUNTED_REP_UNCLOSED = "found unclosed counted repetition operator"
internal const val ERR_COUNTED_REP_MIN_UNCLOSED = "found incomplete and unclosed counted repetition operator"
internal const val ERR_COUNTED_REP_COMMA_UNCLOSED = "found counted repetition operator with a comma that is unclosed"
internal const val ERR_COUNTED_REP_MIN_MAX_UNCLOSED = "found counted repetition with min and max that is unclosed"
internal const val ERR_COUNTED_REP_INVALID = "expected closing brace for counted repetition, but got something else"
internal const val ERR_COUNTED_REP_INVALID_RANGE = "found counted repetition with a min bigger than its max"
internal const val ERR_CLASS_UNCLOSED_AFTER_ITEM = "non-empty character class has no closing bracket"
internal const val ERR_CLASS_INVALID_RANGE_ITEM = "character class ranges must start and end with a single character"
internal const val ERR_CLASS_INVALID_ITEM = "invalid escape sequence in character class"
internal const val ERR_CLASS_UNCLOSED_AFTER_DASH = "non-empty character class has no closing bracket after dash"
internal const val ERR_CLASS_UNCLOSED_AFTER_NEGATION = "negated character class has no closing bracket"
internal const val ERR_CLASS_UNCLOSED_AFTER_CLOSING = "character class begins with literal ']' but has no closing bracket"
internal const val ERR_CLASS_INVALID_RANGE = "invalid range in character class"
internal const val ERR_CLASS_UNCLOSED = "found unclosed character class"
internal const val ERR_CLASS_NEST_UNSUPPORTED = "nested character classes are not supported"
internal const val ERR_CLASS_INTERSECTION_UNSUPPORTED = "character class intersection is not supported"
internal const val ERR_CLASS_DIFFERENCE_UNSUPPORTED = "character class difference is not supported"
internal const val ERR_CLASS_SYMDIFFERENCE_UNSUPPORTED = "character class symmetric difference is not supported"
internal const val ERR_SPECIAL_WORD_BOUNDARY_UNCLOSED = "special word boundary assertion is unclosed or has an invalid character"
internal const val ERR_SPECIAL_WORD_BOUNDARY_UNRECOGNIZED = "special word boundary assertion is unrecognized"
internal const val ERR_SPECIAL_WORD_OR_REP_UNEXPECTED_EOF = "found start of special word boundary or repetition without an end"

internal class Parser(
    private val config: Config,
    private val pattern: String,
) {
    private var depth: UInt = 0u
    private var pos: Int = 0
    private var char: Int? = if (pattern.isEmpty()) null else codePointAt(pattern, 0)
    private var captureIndex: UInt = 0u
    private var flags: Flags = config.flags.copy()
    private val captureNames: MutableList<String> = mutableListOf()

    private fun incrementDepth(): Result<UInt> {
        val old = depth
        if (old > config.nestLimit) {
            return Result.failure(Error(ERR_TOO_MUCH_NESTING))
        }
        depth += 1u
        return Result.success(old)
    }

    private fun decrementDepth() {
        check(depth > 0u) { "depth underflow" }
        depth -= 1u
    }

    private fun scalar(): Int =
        char ?: error("codepoint, but parser is done")

    private fun isDone(): Boolean = pos >= pattern.length

    private fun bump(): Boolean {
        val current = char ?: return false
        pos += scalarCharCount(current)
        char = if (pos < pattern.length) codePointAt(pattern, pos) else null
        return char != null
    }

    private fun bumpIf(prefix: String): Boolean {
        if (pos + prefix.length <= pattern.length && pattern.startsWith(prefix, pos)) {
            pos += prefix.length
            char = if (pos < pattern.length) codePointAt(pattern, pos) else null
            return true
        }
        return false
    }

    private fun bumpAndBumpSpace(): Boolean {
        if (!bump()) {
            return false
        }
        bumpSpace()
        return !isDone()
    }

    private fun bumpSpace() {
        if (!flags.ignoreWhitespace) {
            return
        }
        while (!isDone()) {
            val c = scalar()
            if (isUnicodeWhitespace(c)) {
                bump()
            } else if (c == '#'.code) {
                bump()
                while (!isDone()) {
                    val ch = scalar()
                    bump()
                    if (ch == '\n'.code) {
                        break
                    }
                }
            } else {
                break
            }
        }
    }

    private fun peek(): Int? {
        val current = char ?: return null
        val nextPos = pos + scalarCharCount(current)
        return if (nextPos < pattern.length) codePointAt(pattern, nextPos) else null
    }

    private fun peekSpace(): Int? {
        if (!flags.ignoreWhitespace) {
            return peek()
        }
        val current = char ?: return null
        var start = pos + scalarCharCount(current)
        var inComment = false
        while (start < pattern.length) {
            val ch = codePointAt(pattern, start)
            val step = scalarCharCount(ch)
            if (isUnicodeWhitespace(ch)) {
                start += step
            } else if (!inComment && ch == '#'.code) {
                inComment = true
                start += step
            } else if (inComment && ch == '\n'.code) {
                inComment = false
                start += step
            } else if (inComment) {
                start += step
            } else {
                return ch
            }
        }
        return null
    }

    private fun nextCaptureIndex(): Result<UInt> {
        val current = captureIndex
        if (current == UInt.MAX_VALUE) {
            return Result.failure(Error(ERR_TOO_MANY_CAPTURES))
        }
        val next = current + 1u
        captureIndex = next
        return Result.success(next)
    }

    private fun addCaptureName(name: String): Result<Unit> {
        val idx = captureNames.binarySearch(name)
        return if (idx >= 0) {
            Result.failure(Error(ERR_DUPLICATE_CAPTURE_NAME))
        } else {
            captureNames.add(-idx - 1, name)
            Result.success(Unit)
        }
    }

    private fun isLookaroundPrefix(): Boolean =
        bumpIf("?=") || bumpIf("?!") || bumpIf("?<=") || bumpIf("?<!")

    fun parse(): Result<Hir> {
        val hir = parseInner().getOrElse { return Result.failure(it) }
        checkHirNesting(hir, config.nestLimit).getOrElse { return Result.failure(it) }
        return Result.success(hir)
    }

    internal fun parseInner(): Result<Hir> {
        val curDepth = incrementDepth().getOrElse { return Result.failure(it) }
        val alternates = mutableListOf<Hir>()
        val concat = mutableListOf<Hir>()

        while (true) {
            bumpSpace()
            if (isDone()) {
                break
            }
            when (scalar()) {
                '('.code -> {
                    val oldFlags = flags.copy()
                    val groupRes = parseGroup()
                    val sub = groupRes.getOrElse { return Result.failure(it) }
                    if (sub != null) {
                        concat.add(sub)
                        flags = oldFlags
                    }
                    if (char != ')'.code) {
                        return Result.failure(Error(ERR_UNCLOSED_GROUP))
                    }
                    bump()
                }
                ')'.code -> {
                    if (curDepth == 0u) {
                        return Result.failure(Error(ERR_UNOPENED_GROUP))
                    }
                    break
                }
                '|'.code -> {
                    alternates.add(Hir.concat(concat.toMutableList()))
                    concat.clear()
                    bump()
                }
                '['.code -> {
                    val cls = parseClass().getOrElse { return Result.failure(it) }
                    concat.add(cls)
                }
                '?'.code, '*'.code, '+'.code -> {
                    parseUncountedRepetition(concat).getOrElse { return Result.failure(it) }
                }
                '{'.code -> {
                    parseCountedRepetition(concat).getOrElse { return Result.failure(it) }
                }
                else -> {
                    val prim = parsePrimitive().getOrElse { return Result.failure(it) }
                    concat.add(prim)
                }
            }
        }
        decrementDepth()
        alternates.add(Hir.concat(concat))
        return Result.success(Hir.alternation(alternates))
    }

    private fun parsePrimitive(): Result<Hir> {
        val ch = scalar()
        bump()
        return when (ch) {
            '\\'.code -> parseEscape()
            '.'.code -> Result.success(hirDot())
            '^'.code -> Result.success(hirAnchorStart())
            '$'.code -> Result.success(hirAnchorEnd())
            else -> Result.success(hirChar(ch))
        }
    }

    private fun parseEscape(): Result<Hir> {
        if (isDone()) {
            return Result.failure(Error(ERR_ESCAPE_UNEXPECTED_EOF))
        }
        val ch = scalar()
        when (ch) {
            in '0'.code..'9'.code -> return Result.failure(Error(ERR_BACKREF_UNSUPPORTED))
            'p'.code, 'P'.code -> return Result.failure(Error(ERR_UNICODE_CLASS_UNSUPPORTED))
            'x'.code, 'u'.code, 'U'.code -> return parseHex()
            'd'.code, 's'.code, 'w'.code, 'D'.code, 'S'.code, 'W'.code -> return Result.success(parsePerlClass())
        }

        bump()
        if (isMetaCharacter(ch) || isEscapableCharacter(ch)) {
            return Result.success(hirChar(ch))
        }
        return when (ch) {
            'a'.code -> Result.success(hirChar(0x07))
            'f'.code -> Result.success(hirChar(0x0C))
            't'.code -> Result.success(hirChar('\t'.code))
            'n'.code -> Result.success(hirChar('\n'.code))
            'r'.code -> Result.success(hirChar('\r'.code))
            'v'.code -> Result.success(hirChar(0x0B))
            'A'.code -> Result.success(Hir.look(Look.Start))
            'z'.code -> Result.success(Hir.look(Look.End))
            'b'.code -> {
                var hir = Hir.look(Look.Word)
                if (!isDone() && scalar() == '{'.code) {
                    val special = maybeParseSpecialWordBoundary().getOrElse { return Result.failure(it) }
                    if (special != null) {
                        hir = special
                    }
                }
                Result.success(hir)
            }
            'B'.code -> Result.success(Hir.look(Look.WordNegate))
            '<'.code -> Result.success(Hir.look(Look.WordStart))
            '>'.code -> Result.success(Hir.look(Look.WordEnd))
            else -> Result.failure(Error(ERR_ESCAPE_UNRECOGNIZED))
        }
    }

    private fun maybeParseSpecialWordBoundary(): Result<Hir?> {
        check(scalar() == '{'.code)
        val isValidChar: (Int) -> Boolean = { c ->
            c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c == '-'.code
        }
        val startPos = pos
        if (!bumpAndBumpSpace()) {
            return Result.failure(Error(ERR_SPECIAL_WORD_OR_REP_UNEXPECTED_EOF))
        }
        if (!isValidChar(scalar())) {
            pos = startPos
            char = '{'.code
            return Result.success(null)
        }

        val scratch = StringBuilder()
        while (!isDone() && isValidChar(scalar())) {
            scratch.append(scalar().toChar())
            bumpAndBumpSpace()
        }
        if (isDone() || scalar() != '}'.code) {
            return Result.failure(Error(ERR_SPECIAL_WORD_BOUNDARY_UNCLOSED))
        }
        bump()
        val look =
            when (scratch.toString()) {
                "start" -> Look.WordStart
                "end" -> Look.WordEnd
                "start-half" -> Look.WordStartHalf
                "end-half" -> Look.WordEndHalf
                else -> return Result.failure(Error(ERR_SPECIAL_WORD_BOUNDARY_UNRECOGNIZED))
            }
        return Result.success(Hir.look(look))
    }

    private fun parseHex(): Result<Hir> {
        val digitLen =
            when (val ch = scalar()) {
                'x'.code -> 2
                'u'.code -> 4
                'U'.code -> 8
                else -> error("invalid start of fixed length hexadecimal number $ch")
            }
        if (!bumpAndBumpSpace()) {
            return Result.failure(Error(ERR_HEX_UNEXPECTED_EOF))
        }
        return if (scalar() == '{'.code) {
            parseHexBrace()
        } else {
            parseHexDigits(digitLen)
        }
    }

    private fun parseHexDigits(digitLen: Int): Result<Hir> {
        val scratch = StringBuilder()
        for (i in 0 until digitLen) {
            if (i > 0 && !bumpAndBumpSpace()) {
                return Result.failure(Error(ERR_HEX_FIXED_UNEXPECTED_EOF))
            }
            val cp = scalar()
            if (!isHex(cp)) {
                return Result.failure(Error(ERR_HEX_FIXED_INVALID_DIGIT))
            }
            scratch.append(cp.toChar())
        }
        bumpAndBumpSpace()
        val code = scratch.toString().toIntOrNull(16)
        if (code == null || code < 0 || code > 0x10FFFF || (code in 0xD800..0xDFFF)) {
            return Result.failure(Error(ERR_HEX_FIXED_INVALID))
        }
        return Result.success(hirChar(code))
    }

    private fun parseHexBrace(): Result<Hir> {
        val scratch = StringBuilder()
        while (bumpAndBumpSpace() && scalar() != '}'.code) {
            val cp = scalar()
            if (!isHex(cp)) {
                return Result.failure(Error(ERR_HEX_BRACE_INVALID_DIGIT))
            }
            scratch.append(cp.toChar())
        }
        if (isDone()) {
            return Result.failure(Error(ERR_HEX_BRACE_UNEXPECTED_EOF))
        }
        check(scalar() == '}'.code)
        bumpAndBumpSpace()

        if (scratch.isEmpty()) {
            return Result.failure(Error(ERR_HEX_BRACE_EMPTY))
        }
        val code = scratch.toString().toIntOrNull(16)
        if (code == null || code < 0 || code > 0x10FFFF || (code in 0xD800..0xDFFF)) {
            return Result.failure(Error(ERR_HEX_BRACE_INVALID))
        }
        return Result.success(hirChar(code))
    }

    private fun parseDecimal(): Result<UInt> {
        val scratch = StringBuilder()
        while (!isDone() && isUnicodeWhitespace(scalar())) {
            bump()
        }
        while (!isDone() && scalar() in '0'.code..'9'.code) {
            scratch.append(scalar().toChar())
            bumpAndBumpSpace()
        }
        while (!isDone() && isUnicodeWhitespace(scalar())) {
            bumpAndBumpSpace()
        }
        if (scratch.isEmpty()) {
            return Result.failure(Error(ERR_DECIMAL_NO_DIGITS))
        }
        val value =
            scratch.toString().toUIntOrNull()
                ?: return Result.failure(Error(ERR_DECIMAL_INVALID))
        return Result.success(value)
    }

    private fun parseUncountedRepetition(concat: MutableList<Hir>): Result<MutableList<Hir>> {
        if (concat.isEmpty()) {
            return Result.failure(Error(ERR_UNCOUNTED_REP_SUB_MISSING))
        }
        val sub = concat.removeAt(concat.size - 1)
        val (min, max) =
            when (val c = scalar()) {
                '?'.code -> Pair(0u, 1u)
                '*'.code -> Pair(0u, null)
                '+'.code -> Pair(1u, null)
                else -> error("unrecognized repetition operator '$c'")
            }
        var greedy = true
        if (bump() && char == '?'.code) {
            greedy = false
            bump()
        }
        if (flags.swapGreed) {
            greedy = !greedy
        }
        concat.add(Hir.repetition(Repetition(min = min, max = max, greedy = greedy, sub = sub)))
        return Result.success(concat)
    }

    private fun parseCountedRepetition(concat: MutableList<Hir>): Result<MutableList<Hir>> {
        check(scalar() == '{'.code) { "expected opening brace" }
        if (concat.isEmpty()) {
            return Result.failure(Error(ERR_COUNTED_REP_SUB_MISSING))
        }
        val sub = concat.removeAt(concat.size - 1)
        if (!bumpAndBumpSpace()) {
            return Result.failure(Error(ERR_COUNTED_REP_UNCLOSED))
        }
        val min = parseDecimal().getOrElse { return Result.failure(it) }
        var max: UInt? = min
        if (isDone()) {
            return Result.failure(Error(ERR_COUNTED_REP_MIN_UNCLOSED))
        }
        if (scalar() == ','.code) {
            if (!bumpAndBumpSpace()) {
                return Result.failure(Error(ERR_COUNTED_REP_COMMA_UNCLOSED))
            }
            if (scalar() != '}'.code) {
                max = parseDecimal().getOrElse { return Result.failure(it) }
            } else {
                max = null
            }
            if (isDone()) {
                return Result.failure(Error(ERR_COUNTED_REP_MIN_MAX_UNCLOSED))
            }
        }
        if (scalar() != '}'.code) {
            return Result.failure(Error(ERR_COUNTED_REP_INVALID))
        }

        var greedy = true
        if (bumpAndBumpSpace() && char == '?'.code) {
            greedy = false
            bump()
        }
        if (flags.swapGreed) {
            greedy = !greedy
        }
        if (max != null && min > max) {
            return Result.failure(Error(ERR_COUNTED_REP_INVALID_RANGE))
        }
        concat.add(Hir.repetition(Repetition(min = min, max = max, greedy = greedy, sub = sub)))
        return Result.success(concat)
    }

    private fun parseGroup(): Result<Hir?> {
        check(scalar() == '('.code)
        bumpAndBumpSpace()
        if (isLookaroundPrefix()) {
            return Result.failure(Error(ERR_LOOK_UNSUPPORTED))
        }
        if (bumpIf("?P<") || bumpIf("?<")) {
            val index = nextCaptureIndex().getOrElse { return Result.failure(it) }
            val name = parseCaptureName().getOrElse { return Result.failure(it) }
            val sub = parseInner().getOrElse { return Result.failure(it) }
            val cap = Capture(index = index, name = name, sub = sub)
            return Result.success(Hir.capture(cap))
        } else if (bumpIf("?")) {
            if (isDone()) {
                return Result.failure(Error(ERR_UNCLOSED_GROUP_QUESTION))
            }
            val start = pos
            val parsedFlags = parseFlags().getOrElse { return Result.failure(it) }
            flags = parsedFlags
            val consumed = pos - start
            if (char == ')'.code) {
                if (consumed == 0) {
                    return Result.failure(Error(ERR_EMPTY_FLAGS))
                }
                return Result.success(null)
            } else {
                check(char == ':'.code)
                bump()
                return parseInner().map { it }
            }
        } else {
            val index = nextCaptureIndex().getOrElse { return Result.failure(it) }
            val sub = parseInner().getOrElse { return Result.failure(it) }
            val cap = Capture(index = index, name = null, sub = sub)
            return Result.success(Hir.capture(cap))
        }
    }

    private fun parseCaptureName(): Result<String> {
        if (isDone()) {
            return Result.failure(Error(ERR_MISSING_GROUP_NAME))
        }
        val start = pos
        while (true) {
            val cp = scalar()
            if (cp == '>'.code) {
                break
            }
            if (!isCaptureScalar(cp, pos == start)) {
                return Result.failure(Error(ERR_INVALID_GROUP_NAME))
            }
            if (!bump()) {
                break
            }
        }
        val end = pos
        if (isDone()) {
            return Result.failure(Error(ERR_UNCLOSED_GROUP_NAME))
        }
        check(scalar() == '>'.code)
        bump()
        val name = pattern.substring(start, end)
        if (name.isEmpty()) {
            return Result.failure(Error(ERR_EMPTY_GROUP_NAME))
        }
        addCaptureName(name).getOrElse { return Result.failure(it) }
        return Result.success(name)
    }

    private fun parseFlags(): Result<Flags> {
        val f = flags.copy()
        var negate = false
        var lastWasNegation = false
        val seen = BooleanArray(128)

        while (char != ':'.code && char != ')'.code) {
            if (scalar() == '-'.code) {
                lastWasNegation = true
                if (negate) {
                    return Result.failure(Error(ERR_FLAG_REPEATED_NEGATION))
                }
                negate = true
            } else {
                lastWasNegation = false
                parseFlag(f, negate).getOrElse { return Result.failure(it) }
                val flagByte = scalar()
                if (flagByte in 0..127) {
                    if (seen[flagByte]) {
                        return Result.failure(Error(ERR_FLAG_DUPLICATE))
                    }
                    seen[flagByte] = true
                }
            }
            if (!bump()) {
                return Result.failure(Error(ERR_FLAG_UNEXPECTED_EOF))
            }
        }
        if (lastWasNegation) {
            return Result.failure(Error(ERR_FLAG_DANGLING_NEGATION))
        }
        return Result.success(f)
    }

    private fun parseFlag(f: Flags, negate: Boolean): Result<Unit> {
        val enabled = !negate
        when (scalar()) {
            'i'.code -> f.caseInsensitive = enabled
            'm'.code -> f.multiLine = enabled
            's'.code -> f.dotMatchesNewLine = enabled
            'U'.code -> f.swapGreed = enabled
            'R'.code -> f.crlf = enabled
            'x'.code -> f.ignoreWhitespace = enabled
            'u'.code -> {} // No-op compatibility flag
            else -> return Result.failure(Error(ERR_FLAG_UNRECOGNIZED))
        }
        return Result.success(Unit)
    }

    private fun parseClass(): Result<Hir> {
        check(scalar() == '['.code)
        val union = mutableListOf<ClassRange>()
        if (!bumpAndBumpSpace()) {
            return Result.failure(Error(ERR_CLASS_UNCLOSED))
        }
        val negate =
            if (scalar() != '^'.code) {
                false
            } else {
                if (!bumpAndBumpSpace()) {
                    return Result.failure(Error(ERR_CLASS_UNCLOSED_AFTER_NEGATION))
                }
                true
            }

        while (scalar() == '-'.code) {
            union.add(ClassRange('-'.code, '-'.code))
            if (!bumpAndBumpSpace()) {
                return Result.failure(Error(ERR_CLASS_UNCLOSED_AFTER_DASH))
            }
        }

        if (union.isEmpty() && scalar() == ']'.code) {
            union.add(ClassRange(']'.code, ']'.code))
            if (!bumpAndBumpSpace()) {
                return Result.failure(Error(ERR_CLASS_UNCLOSED_AFTER_CLOSING))
            }
        }

        while (true) {
            bumpSpace()
            if (isDone()) {
                return Result.failure(Error(ERR_CLASS_UNCLOSED))
            }
            when (scalar()) {
                '['.code -> {
                    val posix = maybeParsePosixClass()
                    if (posix != null) {
                        union.addAll(posix.ranges)
                        continue
                    }
                    return Result.failure(Error(ERR_CLASS_NEST_UNSUPPORTED))
                }
                ']'.code -> {
                    bump()
                    val cls = Class(union)
                    if (flags.caseInsensitive) {
                        cls.asciiCaseFold()
                    }
                    if (negate) {
                        cls.negate()
                    }
                    return Result.success(Hir.classNode(cls))
                }
                '&'.code ->
                    if (peek() == '&'.code) {
                        return Result.failure(Error(ERR_CLASS_INTERSECTION_UNSUPPORTED))
                    } else {
                        parseClassRange(union).getOrElse { return Result.failure(it) }
                    }
                '-'.code ->
                    if (peek() == '-'.code) {
                        return Result.failure(Error(ERR_CLASS_DIFFERENCE_UNSUPPORTED))
                    } else {
                        parseClassRange(union).getOrElse { return Result.failure(it) }
                    }
                '~'.code ->
                    if (peek() == '~'.code) {
                        return Result.failure(Error(ERR_CLASS_SYMDIFFERENCE_UNSUPPORTED))
                    } else {
                        parseClassRange(union).getOrElse { return Result.failure(it) }
                    }
                else -> parseClassRange(union).getOrElse { return Result.failure(it) }
            }
        }
    }

    private fun parseClassRange(union: MutableList<ClassRange>): Result<Unit> {
        val prim1 = parseClassItem().getOrElse { return Result.failure(it) }
        bumpSpace()
        if (isDone()) {
            return Result.failure(Error(ERR_CLASS_UNCLOSED_AFTER_ITEM))
        }
        val nextSpace = peekSpace()
        if (scalar() != '-'.code || nextSpace == ']'.code || nextSpace == '-'.code) {
            val ranges = intoClassItemRanges(prim1).getOrElse { return Result.failure(it) }
            union.addAll(ranges)
            return Result.success(Unit)
        }
        if (!bumpAndBumpSpace()) {
            return Result.failure(Error(ERR_CLASS_UNCLOSED_AFTER_DASH))
        }
        val prim2 = parseClassItem().getOrElse { return Result.failure(it) }
        val start = intoClassItemRange(prim1).getOrElse { return Result.failure(it) }
        val end = intoClassItemRange(prim2).getOrElse { return Result.failure(it) }
        if (start > end) {
            return Result.failure(Error(ERR_CLASS_INVALID_RANGE))
        }
        union.add(ClassRange(start, end))
        return Result.success(Unit)
    }

    private fun parseClassItem(): Result<Hir> {
        val ch = scalar()
        bump()
        return if (ch == '\\'.code) {
            parseEscape()
        } else {
            Result.success(Hir.char(ch))
        }
    }

    private fun maybeParsePosixClass(): Class? {
        check(scalar() == '['.code)
        val startPos = pos
        val startChar = char

        val reset: () -> Unit = {
            pos = startPos
            char = startChar
        }

        var negated = false
        if (!bump() || scalar() != ':'.code) {
            reset()
            return null
        }
        if (!bump()) {
            reset()
            return null
        }
        if (scalar() == '^'.code) {
            negated = true
            if (!bump()) {
                reset()
                return null
            }
        }
        val nameStart = pos
        while (scalar() != ':'.code && bump()) {
            // Advance through POSIX class name until delimiter
        }
        if (isDone()) {
            reset()
            return null
        }
        val name = pattern.substring(nameStart, pos)
        if (!bumpIf(":]")) {
            reset()
            return null
        }
        val ranges = posixClass(name).getOrNull()
        if (ranges != null) {
            val cls = Class(ranges.toMutableList())
            if (negated) {
                cls.negate()
            }
            return cls
        }
        reset()
        return null
    }

    private fun parsePerlClass(): Hir {
        val ch = scalar()
        bump()
        val ranges =
            when (ch) {
                'd'.code, 'D'.code -> posixClass("digit").getOrThrow()
                's'.code, 'S'.code -> posixClass("space").getOrThrow()
                'w'.code, 'W'.code -> posixClass("word").getOrThrow()
                else -> error("invalid Perl class \\$ch")
            }
        val cls = Class(ranges.toMutableList())
        if (ch == 'D'.code || ch == 'S'.code || ch == 'W'.code) {
            cls.negate()
        }
        return Hir.classNode(cls)
    }

    private fun hirDot(): Hir =
        if (flags.dotMatchesNewLine) {
            Hir.classNode(Class(mutableListOf(ClassRange(0x0000, 0x10FFFF))))
        } else if (flags.crlf) {
            Hir.classNode(
                Class(
                    mutableListOf(
                        ClassRange(0x0000, 0x0009),
                        ClassRange(0x000B, 0x000C),
                        ClassRange(0x000E, 0x10FFFF),
                    ),
                ),
            )
        } else {
            Hir.classNode(
                Class(
                    mutableListOf(
                        ClassRange(0x0000, 0x0009),
                        ClassRange(0x000B, 0x10FFFF),
                    ),
                ),
            )
        }

    private fun hirAnchorStart(): Hir {
        val look =
            if (flags.multiLine) {
                if (flags.crlf) Look.StartCRLF else Look.StartLF
            } else {
                Look.Start
            }
        return Hir.look(look)
    }

    private fun hirAnchorEnd(): Hir {
        val look =
            if (flags.multiLine) {
                if (flags.crlf) Look.EndCRLF else Look.EndLF
            } else {
                Look.End
            }
        return Hir.look(look)
    }

    private fun hirChar(ch: Int): Hir {
        if (flags.caseInsensitive) {
            val thisRange = ClassRange(ch, ch)
            val folded = thisRange.asciiCaseFold()
            if (folded != null) {
                return Hir.classNode(Class(mutableListOf(thisRange, folded)))
            }
        }
        return Hir.char(ch)
    }
}

private fun checkHirNesting(hir: Hir, limit: UInt): Result<Unit> {
    fun recurse(h: Hir, nestingLimit: UInt, depth: UInt): Result<Unit> {
        if (depth > nestingLimit) {
            return Result.failure(Error(ERR_TOO_MUCH_NESTING))
        }
        val nextDepth = depth + 1u
        return when (val k = h.kind) {
            is HirKind.Empty, is HirKind.Char, is HirKind.Class, is HirKind.Look -> Result.success(Unit)
            is HirKind.Repetition -> recurse(k.rep.sub, nestingLimit, nextDepth)
            is HirKind.Capture -> recurse(k.cap.sub, nestingLimit, nextDepth)
            is HirKind.Concat -> {
                for (sub in k.subs) {
                    recurse(sub, nestingLimit, nextDepth).getOrElse { return Result.failure(it) }
                }
                Result.success(Unit)
            }
            is HirKind.Alternation -> {
                for (sub in k.subs) {
                    recurse(sub, nestingLimit, nextDepth).getOrElse { return Result.failure(it) }
                }
                Result.success(Unit)
            }
        }
    }
    return recurse(hir, limit, 0u)
}

private fun intoClassItemRange(hir: Hir): Result<Int> =
    when (val k = hir.kind) {
        is HirKind.Char -> Result.success(k.ch)
        else -> Result.failure(Error(ERR_CLASS_INVALID_RANGE_ITEM))
    }

private fun intoClassItemRanges(hir: Hir): Result<List<ClassRange>> =
    when (val k = hir.kind) {
        is HirKind.Char -> Result.success(listOf(ClassRange(k.ch, k.ch)))
        is HirKind.Class -> Result.success(k.classDef.ranges)
        else -> Result.failure(Error(ERR_CLASS_INVALID_ITEM))
    }

internal fun posixClass(kind: String): Result<List<ClassRange>> {
    val ranges =
        when (kind) {
            "alnum" -> listOf(ClassRange('0', '9'), ClassRange('A', 'Z'), ClassRange('a', 'z'))
            "alpha" -> listOf(ClassRange('A', 'Z'), ClassRange('a', 'z'))
            "ascii" -> listOf(ClassRange(0x0000, 0x007F))
            "blank" -> listOf(ClassRange('\t', '\t'), ClassRange(' ', ' '))
            "cntrl" -> listOf(ClassRange(0x0000, 0x001F), ClassRange(0x007F, 0x007F))
            "digit" -> listOf(ClassRange('0', '9'))
            "graph" -> listOf(ClassRange('!', '~'))
            "lower" -> listOf(ClassRange('a', 'z'))
            "print" -> listOf(ClassRange(' ', '~'))
            "punct" -> listOf(ClassRange('!', '/'), ClassRange(':', '@'), ClassRange('[', '`'), ClassRange('{', '~'))
            "space" ->
                listOf(
                    ClassRange('\t', '\t'),
                    ClassRange('\n', '\n'),
                    ClassRange(0x000B, 0x000B),
                    ClassRange(0x000C, 0x000C),
                    ClassRange('\r', '\r'),
                    ClassRange(' ', ' '),
                )
            "upper" -> listOf(ClassRange('A', 'Z'))
            "word" -> listOf(ClassRange('0', '9'), ClassRange('A', 'Z'), ClassRange('_', '_'), ClassRange('a', 'z'))
            "xdigit" -> listOf(ClassRange('0', '9'), ClassRange('A', 'F'), ClassRange('a', 'f'))
            else -> return Result.failure(Error(ERR_POSIX_CLASS_UNRECOGNIZED))
        }
    return Result.success(ranges)
}

private fun isHex(c: Int): Boolean =
    c in '0'.code..'9'.code || c in 'a'.code..'f'.code || c in 'A'.code..'F'.code

private fun isCaptureScalar(cp: Int, first: Boolean): Boolean {
    if (cp == '_'.code) {
        return true
    }
    if (!first && (cp == '.'.code || cp == '['.code || cp == ']'.code)) {
        return true
    }
    if (cp !in 0..0xFFFF || cp in 0xD800..0xDFFF) {
        return false
    }
    val c = cp.toChar()
    return if (first) {
        c.isLetter()
    } else {
        c.isLetterOrDigit() || c.category == CharCategory.OTHER_NUMBER || c.category == CharCategory.LETTER_NUMBER
    }
}

private fun isUnicodeWhitespace(cp: Int): Boolean =
    when (cp) {
        in 0x0009..0x000D,
        0x0020,
        0x0085,
        0x00A0,
        0x1680,
        in 0x2000..0x200A,
        0x2028,
        0x2029,
        0x202F,
        0x205F,
        0x3000,
        -> true
        else -> false
    }

private fun codePointAt(s: String, index: Int): Int {
    val hi = s[index]
    if (hi.isHighSurrogate() && index + 1 < s.length) {
        val lo = s[index + 1]
        if (lo.isLowSurrogate()) {
            return 0x10000 + ((hi.code - 0xD800) shl 10) + (lo.code - 0xDC00)
        }
    }
    return hi.code
}

private fun scalarCharCount(cp: Int): Int = if (cp >= 0x10000) 2 else 1
