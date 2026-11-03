// port-lint: source nfa.rs
package io.github.kotlinmania.regexlite.nfa

import io.github.kotlinmania.regexlite.Error
import io.github.kotlinmania.regexlite.hir.Hir
import io.github.kotlinmania.regexlite.hir.HirKind
import io.github.kotlinmania.regexlite.hir.Look

typealias StateID = UInt

internal data class Config(
    var sizeLimit: Int? = 10 * (1 shl 20),
)

internal sealed interface State {
    data class CharState(
        var target: StateID,
        val ch: Char,
    ) : State

    data class Ranges(
        var target: StateID,
        val ranges: List<Pair<Char, Char>>,
    ) : State

    data class Splits(
        val targets: MutableList<StateID>,
        val reverse: Boolean,
    ) : State

    data class Goto(
        var target: StateID,
        val look: Look?,
    ) : State

    data class Capture(
        var target: StateID,
        val slot: UInt,
    ) : State

    data object Fail : State

    data object Match : State

    fun memoryUsage(): Int =
        when (this) {
            is CharState, is Goto, is Capture, Fail, Match -> 0
            is Splits -> targets.size * 4
            is Ranges -> ranges.size * 8
        }
}

internal fun iterSplits(splits: List<StateID>, reverse: Boolean): List<StateID> = if (reverse) splits.asReversed() else splits

internal class Nfa internal constructor(
    val pattern: String,
    val states: MutableList<State>,
    var start: StateID,
    var isStartAnchored: Boolean,
    var isMatchEmpty: Boolean,
    var staticExplicitCapturesLen: Int?,
    val capNameToIndex: MutableMap<String, UInt>,
    val capIndexToName: MutableList<String?>,
    var memoryExtra: Int,
) {
    companion object {
        fun compile(
            config: Config,
            pattern: String,
            hir: Hir,
        ): Result<Nfa> = Compiler(config, pattern).compile(hir)
    }

    fun state(id: StateID): State = states[id.toInt()]

    fun len(): Int = states.size

    fun toIndex(name: String): Int? = capNameToIndex[name]?.toInt()

    fun captureNames(): List<String?> = capIndexToName

    fun groupLen(): Int = capIndexToName.size

    fun memoryUsage(): Int = (states.size * 32) + (capIndexToName.size * 16) + memoryExtra
}

private class Compiler(
    private val config: Config,
    pattern: String,
) {
    private val nfa: Nfa =
        Nfa(
            pattern = pattern,
            states = mutableListOf(),
            start = 0u,
            isStartAnchored = false,
            isMatchEmpty = false,
            staticExplicitCapturesLen = null,
            capNameToIndex = mutableMapOf(),
            capIndexToName = mutableListOf(),
            memoryExtra = 0,
        )

    fun compile(hir: Hir): Result<Nfa> {
        nfa.isStartAnchored = hir.isStartAnchored
        nfa.isMatchEmpty = hir.isMatchEmpty
        nfa.staticExplicitCapturesLen = hir.staticExplicitCapturesLen
        val compiled = cCapture(0u, null, hir).getOrElse { return Result.failure(it) }
        val mat = add(State.Match).getOrElse { return Result.failure(it) }
        patch(compiled.end, mat).getOrElse { return Result.failure(it) }
        nfa.start = compiled.start
        return Result.success(nfa)
    }

    private fun c(hir: Hir): Result<ThompsonRef> =
        when (val k = hir.kind) {
            is HirKind.Empty -> cEmpty()
            is HirKind.Char -> cChar(k.ch)
            is HirKind.Class -> cClass(k.classDef)
            is HirKind.Look -> cLook(k.look)
            is HirKind.Repetition -> cRepetition(k.rep)
            is HirKind.Capture -> cCapture(k.cap.index, k.cap.name, k.cap.sub)
            is HirKind.Concat -> cConcat(k.subs.map { c(it) })
            is HirKind.Alternation -> cAlternation(k.subs.map { c(it) })
        }

    private fun cFail(): Result<ThompsonRef> {
        val id = add(State.Fail).getOrElse { return Result.failure(it) }
        return Result.success(ThompsonRef(start = id, end = id))
    }

    private fun cEmpty(): Result<ThompsonRef> {
        val id = addEmpty().getOrElse { return Result.failure(it) }
        return Result.success(ThompsonRef(start = id, end = id))
    }

    private fun cChar(ch: Char): Result<ThompsonRef> {
        val id = add(State.CharState(target = 0u, ch = ch)).getOrElse { return Result.failure(it) }
        return Result.success(ThompsonRef(start = id, end = id))
    }

    private fun cClass(classDef: io.github.kotlinmania.regexlite.hir.Class): Result<ThompsonRef> {
        val id =
            if (classDef.ranges.isEmpty()) {
                add(State.Fail)
            } else {
                val ranges = classDef.ranges.map { Pair(it.start, it.end) }
                add(State.Ranges(target = 0u, ranges = ranges))
            }.getOrElse { return Result.failure(it) }
        return Result.success(ThompsonRef(start = id, end = id))
    }

    private fun cLook(look: Look): Result<ThompsonRef> {
        val id = add(State.Goto(target = 0u, look = look)).getOrElse { return Result.failure(it) }
        return Result.success(ThompsonRef(start = id, end = id))
    }

    private fun cRepetition(rep: io.github.kotlinmania.regexlite.hir.Repetition): Result<ThompsonRef> {
        val min = rep.min
        val max = rep.max
        return when {
            min == 0u && max == 1u -> cZeroOrOne(rep.sub, rep.greedy)
            max == null -> cAtLeast(rep.sub, rep.greedy, min)
            min == max -> cExactly(rep.sub, min)
            else -> cBounded(rep.sub, rep.greedy, min, max)
        }
    }

    private fun cBounded(
        hir: Hir,
        greedy: Boolean,
        min: UInt,
        max: UInt,
    ): Result<ThompsonRef> {
        val prefix = cExactly(hir, min).getOrElse { return Result.failure(it) }
        if (min == max) {
            return Result.success(prefix)
        }

        val empty = addEmpty().getOrElse { return Result.failure(it) }
        var prevEnd = prefix.end
        var i = min
        while (i < max) {
            val splits =
                add(State.Splits(targets = mutableListOf(), reverse = !greedy))
                    .getOrElse { return Result.failure(it) }
            val compiled = c(hir).getOrElse { return Result.failure(it) }
            patch(prevEnd, splits).getOrElse { return Result.failure(it) }
            patch(splits, compiled.start).getOrElse { return Result.failure(it) }
            patch(splits, empty).getOrElse { return Result.failure(it) }
            prevEnd = compiled.end
            i += 1u
        }
        patch(prevEnd, empty).getOrElse { return Result.failure(it) }
        return Result.success(ThompsonRef(start = prefix.start, end = empty))
    }

    private fun cAtLeast(
        hir: Hir,
        greedy: Boolean,
        n: UInt,
    ): Result<ThompsonRef> {
        if (n == 0u) {
            if (!hir.isMatchEmpty) {
                val splits =
                    add(State.Splits(targets = mutableListOf(), reverse = !greedy))
                        .getOrElse { return Result.failure(it) }
                val compiled = c(hir).getOrElse { return Result.failure(it) }
                patch(splits, compiled.start).getOrElse { return Result.failure(it) }
                patch(compiled.end, splits).getOrElse { return Result.failure(it) }
                return Result.success(ThompsonRef(start = splits, end = splits))
            }

            val compiled = c(hir).getOrElse { return Result.failure(it) }
            val plus =
                add(State.Splits(targets = mutableListOf(), reverse = !greedy))
                    .getOrElse { return Result.failure(it) }
            patch(compiled.end, plus).getOrElse { return Result.failure(it) }
            patch(plus, compiled.start).getOrElse { return Result.failure(it) }

            val question =
                add(State.Splits(targets = mutableListOf(), reverse = !greedy))
                    .getOrElse { return Result.failure(it) }
            val empty = addEmpty().getOrElse { return Result.failure(it) }
            patch(question, compiled.start).getOrElse { return Result.failure(it) }
            patch(question, empty).getOrElse { return Result.failure(it) }
            patch(plus, empty).getOrElse { return Result.failure(it) }
            return Result.success(ThompsonRef(start = question, end = empty))
        } else if (n == 1u) {
            val compiled = c(hir).getOrElse { return Result.failure(it) }
            val splits =
                add(State.Splits(targets = mutableListOf(), reverse = !greedy))
                    .getOrElse { return Result.failure(it) }
            patch(compiled.end, splits).getOrElse { return Result.failure(it) }
            patch(splits, compiled.start).getOrElse { return Result.failure(it) }
            return Result.success(ThompsonRef(start = compiled.start, end = splits))
        } else {
            val prefix = cExactly(hir, n - 1u).getOrElse { return Result.failure(it) }
            val last = c(hir).getOrElse { return Result.failure(it) }
            val splits =
                add(State.Splits(targets = mutableListOf(), reverse = !greedy))
                    .getOrElse { return Result.failure(it) }
            patch(prefix.end, last.start).getOrElse { return Result.failure(it) }
            patch(last.end, splits).getOrElse { return Result.failure(it) }
            patch(splits, last.start).getOrElse { return Result.failure(it) }
            return Result.success(ThompsonRef(start = prefix.start, end = splits))
        }
    }

    private fun cZeroOrOne(
        hir: Hir,
        greedy: Boolean,
    ): Result<ThompsonRef> {
        val splits =
            add(State.Splits(targets = mutableListOf(), reverse = !greedy))
                .getOrElse { return Result.failure(it) }
        val compiled = c(hir).getOrElse { return Result.failure(it) }
        val empty = addEmpty().getOrElse { return Result.failure(it) }
        patch(splits, compiled.start).getOrElse { return Result.failure(it) }
        patch(splits, empty).getOrElse { return Result.failure(it) }
        patch(compiled.end, empty).getOrElse { return Result.failure(it) }
        return Result.success(ThompsonRef(start = splits, end = empty))
    }

    private fun cExactly(hir: Hir, n: UInt): Result<ThompsonRef> {
        val list = mutableListOf<Result<ThompsonRef>>()
        var i = 0u
        while (i < n) {
            list.add(c(hir))
            i += 1u
        }
        return cConcat(list)
    }

    private fun cCapture(
        index: UInt,
        name: String?,
        hir: Hir,
    ): Result<ThompsonRef> {
        val existingGroupsLen = nfa.capIndexToName.size
        val idxInt = index.toInt()
        while (nfa.capIndexToName.size < idxInt) {
            nfa.capIndexToName.add(null)
        }
        if (idxInt >= existingGroupsLen) {
            if (name != null) {
                nfa.capNameToIndex[name] = index
                nfa.capIndexToName.add(name)
                nfa.memoryExtra += name.length + 4
            } else {
                nfa.capIndexToName.add(null)
            }
        }

        val slot = index * 2u
        val start = add(State.Capture(target = 0u, slot = slot)).getOrElse { return Result.failure(it) }
        val inner = c(hir).getOrElse { return Result.failure(it) }
        val end = add(State.Capture(target = 0u, slot = slot + 1u)).getOrElse { return Result.failure(it) }
        patch(start, inner.start).getOrElse { return Result.failure(it) }
        patch(inner.end, end).getOrElse { return Result.failure(it) }

        return Result.success(ThompsonRef(start = start, end = end))
    }

    private fun cConcat(results: List<Result<ThompsonRef>>): Result<ThompsonRef> {
        if (results.isEmpty()) {
            return cEmpty()
        }
        val first = results[0].getOrElse { return Result.failure(it) }
        var start = first.start
        var end = first.end
        for (i in 1 until results.size) {
            val compiled = results[i].getOrElse { return Result.failure(it) }
            patch(end, compiled.start).getOrElse { return Result.failure(it) }
            end = compiled.end
        }
        return Result.success(ThompsonRef(start = start, end = end))
    }

    private fun cAlternation(results: List<Result<ThompsonRef>>): Result<ThompsonRef> {
        if (results.isEmpty()) {
            return cFail()
        }
        val first = results[0].getOrElse { return Result.failure(it) }
        if (results.size == 1) {
            return Ok(first)
        }
        val second = results[1].getOrElse { return Result.failure(it) }

        val splits =
            add(State.Splits(targets = mutableListOf(), reverse = false))
                .getOrElse { return Result.failure(it) }
        val end = addEmpty().getOrElse { return Result.failure(it) }
        patch(splits, first.start).getOrElse { return Result.failure(it) }
        patch(first.end, end).getOrElse { return Result.failure(it) }
        patch(splits, second.start).getOrElse { return Result.failure(it) }
        patch(second.end, end).getOrElse { return Result.failure(it) }

        for (i in 2 until results.size) {
            val compiled = results[i].getOrElse { return Result.failure(it) }
            patch(splits, compiled.start).getOrElse { return Result.failure(it) }
            patch(compiled.end, end).getOrElse { return Result.failure(it) }
        }
        return Result.success(ThompsonRef(start = splits, end = end))
    }

    private fun Ok(value: ThompsonRef): Result<ThompsonRef> = Result.success(value)

    private fun addEmpty(): Result<StateID> =
        add(State.Goto(target = 0u, look = null))

    private fun add(state: State): Result<StateID> {
        val id = nfa.states.size.toUInt()
        nfa.memoryExtra += state.memoryUsage()
        nfa.states.add(state)
        checkSizeLimit().getOrElse { return Result.failure(it) }
        return Result.success(id)
    }

    private fun patch(from: StateID, to: StateID): Result<Unit> {
        var newMemoryExtra = nfa.memoryExtra
        when (val st = nfa.states[from.toInt()]) {
            is State.CharState -> st.target = to
            is State.Ranges -> st.target = to
            is State.Splits -> {
                st.targets.add(to)
                newMemoryExtra += 4
            }
            is State.Goto -> st.target = to
            is State.Capture -> st.target = to
            State.Fail, State.Match -> {}
        }
        if (newMemoryExtra != nfa.memoryExtra) {
            nfa.memoryExtra = newMemoryExtra
            checkSizeLimit().getOrElse { return Result.failure(it) }
        }
        return Result.success(Unit)
    }

    private fun checkSizeLimit(): Result<Unit> {
        val limit = config.sizeLimit
        if (limit != null && nfa.memoryUsage() > limit) {
            return Result.failure(Error("compiled regex exceeded size limit"))
        }
        return Result.success(Unit)
    }
}

private data class ThompsonRef(
    val start: StateID,
    val end: StateID,
)
