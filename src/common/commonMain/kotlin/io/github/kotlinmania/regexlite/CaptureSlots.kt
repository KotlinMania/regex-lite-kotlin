package io.github.kotlinmania.regexlite

/** Nullable capture offsets stored without reserving any integer as an absence sentinel. */
internal class CaptureSlots private constructor(
    private val values: IntArray,
    private val present: BooleanArray,
) {
    constructor(size: Int) : this(IntArray(size), BooleanArray(size))

    val size: Int get() = values.size

    operator fun get(index: Int): Int? = if (present[index]) values[index] else null

    operator fun set(index: Int, value: Int?) {
        present[index] = value != null
        if (value != null) {
            values[index] = value
        }
    }

    fun copyOf(): CaptureSlots = CaptureSlots(values.copyOf(), present.copyOf())

    fun copyOfRange(fromIndex: Int, toIndex: Int): CaptureSlots =
        CaptureSlots(values.copyOfRange(fromIndex, toIndex), present.copyOfRange(fromIndex, toIndex))

    fun contentEquals(other: CaptureSlots): Boolean {
        if (size != other.size) {
            return false
        }
        for (index in values.indices) {
            if (present[index] != other.present[index] || (present[index] && values[index] != other.values[index])) {
                return false
            }
        }
        return true
    }

    fun contentHashCode(): Int {
        var result = 1
        for (index in values.indices) {
            result = 31 * result + if (present[index]) values[index] else 0
        }
        return result
    }
}
