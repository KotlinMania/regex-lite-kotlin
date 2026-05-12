// port-lint: source src/error.rs
package io.github.kotlinmania.regexlite

/**
 * An error that occurred during parsing or compiling a regular expression.
 *
 * A parse error occurs when the syntax of the regex pattern is not
 * valid. Otherwise, a regex can still fail to build if it would
 * result in a machine that exceeds the configured size limit, via
 * [RegexBuilder.sizeLimit].
 *
 * This error type provides no introspection capabilities. The only thing you
 * can do with it is convert it to a string as a human readable error message.
 */
class Error internal constructor(private val msg: String) {
    override fun toString(): String = msg

    override fun equals(other: Any?): Boolean =
        this === other || (other is Error && msg == other.msg)

    override fun hashCode(): Int = msg.hashCode()
}
