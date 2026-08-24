// port-lint: source hir/parse.rs
package io.github.kotlinmania.regexlite.hir

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
    private var char: Char? = pattern.firstOrNull()
    private var captureIndex: UInt = 0u
    private var flags: Flags = config.flags.copy()
    private val captureNames: MutableList<String> = mutableListOf()

    private fun pos(): Int = pos

    private fun incrementDepth(): Result<UInt> {
        val old = depth
        if (old > config.nestLimit) {
            return Result.failure(Exception(ERR_TOO_MUCH_NESTING))
        }
        depth += 1u
        return Result.success(old)
    }

    private fun decrementDepth() {
        check(depth > 0u) { "depth underflow" }
        depth -= 1u
    }

    private fun char(): Char =
        char ?: error("codepoint, but parser is done")

    private fun isDone(): Boolean = pos >= pattern.length

    private fun bump(): Boolean {
        if (isDone()) {
            return false
        }
        pos += 1
        char = if (pos < pattern.length) pattern[pos] else null
        return char != null
    }

    private fun bumpIf(prefix: String): Boolean {
        if (pos + prefix.length <= pattern.length && pattern.startsWith(prefix, pos)) {
            for (i in 0 until prefix.length) {
                bump()
            }
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
            val c = char()
            if (c.isWhitespace()) {
                bump()
            } else if (c == '#') {
                bump()
                while (!isDone()) {
                    val ch = char()
                    bump()
                    if (ch == '\n') {
                        break
                    }
                }
            } else {
                break
            }
        }
    }

    private fun peek(): Char? {
        if (isDone() || pos + 1 >= pattern.length) {
            return null
        }
        return pattern[pos + 1]
    }

    private fun peekSpace(): Char? {
        if (!flags.ignoreWhitespace) {
            return peek()
        }
        if (isDone()) {
            return null
        }
        var start = pos + 1
        var inComment = false
        while (start < pattern.length) {
            val ch = pattern[start]
            if (ch.isWhitespace()) {
                start += 1
            } else if (!inComment && ch == '#') {
                inComment = true
                start += 1
            } else if (inComment && ch == '\n') {
                inComment = false
                start += 1
            } else if (inComment) {
                start += 1
            } else {
                return ch
            }
        }
        return null
    }

    private fun nextCaptureIndex(): Result<UInt> {
        val current = captureIndex
        if (current == UInt.MAX_VALUE) {
            return Result.failure(Exception(ERR_TOO_MANY_CAPTURES))
        }
        val next = current + 1u
        captureIndex = next
        return Result.success(next)
    }

    private fun addCaptureName(name: String): Result<Unit> {
        val idx = captureNames.binarySearch(name)
        return if (idx >= 0) {
            Result.failure(Exception(ERR_DUPLICATE_CAPTURE_NAME))
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
        var concat = mutableListOf<Hir>()

        while (true) {
            bumpSpace()
            if (isDone()) {
                break
            }
            when (char()) {
                '(' -> {
                    val oldFlags = flags.copy()
                    val groupRes = parseGroup()
                    val sub = groupRes.getOrElse { return Result.failure(it) }
                    if (sub != null) {
                        concat.add(sub)
                        flags = oldFlags
                    }
                    if (char != ')') {
                        return Result.failure(Exception(ERR_UNCLOSED_GROUP))
                    }
                    bump()
                }
                ')' -> {
                    if (curDepth == 0u) {
                        return Result.failure(Exception(ERR_UNOPENED_GROUP))
                    }
                    break
                }
                '|' -> {
                    alternates.add(Hir.concat(concat))
                    concat = mutableListOf()
                    bump()
                }
                '[' -> {
                    val cls = parseClass().getOrElse { return Result.failure(it) }
                    concat.add(cls)
                }
                '?', '*', '+' -> {
                    val newConcat = parseUncountedRepetition(concat).getOrElse { return Result.failure(it) }
                    concat = newConcat
                }
                '{' -> {
                    val newConcat = parseCountedRepetition(concat).getOrElse { return Result.failure(it) }
                    concat = newConcat
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
        val ch = char()
        bump()
        return when (ch) {
            '\\' -> parseEscape()
            '.' -> Result.success(hirDot())
            '^' -> Result.success(hirAnchorStart())
            '$' -> Result.success(hirAnchorEnd())
            else -> Result.success(hirChar(ch))
        }
    }

    private fun parseEscape(): Result<Hir> {
        if (isDone()) {
            return Result.failure(Exception(ERR_ESCAPE_UNEXPECTED_EOF))
        }
        val ch = char()
        when (ch) {
            in '0'..'9' -> return Result.failure(Exception(ERR_BACKREF_UNSUPPORTED))
            'p', 'P' -> return Result.failure(Exception(ERR_UNICODE_CLASS_UNSUPPORTED))
            'x', 'u', 'U' -> return parseHex()
            'd', 's', 'w', 'D', 'S', 'W' -> return Result.success(parsePerlClass())
        }

        bump()
        if (isMetaCharacter(ch) || isEscapableCharacter(ch)) {
            return Result.success(hirChar(ch))
        }
        return when (ch) {
            'a' -> Result.success(hirChar('\u0007'))
            'f' -> Result.success(hirChar('\u000C'))
            't' -> Result.success(hirChar('\t'))
            'n' -> Result.success(hirChar('\n'))
            'r' -> Result.success(hirChar('\r'))
            'v' -> Result.success(hirChar('\u000B'))
            'A' -> Result.success(Hir.look(Look.Start))
            'z' -> Result.success(Hir.look(Look.End))
            'b' -> {
                var hir = Hir.look(Look.Word)
                if (!isDone() && char() == '{') {
                    val special = maybeParseSpecialWordBoundary().getOrElse { return Result.failure(it) }
                    if (special != null) {
                        hir = special
                    }
                }
                Result.success(hir)
            }
            'B' -> Result.success(Hir.look(Look.WordNegate))
            '<' -> Result.success(Hir.look(Look.WordStart))
            '>' -> Result.success(Hir.look(Look.WordEnd))
            else -> Result.failure(Exception(ERR_ESCAPE_UNRECOGNIZED))
        }
    }

    private fun maybeParseSpecialWordBoundary(): Result<Hir?> {
        check(char() == '{')
        val isValidChar: (Char) -> Boolean = { c -> c in 'A'..'Z' || c in 'a'..'z' || c == '-' }
        val startPos = pos
        if (!bumpAndBumpSpace()) {
            return Result.failure(Exception(ERR_SPECIAL_WORD_OR_REP_UNEXPECTED_EOF))
        }
        if (!isValidChar(char())) {
            pos = startPos
            char = '{'
            return Result.success(null)
        }

        val scratch = StringBuilder()
        while (!isDone() && isValidChar(char())) {
            scratch.append(char())
            bumpAndBumpSpace()
        }
        if (isDone() || char() != '}') {
            return Result.failure(Exception(ERR_SPECIAL_WORD_BOUNDARY_UNCLOSED))
        }
        bump()
        val look =
            when (scratch.toString()) {
                "start" -> Look.WordStart
                "end" -> Look.WordEnd
                "start-half" -> Look.WordStartHalf
                "end-half" -> Look.WordEndHalf
                else -> return Result.failure(Exception(ERR_SPECIAL_WORD_BOUNDARY_UNRECOGNIZED))
            }
        return Result.success(Hir.look(look))
    }

    private fun parseHex(): Result<Hir> {
        val digitLen =
            when (val ch = char()) {
                'x' -> 2
                'u' -> 4
                'U' -> 8
                else -> error("invalid start of fixed length hexadecimal number $ch")
            }
        if (!bumpAndBumpSpace()) {
            return Result.failure(Exception(ERR_HEX_UNEXPECTED_EOF))
        }
        return if (char() == '{') {
            parseHexBrace()
        } else {
            parseHexDigits(digitLen)
        }
    }

    private fun parseHexDigits(digitLen: Int): Result<Hir> {
        val scratch = StringBuilder()
        for (i in 0 until digitLen) {
            if (i > 0 && !bumpAndBumpSpace()) {
                return Result.failure(Exception(ERR_HEX_FIXED_UNEXPECTED_EOF))
            }
            if (!isHex(char())) {
                return Result.failure(Exception(ERR_HEX_FIXED_INVALID_DIGIT))
            }
            scratch.append(char())
        }
        bumpAndBumpSpace()
        val code = scratch.toString().toIntOrNull(16)
        if (code == null || code > 0x10FFFF || (code in 0xD800..0xDFFF)) {
            return Result.failure(Exception(ERR_HEX_FIXED_INVALID))
        }
        return Result.success(hirChar(code.toChar()))
    }

    private fun parseHexBrace(): Result<Hir> {
        val scratch = StringBuilder()
        while (bumpAndBumpSpace() && char() != '}') {
            if (!isHex(char())) {
                return Result.failure(Exception(ERR_HEX_BRACE_INVALID_DIGIT))
            }
            scratch.append(char())
        }
        if (isDone()) {
            return Result.failure(Exception(ERR_HEX_BRACE_UNEXPECTED_EOF))
        }
        check(char() == '}')
        bumpAndBumpSpace()

        if (scratch.isEmpty()) {
            return Result.failure(Exception(ERR_HEX_BRACE_EMPTY))
        }
        val code = scratch.toString().toIntOrNull(16)
        if (code == null || code > 0x10FFFF || (code in 0xD800..0xDFFF)) {
            return Result.failure(Exception(ERR_HEX_BRACE_INVALID))
        }
        return Result.success(hirChar(code.toChar()))
    }

    private fun parseDecimal(): Result<UInt> {
        val scratch = StringBuilder()
        while (!isDone() && char().isWhitespace()) {
            bump()
        }
        while (!isDone() && char() in '0'..'9') {
            scratch.append(char())
            bumpAndBumpSpace()
        }
        while (!isDone() && char().isWhitespace()) {
            bumpAndBumpSpace()
        }
        if (scratch.isEmpty()) {
            return Result.failure(Exception(ERR_DECIMAL_NO_DIGITS))
        }
        val value =
            scratch.toString().toUIntOrNull()
                ?: return Result.failure(Exception(ERR_DECIMAL_INVALID))
        return Result.success(value)
    }

    private fun parseUncountedRepetition(concat: MutableList<Hir>): Result<MutableList<Hir>> {
        if (concat.isEmpty()) {
            return Result.failure(Exception(ERR_UNCOUNTED_REP_SUB_MISSING))
        }
        val sub = concat.removeAt(concat.size - 1)
        val (min, max) =
            when (val c = char()) {
                '?' -> Pair(0u, 1u)
                '*' -> Pair(0u, null)
                '+' -> Pair(1u, null)
                else -> error("unrecognized repetition operator '$c'")
            }
        var greedy = true
        if (bump() && char == '?') {
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
        check(char() == '{') { "expected opening brace" }
        if (concat.isEmpty()) {
            return Result.failure(Exception(ERR_COUNTED_REP_SUB_MISSING))
        }
        val sub = concat.removeAt(concat.size - 1)
        if (!bumpAndBumpSpace()) {
            return Result.failure(Exception(ERR_COUNTED_REP_UNCLOSED))
        }
        val min = parseDecimal().getOrElse { return Result.failure(it) }
        var max: UInt? = min
        if (isDone()) {
            return Result.failure(Exception(ERR_COUNTED_REP_MIN_UNCLOSED))
        }
        if (char() == ',') {
            if (!bumpAndBumpSpace()) {
                return Result.failure(Exception(ERR_COUNTED_REP_COMMA_UNCLOSED))
            }
            if (char() != '}') {
                max = parseDecimal().getOrElse { return Result.failure(it) }
            } else {
                max = null
            }
            if (isDone()) {
                return Result.failure(Exception(ERR_COUNTED_REP_MIN_MAX_UNCLOSED))
            }
        }
        if (char() != '}') {
            return Result.failure(Exception(ERR_COUNTED_REP_INVALID))
        }

        var greedy = true
        if (bumpAndBumpSpace() && char == '?') {
            greedy = false
            bump()
        }
        if (flags.swapGreed) {
            greedy = !greedy
        }
        if (max != null && min > max) {
            return Result.failure(Exception(ERR_COUNTED_REP_INVALID_RANGE))
        }
        concat.add(Hir.repetition(Repetition(min = min, max = max, greedy = greedy, sub = sub)))
        return Result.success(concat)
    }

    private fun parseGroup(): Result<Hir?> {
        check(char() == '(')
        bumpAndBumpSpace()
        if (isLookaroundPrefix()) {
            return Result.failure(Exception(ERR_LOOK_UNSUPPORTED))
        }
        if (bumpIf("?P<") || bumpIf("?<")) {
            val index = nextCaptureIndex().getOrElse { return Result.failure(it) }
            val name = parseCaptureName().getOrElse { return Result.failure(it) }
            val sub = parseInner().getOrElse { return Result.failure(it) }
            val cap = Capture(index = index, name = name, sub = sub)
            return Result.success(Hir.capture(cap))
        } else if (bumpIf("?")) {
            if (isDone()) {
                return Result.failure(Exception(ERR_UNCLOSED_GROUP_QUESTION))
            }
            val start = pos
            val parsedFlags = parseFlags().getOrElse { return Result.failure(it) }
            flags = parsedFlags
            val consumed = pos - start
            if (char == ')') {
                if (consumed == 0) {
                    return Result.failure(Exception(ERR_EMPTY_FLAGS))
                }
                return Result.success(null)
            } else {
                check(char == ':')
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
            return Result.failure(Exception(ERR_MISSING_GROUP_NAME))
        }
        val start = pos
        while (true) {
            if (char() == '>') {
                break
            }
            if (!isCaptureChar(char(), pos == start)) {
                return Result.failure(Exception(ERR_INVALID_GROUP_NAME))
            }
            if (!bump()) {
                break
            }
        }
        val end = pos
        if (isDone()) {
            return Result.failure(Exception(ERR_UNCLOSED_GROUP_NAME))
        }
        check(char() == '>')
        bump()
        val name = pattern.substring(start, end)
        if (name.isEmpty()) {
            return Result.failure(Exception(ERR_EMPTY_GROUP_NAME))
        }
        addCaptureName(name).getOrElse { return Result.failure(it) }
        return Result.success(name)
    }

    private fun parseFlags(): Result<Flags> {
        val f = flags.copy()
        var negate = false
        var lastWasNegation = false
        val seen = BooleanArray(128)

        while (char != ':' && char != ')') {
            if (char() == '-') {
                lastWasNegation = true
                if (negate) {
                    return Result.failure(Exception(ERR_FLAG_REPEATED_NEGATION))
                }
                negate = true
            } else {
                lastWasNegation = false
                parseFlag(f, negate).getOrElse { return Result.failure(it) }
                val flagByte = char().code
                if (flagByte in 0..127) {
                    if (seen[flagByte]) {
                        return Result.failure(Exception(ERR_FLAG_DUPLICATE))
                    }
                    seen[flagByte] = true
                }
            }
            if (!bump()) {
                return Result.failure(Exception(ERR_FLAG_UNEXPECTED_EOF))
            }
        }
        if (lastWasNegation) {
            return Result.failure(Exception(ERR_FLAG_DANGLING_NEGATION))
        }
        return Result.success(f)
    }

    private fun parseFlag(f: Flags, negate: Boolean): Result<Unit> {
        val enabled = !negate
        when (char()) {
            'i' -> f.caseInsensitive = enabled
            'm' -> f.multiLine = enabled
            's' -> f.dotMatchesNewLine = enabled
            'U' -> f.swapGreed = enabled
            'R' -> f.crlf = enabled
            'x' -> f.ignoreWhitespace = enabled
            'u' -> {} // No-op compatibility flag
            else -> return Result.failure(Exception(ERR_FLAG_UNRECOGNIZED))
        }
        return Result.success(Unit)
    }

    private fun parseClass(): Result<Hir> {
        check(char() == '[')
        val union = mutableListOf<ClassRange>()
        if (!bumpAndBumpSpace()) {
            return Result.failure(Exception(ERR_CLASS_UNCLOSED))
        }
        val negate =
            if (char() != '^') {
                false
            } else {
                if (!bumpAndBumpSpace()) {
                    return Result.failure(Exception(ERR_CLASS_UNCLOSED_AFTER_NEGATION))
                }
                true
            }

        while (char() == '-') {
            union.add(ClassRange('-', '-'))
            if (!bumpAndBumpSpace()) {
                return Result.failure(Exception(ERR_CLASS_UNCLOSED_AFTER_DASH))
            }
        }

        if (union.isEmpty() && char() == ']') {
            union.add(ClassRange(']', ']'))
            if (!bumpAndBumpSpace()) {
                return Result.failure(Exception(ERR_CLASS_UNCLOSED_AFTER_CLOSING))
            }
        }

        while (true) {
            bumpSpace()
            if (isDone()) {
                return Result.failure(Exception(ERR_CLASS_UNCLOSED))
            }
            when (char()) {
                '[' -> {
                    val posix = maybeParsePosixClass()
                    if (posix != null) {
                        union.addAll(posix.ranges)
                        continue
                    }
                    return Result.failure(Exception(ERR_CLASS_NEST_UNSUPPORTED))
                }
                ']' -> {
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
                '&' ->
                    if (peek() == '&') {
                        return Result.failure(Exception(ERR_CLASS_INTERSECTION_UNSUPPORTED))
                    } else {
                        parseClassRange(union).getOrElse { return Result.failure(it) }
                    }
                '-' ->
                    if (peek() == '-') {
                        return Result.failure(Exception(ERR_CLASS_DIFFERENCE_UNSUPPORTED))
                    } else {
                        parseClassRange(union).getOrElse { return Result.failure(it) }
                    }
                '~' ->
                    if (peek() == '~') {
                        return Result.failure(Exception(ERR_CLASS_SYMDIFFERENCE_UNSUPPORTED))
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
            return Result.failure(Exception(ERR_CLASS_UNCLOSED_AFTER_ITEM))
        }
        if (char() != '-' || peekSpace() == ']' || peekSpace() == '-') {
            val ranges = intoClassItemRanges(prim1).getOrElse { return Result.failure(it) }
            union.addAll(ranges)
            return Result.success(Unit)
        }
        if (!bumpAndBumpSpace()) {
            return Result.failure(Exception(ERR_CLASS_UNCLOSED_AFTER_DASH))
        }
        val prim2 = parseClassItem().getOrElse { return Result.failure(it) }
        val start = intoClassItemRange(prim1).getOrElse { return Result.failure(it) }
        val end = intoClassItemRange(prim2).getOrElse { return Result.failure(it) }
        if (start > end) {
            return Result.failure(Exception(ERR_CLASS_INVALID_RANGE))
        }
        union.add(ClassRange(start, end))
        return Result.success(Unit)
    }

    private fun parseClassItem(): Result<Hir> {
        val ch = char()
        bump()
        return if (ch == '\\') {
            parseEscape()
        } else {
            Result.success(Hir.char(ch))
        }
    }

    private fun maybeParsePosixClass(): Class? {
        check(char() == '[')
        val startPos = pos
        val startChar = char

        val reset: () -> Unit = {
            pos = startPos
            char = startChar
        }

        var negated = false
        if (!bump() || char() != ':') {
            reset()
            return null
        }
        if (!bump()) {
            reset()
            return null
        }
        if (char() == '^') {
            negated = true
            if (!bump()) {
                reset()
                return null
            }
        }
        val nameStart = pos
        while (char() != ':' && bump()) {
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
        val ch = char()
        bump()
        val ranges =
            when (ch) {
                'd', 'D' -> posixClass("digit").getOrThrow()
                's', 'S' -> posixClass("space").getOrThrow()
                'w', 'W' -> posixClass("word").getOrThrow()
                else -> error("invalid Perl class \\$ch")
            }
        val cls = Class(ranges.toMutableList())
        if (ch.isUpperCase()) {
            cls.negate()
        }
        return Hir.classNode(cls)
    }

    private fun hirDot(): Hir =
        if (flags.dotMatchesNewLine) {
            Hir.classNode(Class(mutableListOf(ClassRange('\u0000', '\uFFFF'))))
        } else if (flags.crlf) {
            Hir.classNode(
                Class(
                    mutableListOf(
                        ClassRange('\u0000', '\u0009'),
                        ClassRange('\u000B', '\u000C'),
                        ClassRange('\u000E', '\uFFFF'),
                    ),
                ),
            )
        } else {
            Hir.classNode(
                Class(
                    mutableListOf(
                        ClassRange('\u0000', '\u0009'),
                        ClassRange('\u000B', '\uFFFF'),
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

    private fun hirChar(ch: Char): Hir {
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
    fun recurse(h: Hir, limit: UInt, depth: UInt): Result<Unit> {
        if (depth > limit) {
            return Result.failure(Exception(ERR_TOO_MUCH_NESTING))
        }
        val nextDepth = depth + 1u
        return when (val k = h.kind) {
            is HirKind.Empty, is HirKind.Char, is HirKind.Class, is HirKind.Look -> Result.success(Unit)
            is HirKind.Repetition -> recurse(k.rep.sub, limit, nextDepth)
            is HirKind.Capture -> recurse(k.cap.sub, limit, nextDepth)
            is HirKind.Concat -> {
                for (sub in k.subs) {
                    recurse(sub, limit, nextDepth).getOrElse { return Result.failure(it) }
                }
                Result.success(Unit)
            }
            is HirKind.Alternation -> {
                for (sub in k.subs) {
                    recurse(sub, limit, nextDepth).getOrElse { return Result.failure(it) }
                }
                Result.success(Unit)
            }
        }
    }
    return recurse(hir, limit, 0u)
}

private fun intoClassItemRange(hir: Hir): Result<Char> =
    when (val k = hir.kind) {
        is HirKind.Char -> Result.success(k.ch)
        else -> Result.failure(Exception(ERR_CLASS_INVALID_RANGE_ITEM))
    }

private fun intoClassItemRanges(hir: Hir): Result<List<ClassRange>> =
    when (val k = hir.kind) {
        is HirKind.Char -> Result.success(listOf(ClassRange(k.ch, k.ch)))
        is HirKind.Class -> Result.success(k.classDef.ranges)
        else -> Result.failure(Exception(ERR_CLASS_INVALID_ITEM))
    }

internal fun posixClass(kind: String): Result<List<ClassRange>> {
    val ranges =
        when (kind) {
            "alnum" -> listOf(ClassRange('0', '9'), ClassRange('A', 'Z'), ClassRange('a', 'z'))
            "alpha" -> listOf(ClassRange('A', 'Z'), ClassRange('a', 'z'))
            "ascii" -> listOf(ClassRange('\u0000', '\u007F'))
            "blank" -> listOf(ClassRange('\t', '\t'), ClassRange(' ', ' '))
            "cntrl" -> listOf(ClassRange('\u0000', '\u001F'), ClassRange('\u007F', '\u007F'))
            "digit" -> listOf(ClassRange('0', '9'))
            "graph" -> listOf(ClassRange('!', '~'))
            "lower" -> listOf(ClassRange('a', 'z'))
            "print" -> listOf(ClassRange(' ', '~'))
            "punct" -> listOf(ClassRange('!', '/'), ClassRange(':', '@'), ClassRange('[', '`'), ClassRange('{', '~'))
            "space" ->
                listOf(
                    ClassRange('\t', '\t'),
                    ClassRange('\n', '\n'),
                    ClassRange('\u000B', '\u000B'),
                    ClassRange('\u000C', '\u000C'),
                    ClassRange('\r', '\r'),
                    ClassRange(' ', ' '),
                )
            "upper" -> listOf(ClassRange('A', 'Z'))
            "word" -> listOf(ClassRange('0', '9'), ClassRange('A', 'Z'), ClassRange('_', '_'), ClassRange('a', 'z'))
            "xdigit" -> listOf(ClassRange('0', '9'), ClassRange('A', 'F'), ClassRange('a', 'f'))
            else -> return Result.failure(Exception(ERR_POSIX_CLASS_UNRECOGNIZED))
        }
    return Result.success(ranges)
}

private fun isHex(c: Char): Boolean =
    c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

private fun isCaptureChar(c: Char, first: Boolean): Boolean =
    if (first) {
        c == '_' || c.isLetter()
    } else {
        c == '_' || c == '.' || c == '[' || c == ']' || c.isLetterOrDigit()
    }
