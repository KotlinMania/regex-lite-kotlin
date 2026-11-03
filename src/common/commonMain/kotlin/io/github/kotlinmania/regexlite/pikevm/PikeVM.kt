// port-lint: source pikevm.rs
package io.github.kotlinmania.regexlite.pikevm

import io.github.kotlinmania.regexlite.decodeLossy
import io.github.kotlinmania.regexlite.nfa.Nfa
import io.github.kotlinmania.regexlite.nfa.State
import io.github.kotlinmania.regexlite.nfa.StateID
import kotlin.math.max

internal class PikeVM(
    val nfa: Nfa,
) {
    fun nfa(): Nfa = nfa

    fun createCache(): Cache = Cache(this)

    fun findIter(cache: Cache, haystack: ByteArray): FindMatches =
        FindMatches(
            pikevm = this,
            cache = cache,
            haystack = haystack,
            at = 0,
            slots = arrayOfNulls(2),
            lastMatchEnd = null,
        )

    fun capturesIter(cache: Cache, haystack: ByteArray): CapturesMatches {
        val len = nfa.groupLen() * 2
        return CapturesMatches(
            it =
                FindMatches(
                    pikevm = this,
                    cache = cache,
                    haystack = haystack,
                    at = 0,
                    slots = arrayOfNulls(len),
                    lastMatchEnd = null,
                ),
        )
    }

    fun search(
        cache: Cache,
        haystack: ByteArray,
        start: Int,
        end: Int,
        earliest: Boolean,
        slots: Array<Int?>,
    ): Boolean {
        cache.setupSearch(slots.size)
        if (start > end) {
            return false
        }

        val stack = cache.stack
        var curr = cache.curr
        var next = cache.next
        val startId = nfa.start
        val anchored = nfa.isStartAnchored
        var matched = false

        var at = start
        while (at <= end) {
            if (curr.set.isEmpty()) {
                if (matched) {
                    break
                }
                if (anchored && at > start) {
                    break
                }
            }

            if (!matched) {
                val absSlots = next.slotTable.allAbsent()
                epsilonClosure(stack, absSlots, curr, haystack, at, startId)
            }

            val (cp, len) =
                if (at < haystack.size) {
                    decodeLossy(haystack.copyOfRange(at, haystack.size))
                } else {
                    Pair(0, 0)
                }
            val ch = cp.toChar()

            if (nexts(stack, curr, next, haystack, at, ch, len, slots)) {
                matched = true
            }

            if ((earliest && matched) || len == 0) {
                break
            }

            val tmp = curr
            curr = next
            next = tmp
            next.set.clear()
            at += len
        }
        cache.curr = curr
        cache.next = next
        return matched
    }

    private fun nexts(
        stack: MutableList<FollowEpsilon>,
        curr: ActiveStates,
        next: ActiveStates,
        haystack: ByteArray,
        at: Int,
        atCh: Char,
        atLen: Int,
        slots: Array<Int?>,
    ): Boolean {
        for (sid in curr.set) {
            if (nextState(stack, curr.slotTable, next, haystack, at, atCh, atLen, sid)) {
                curr.slotTable.copyRow(sid, slots)
                return true
            }
        }
        return false
    }

    private fun nextState(
        stack: MutableList<FollowEpsilon>,
        currSlotTable: SlotTable,
        next: ActiveStates,
        haystack: ByteArray,
        at: Int,
        atCh: Char,
        atLen: Int,
        sid: StateID,
    ): Boolean {
        return when (val state = nfa.state(sid)) {
            State.Fail, is State.Goto, is State.Splits, is State.Capture -> false
            is State.CharState -> {
                if (atCh == state.ch && atLen > 0) {
                    val slots = currSlotTable.getRow(sid)
                    val nextAt = at + atLen
                    epsilonClosure(stack, slots, next, haystack, nextAt, state.target)
                }
                false
            }
            is State.Ranges -> {
                for ((start, end) in state.ranges) {
                    if (start > atCh) {
                        break
                    } else if (start <= atCh && atCh <= end) {
                        if (atLen == 0) {
                            return false
                        }
                        val slots = currSlotTable.getRow(sid)
                        val nextAt = at + atLen
                        epsilonClosure(stack, slots, next, haystack, nextAt, state.target)
                    }
                }
                false
            }
            State.Match -> true
        }
    }

    private fun epsilonClosure(
        stack: MutableList<FollowEpsilon>,
        currSlots: Array<Int?>,
        next: ActiveStates,
        haystack: ByteArray,
        at: Int,
        sid: StateID,
    ) {
        stack.add(FollowEpsilon.Explore(sid))
        while (stack.isNotEmpty()) {
            when (val frame = stack.removeAt(stack.size - 1)) {
                is FollowEpsilon.RestoreCapture -> {
                    currSlots[frame.slot.toInt()] = frame.offset
                }
                is FollowEpsilon.Explore -> {
                    epsilonClosureExplore(stack, currSlots, next, haystack, at, frame.sid)
                }
            }
        }
    }

    private fun epsilonClosureExplore(
        stack: MutableList<FollowEpsilon>,
        currSlots: Array<Int?>,
        next: ActiveStates,
        haystack: ByteArray,
        at: Int,
        initialSid: StateID,
    ) {
        var sid = initialSid
        while (true) {
            if (!next.set.insert(sid)) {
                return
            }
            when (val state = nfa.state(sid)) {
                State.Fail, State.Match, is State.CharState, is State.Ranges -> {
                    next.slotTable.setRow(sid, currSlots)
                    return
                }
                is State.Goto -> {
                    val look = state.look
                    if (look != null && !look.isMatch(haystack, at)) {
                        return
                    }
                    sid = state.target
                }
                is State.Splits -> {
                    if (!state.reverse) {
                        if (state.targets.isEmpty()) {
                            return
                        }
                        sid = state.targets[0]
                        for (i in (state.targets.size - 1) downTo 1) {
                            stack.add(FollowEpsilon.Explore(state.targets[i]))
                        }
                    } else {
                        if (state.targets.isEmpty()) {
                            return
                        }
                        sid = state.targets[state.targets.size - 1]
                        for (i in 0 until (state.targets.size - 1)) {
                            stack.add(FollowEpsilon.Explore(state.targets[i]))
                        }
                    }
                }
                is State.Capture -> {
                    val slotIdx = state.slot.toInt()
                    if (slotIdx < currSlots.size) {
                        stack.add(FollowEpsilon.RestoreCapture(state.slot, currSlots[slotIdx]))
                        currSlots[slotIdx] = at
                    }
                    sid = state.target
                }
            }
        }
    }
}

internal class FindMatches(
    val pikevm: PikeVM,
    val cache: Cache,
    val haystack: ByteArray,
    var at: Int,
    val slots: Array<Int?>,
    var lastMatchEnd: Int?,
) : Iterator<Pair<Int, Int>> {
    override fun hasNext(): Boolean = findNext() != null

    private var nextItem: Pair<Int, Int>? = null
    private var computed = false

    private fun findNext(): Pair<Int, Int>? {
        if (computed) {
            return nextItem
        }
        if (!pikevm.search(
                cache = cache,
                haystack = haystack,
                start = at,
                end = haystack.size,
                earliest = false,
                slots = slots,
            )
        ) {
            computed = true
            nextItem = null
            return null
        }
        var m = Pair(slots[0]!!, slots[1]!!)
        if (m.first >= m.second) {
            val adjusted = handleOverlappingEmptyMatch(m)
            if (adjusted == null) {
                computed = true
                nextItem = null
                return null
            }
            m = adjusted
        }
        at = m.second
        lastMatchEnd = m.second
        nextItem = m
        computed = true
        return nextItem
    }

    override fun next(): Pair<Int, Int> {
        val res = findNext() ?: throw NoSuchElementException()
        computed = false
        nextItem = null
        return res
    }

    private fun handleOverlappingEmptyMatch(initialM: Pair<Int, Int>): Pair<Int, Int>? {
        var m = initialM
        if (m.second == lastMatchEnd) {
            val len =
                if (at < haystack.size) {
                    max(1, decodeLossy(haystack.copyOfRange(at, haystack.size)).second)
                } else {
                    1
                }
            at += len
            if (!pikevm.search(
                    cache = cache,
                    haystack = haystack,
                    start = at,
                    end = haystack.size,
                    earliest = false,
                    slots = slots,
                )
            ) {
                return null
            }
            m = Pair(slots[0]!!, slots[1]!!)
        }
        return m
    }
}

internal class CapturesMatches(
    private val it: FindMatches,
) : Iterator<Array<Int?>> {
    override fun hasNext(): Boolean = it.hasNext()

    override fun next(): Array<Int?> {
        if (!hasNext()) {
            throw NoSuchElementException()
        }
        it.next()
        return it.slots.copyOf()
    }
}

internal class Cache(
    val re: PikeVM,
) {
    val stack: MutableList<FollowEpsilon> = mutableListOf()
    var curr: ActiveStates = ActiveStates(re)
    var next: ActiveStates = ActiveStates(re)

    fun setupSearch(capturesSlotLen: Int) {
        stack.clear()
        curr.setupSearch(capturesSlotLen)
        next.setupSearch(capturesSlotLen)
    }

    fun swapCurrNext() {
        val tmp = curr
        curr = next
        next = tmp
    }
}

internal class ActiveStates(
    re: PikeVM,
) {
    val set: SparseSet = SparseSet(re.nfa.len())
    val slotTable: SlotTable = SlotTable()

    init {
        slotTable.reset(re)
    }

    fun setupSearch(capturesSlotLen: Int) {
        set.clear()
        slotTable.setupSearch(capturesSlotLen)
    }
}

internal class SlotTable {
    private var table: Array<Int?> = emptyArray()
    private var slotsPerState: Int = 0
    private var slotsForCaptures: Int = 0

    fun reset(re: PikeVM) {
        val nfa = re.nfa
        slotsPerState = nfa.groupLen() * 2
        slotsForCaptures = slotsPerState
        val len = (nfa.len() + 1) * slotsPerState
        table = arrayOfNulls(len)
    }

    fun setupSearch(capturesSlotLen: Int) {
        slotsForCaptures = capturesSlotLen
    }

    fun getRow(sid: StateID): Array<Int?> {
        val i = sid.toInt() * slotsPerState
        return table.copyOfRange(i, i + slotsForCaptures)
    }

    fun setRow(sid: StateID, slots: Array<Int?>) {
        val i = sid.toInt() * slotsPerState
        val count = minOf(slotsForCaptures, slots.size)
        for (idx in 0 until count) {
            table[i + idx] = slots[idx]
        }
    }

    fun copyRow(sid: StateID, dest: Array<Int?>) {
        val i = sid.toInt() * slotsPerState
        val count = minOf(slotsForCaptures, dest.size)
        for (idx in 0 until count) {
            dest[idx] = table[i + idx]
        }
    }

    fun allAbsent(): Array<Int?> = arrayOfNulls(slotsForCaptures)
}

internal sealed interface FollowEpsilon {
    data class Explore(
        val sid: StateID,
    ) : FollowEpsilon

    data class RestoreCapture(
        val slot: UInt,
        val offset: Int?,
    ) : FollowEpsilon
}

internal class SparseSet(
    capacity: Int,
) : Iterable<StateID> {
    private var len: Int = 0
    private var dense: IntArray = IntArray(capacity)
    private var sparse: IntArray = IntArray(capacity)

    fun capacity(): Int = dense.size

    fun len(): Int = len

    fun isEmpty(): Boolean = len == 0

    fun insert(id: StateID): Boolean {
        if (contains(id)) {
            return false
        }
        val index = len
        val idInt = id.toInt()
        dense[index] = idInt
        sparse[idInt] = index
        len += 1
        return true
    }

    fun contains(id: StateID): Boolean {
        val idInt = id.toInt()
        if (idInt >= sparse.size) return false
        val index = sparse[idInt]
        return index < len && dense[index] == idInt
    }

    fun clear() {
        len = 0
    }

    override fun iterator(): Iterator<StateID> {
        return object : Iterator<StateID> {
            private var cursor = 0

            override fun hasNext(): Boolean = cursor < len

            override fun next(): StateID {
                if (cursor >= len) throw NoSuchElementException()
                val item = dense[cursor].toUInt()
                cursor += 1
                return item
            }
        }
    }
}
