import Testing
import RegexLite

@Suite("RegexLite Swift Export Suite")
struct RegexLiteExportTests {
    @Test("Swift module loads cleanly")
    func swiftModuleLoads() {
        #expect(Bool(true), "RegexLite swift module imported cleanly")
    }
}
