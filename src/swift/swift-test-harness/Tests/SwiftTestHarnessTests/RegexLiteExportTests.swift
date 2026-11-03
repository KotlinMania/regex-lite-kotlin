#if canImport(Testing)
import Testing
import RegexLite

@Suite("RegexLite Swift Export Suite")
struct RegexLiteExportTests {
    @Test("Swift module loads cleanly")
    func swiftModuleLoads() {
        #expect(Bool(true), "RegexLite swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import RegexLite

final class RegexLiteExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "RegexLite swift module imported cleanly")
    }
}
#endif
