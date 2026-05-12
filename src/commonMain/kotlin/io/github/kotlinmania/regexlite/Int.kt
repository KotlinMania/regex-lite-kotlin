// port-lint: source src/int.rs
package io.github.kotlinmania.regexlite

/**
 * An extension that adds a routine for converting a `UInt` value to a
 * `usize`-shaped `Int`.
 */
internal fun UInt.asUsize(): Int {
    // OK because we require 32 or 64 bit targets. Therefore, every UInt
    // necessarily fits into the platform's signed integer offset type.
    return this.toInt()
}

/**
 * An `Int` that can never be `Int.MAX_VALUE`.
 *
 * This is similar to a non-zero integer, but instead of not permitting
 * a zero value, this does not permit a max value.
 *
 * This is useful in certain contexts where one wants to optimize the memory
 * usage of things that contain match offsets. Namely, since slice/string
 * lengths are guaranteed to never reach `Int.MAX_VALUE`, the platform can
 * use `Int.MAX_VALUE` as a sentinel to indicate that no match was found.
 */
internal class NonMaxUsize private constructor(private val value: Int) {
    /**
     * Return the underlying `Int` value. The returned value is guaranteed
     * to not equal `Int.MAX_VALUE`.
     */
    fun get(): Int = value

    override fun equals(other: Any?): Boolean =
        this === other || (other is NonMaxUsize && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    operator fun compareTo(other: NonMaxUsize): Int = value.compareTo(other.value)

    // A custom string form is provided here because seeing the internal repr
    // can be quite surprising if you aren't expecting it. e.g.,
    // 'NonMaxUsize(5)' vs just '5'.
    override fun toString(): String = value.toString()

    companion object {
        /**
         * Create a new [NonMaxUsize] from the given value.
         *
         * This returns `null` only when the given value is equal to
         * `Int.MAX_VALUE`.
         */
        fun new(value: Int): NonMaxUsize? =
            if (value == Int.MAX_VALUE) null else NonMaxUsize(value)
    }
}
