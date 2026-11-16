# regex-lite-kotlin

[![GitHub link](https://img.shields.io/badge/GitHub-KotlinMania%2Fregex--lite--kotlin-blue.svg)](https://github.com/KotlinMania/regex-lite-kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kotlinmania/regex-lite-kotlin)](https://central.sonatype.com/artifact/io.github.kotlinmania/regex-lite-kotlin)
[![Build status](https://img.shields.io/github/actions/workflow/status/KotlinMania/regex-lite-kotlin/ci.yml?branch=main)](https://github.com/KotlinMania/regex-lite-kotlin/actions)

A pure Kotlin Multiplatform port of [`regex-lite`](https://github.com/rust-lang/regex/tree/master/regex-lite) from [`rust-lang/regex`](https://github.com/rust-lang/regex), executing patterns on a deterministic Thompson NFA PikeVM across JVM, Android, JavaScript, WebAssembly, Native, and Swift Export without delegating to platform regex engines.

## Why `regex-lite-kotlin`?

- **Consistent Cross-Platform Semantics**: All targets run the same pure-Kotlin parser and PikeVM over UTF-8 codepoints (`U+0000..U+10FFFF`), avoiding target-specific `kotlin.text.Regex` discrepancies across JVM, JS, Wasm, and Apple/Linux/Windows Native targets.
- **Worst-Case Linear Search Time**: Guaranteed $O(m \cdot n)$ search time (`Regex.isMatch`, `Regex.find`, `Regex.captures`) with configurable `sizeLimit` and `nestLimit` bounds—immune to catastrophic backtracking.
- **Minimal Dependency & Binary Footprint**: Zero external Unicode property tables. ASCII Perl classes (`\d`, `\w`, `\s`) and full UTF-8 scalar matching are built in.
- **Choosing Between Crates**:
  - Use **`regex-lite-kotlin`** for small binary size and fast compile times when you do not need `\p{...}` Unicode property classes or Unicode-aware case folding.
  - Use **[`regex-kotlin`](https://github.com/KotlinMania/regex-kotlin)** for full Unicode property support (`\p{L}`, `\P{L}`, nested character-class set operations) and multi-engine SIMD/DFA acceleration.
  - Use **[`fancy-regex-kotlin`](https://github.com/KotlinMania/fancy-regex-kotlin)** when you need look-around assertions or backreferences.

## Installation

```kotlin
dependencies {
    implementation("io.github.kotlinmania:regex-lite-kotlin:0.1.2")
}
```

## Kotlin Usage

```kotlin
import io.github.kotlinmania.regexlite.Regex
import io.github.kotlinmania.regexlite.RegexBuilder

val dateRegex = Regex("""(?<year>\d{4})-(?<month>\d{2})-(?<day>\d{2})""")
val caps = dateRegex.captures("2026-11-16")!!
println(caps["year"]?.asStr())  // "2026"
println(dateRegex.replaceAll("2026-11-16", $$"$month/$day/$year"))  // "11/16/2026"

val insensitive = RegexBuilder("hello")
    .caseInsensitive(true)
    .build()
    .getOrThrow()
assert(insensitive.isMatch("HELLO"))
```

## Swift Usage (via Swift Export)

```swift
import RegexLite

let regex = io.github.kotlinmania.regexlite.Regex(pattern: "(?<year>[0-9]{4})-(?<month>[0-9]{2})")
if let caps = regex.captures(haystack: "Release 2026-11") {
    print(caps["year"]?.asStr() ?? "")
}
let formatted = regex.replaceAll(haystack: "2026-11", rep: "$month/$year")
```

## Building & Testing

```bash
./gradlew build
./gradlew test
```

## Supported Targets

- JVM & Android (API 24+)
- JavaScript & WebAssembly (`wasmJs`, `wasmWasi`)
- Linux (`linuxX64`, `linuxArm64`), Windows (`mingwX64`), Android Native (`androidNativeArm64`, `androidNativeX64`)
- Apple Silicon (`macosArm64`, `iosArm64`, `iosSimulatorArm64`, `tvosArm64`, `tvosSimulatorArm64`, `watchosDeviceArm64`, `watchosSimulatorArm64`) with Swift Export

## License

Licensed under the MIT License ([LICENSE](LICENSE)), matching [`rust-lang/regex`](https://github.com/rust-lang/regex).  
Original upstream work copyrighted by the `rust-lang/regex` authors. Kotlin port copyright (c) 2026 Sydney Renee and The Solace Project.
