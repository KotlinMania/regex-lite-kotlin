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
}
