import Testing
import RegexLite

@Suite("RegexLite Swift Export Suite")
struct RegexLiteExportTests {
    @Test("Regex matching distinguishes matching and nonmatching text")
    func matchesDigits() {
        let regex = io.github.kotlinmania.regexlite.Regex(pattern: "[0-9]+")
        #expect(regex.isMatch(haystack: "abc123xyz"))
        #expect(!regex.isMatch(haystack: "letters"))
    }

    @Test("Find returns the first match and its UTF-8 byte offsets")
    func firstMatch() throws {
        let regex = io.github.kotlinmania.regexlite.Regex(pattern: "[0-9]+")
        let match = try #require(regex.find(haystack: "abc123xyz"))
        #expect(match.asStr() == "123")
        #expect(match.start == 3)
        #expect(match.end == 6)
        #expect(regex.find(haystack: "letters") == nil)
    }

    @Test("Named capture groups and replacement work across Swift bridge")
    func capturesAndReplace() throws {
        let regex = io.github.kotlinmania.regexlite.Regex(pattern: "(?<year>[0-9]{4})-(?<month>[0-9]{2})")
        let captures = try #require(regex.captures(haystack: "date: 2026-11"))
        #expect(captures[0]?.asStr() == "2026-11")
        #expect(captures["year"]?.asStr() == "2026")
        #expect(captures["month"]?.asStr() == "11")
        #expect(regex.replaceAll(haystack: "2026-11 and 2027-01", rep: "$month/$year") == "11/2026 and 01/2027")
    }

    @Test("Negated shorthand classes and astral Unicode scalars are handled cleanly")
    func negatedClassesAndAstralUnicode() throws {
        let negatedWord = io.github.kotlinmania.regexlite.Regex(pattern: "[^\\w.@]+")
        #expect(negatedWord.replaceAll(haystack: "ab@15.x", rep: "-") == "ab@15.x")

        let aOnly = io.github.kotlinmania.regexlite.Regex(pattern: "a")
        #expect(!aOnly.isMatch(haystack: "\u{10061}"))

        let poop = io.github.kotlinmania.regexlite.Regex(pattern: "[^a]")
        let poopMatch = try #require(poop.find(haystack: "💩"))
        #expect(poopMatch.start == 0)
        #expect(poopMatch.end == 4)

        #expect(io.github.kotlinmania.regexlite.hir.escape(pattern: "a.b[c]") == "a\\.b\\[c\\]")
    }
}
