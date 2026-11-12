import Testing
import RegexLite

@Suite("RegexLite match boundaries")
struct RegexLiteMatchTests {
    @Test("Find returns the first match and its byte offsets")
    func firstMatch() throws {
        let regex = io.github.kotlinmania.regexlite.Regex(pattern: "[0-9]+")
        let match = try #require(regex.find(haystack: "abc123xyz"))
        #expect(match.asStr() == "123")
        #expect(match.start == 3)
        #expect(match.end == 6)
        #expect(regex.find(haystack: "letters") == nil)
    }
}
