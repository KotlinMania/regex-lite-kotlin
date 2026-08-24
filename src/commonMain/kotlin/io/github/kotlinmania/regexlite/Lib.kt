// port-lint: source lib.rs
package io.github.kotlinmania.regexlite

/**
 * Escapes all regular expression meta characters in `pattern`.
 *
 * The string returned can be safely used as a literal in a regular expression.
 */
fun escape(pattern: String): String =
    io.github.kotlinmania.regexlite.hir
        .escape(pattern)
