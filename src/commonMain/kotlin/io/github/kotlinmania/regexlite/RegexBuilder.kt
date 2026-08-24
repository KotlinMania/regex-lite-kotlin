// port-lint: source string.rs
package io.github.kotlinmania.regexlite

import io.github.kotlinmania.regexlite.hir.Hir
import io.github.kotlinmania.regexlite.nfa.Nfa
import io.github.kotlinmania.regexlite.pikevm.Cache
import io.github.kotlinmania.regexlite.pikevm.PikeVM
import io.github.kotlinmania.regexlite.hir.Config as HirConfig
import io.github.kotlinmania.regexlite.nfa.Config as NfaConfig

/**
 * A configurable builder for a [Regex].
 */
class RegexBuilder(
    private val pattern: String,
) {
    private var hirConfig = HirConfig()
    private var nfaConfig = NfaConfig()

    /**
     * This configures whether to enable ASCII case insensitive matching for
     * the entire pattern.
     */
    fun caseInsensitive(yes: Boolean): RegexBuilder =
        apply {
            hirConfig.flags.caseInsensitive = yes
        }

    /**
     * This configures multi-line mode for the entire pattern.
     */
    fun multiLine(yes: Boolean): RegexBuilder =
        apply {
            hirConfig.flags.multiLine = yes
        }

    /**
     * This configures dot-matches-new-line mode for the entire pattern.
     */
    fun dotMatchesNewLine(yes: Boolean): RegexBuilder =
        apply {
            hirConfig.flags.dotMatchesNewLine = yes
        }

    /**
     * This configures CRLF mode for the entire pattern.
     */
    fun crlf(yes: Boolean): RegexBuilder =
        apply {
            hirConfig.flags.crlf = yes
        }

    /**
     * This configures swap-greed mode for the entire pattern.
     */
    fun swapGreed(yes: Boolean): RegexBuilder =
        apply {
            hirConfig.flags.swapGreed = yes
        }

    /**
     * This configures verbose mode for the entire pattern.
     */
    fun ignoreWhitespace(yes: Boolean): RegexBuilder =
        apply {
            hirConfig.flags.ignoreWhitespace = yes
        }

    /**
     * Sets the approximate size limit, in bytes, of the compiled regex.
     */
    fun sizeLimit(limit: Int): RegexBuilder =
        apply {
            nfaConfig.sizeLimit = limit
        }

    /**
     * Set the nesting limit for this parser.
     */
    fun nestLimit(limit: UInt): RegexBuilder =
        apply {
            hirConfig.nestLimit = limit
        }

    /**
     * Compiles the pattern given to [RegexBuilder] with the configuration set on this builder.
     */
    fun build(): Result<Regex> =
        runCatching {
            val hir = Hir.parse(hirConfig, pattern).getOrThrow()
            val nfa = Nfa.compile(nfaConfig, pattern, hir).getOrThrow()
            val pikevm = PikeVM(nfa)
            val pool = Pool { Cache(pikevm) }
            Regex(pikevm, pool)
        }
}
