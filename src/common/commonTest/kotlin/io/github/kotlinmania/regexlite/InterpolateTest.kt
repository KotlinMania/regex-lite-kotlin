// port-lint: tests interpolate.rs
package io.github.kotlinmania.regexlite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InterpolateTest {
    private fun c(name: String, pos: Int): CaptureRef =
        CaptureRef(Ref.Named(name), pos)

    private fun c(num: Int, pos: Int): CaptureRef =
        CaptureRef(Ref.Number(num), pos)

    private fun find(text: String): CaptureRef? =
        findCapRef(text.encodeToByteArray())

    @Test
    fun findCapRef1() {
        assertEquals(c("foo", 4), find("\$foo"))
    }

    @Test
    fun findCapRef2() {
        assertEquals(c("foo", 6), find("\${foo}"))
    }

    @Test
    fun findCapRef3() {
        assertEquals(c(0, 2), find("\$0"))
    }

    @Test
    fun findCapRef4() {
        assertEquals(c(5, 2), find("\$5"))
    }

    @Test
    fun findCapRef5() {
        assertEquals(c(10, 3), find("\$10"))
    }

    @Test
    fun findCapRef6() {
        assertEquals(c("42a", 4), find("\$42a"))
    }

    @Test
    fun findCapRef7() {
        assertEquals(c(42, 5), find("\${42}a"))
    }

    @Test
    fun findCapRef8() {
        assertNull(find("\${42"))
    }

    @Test
    fun findCapRef9() {
        assertNull(find("\${42 "))
    }

    @Test
    fun findCapRef10() {
        assertNull(find(" \$0 "))
    }

    @Test
    fun findCapRef11() {
        assertNull(find("\$"))
    }

    @Test
    fun findCapRef12() {
        assertNull(find(" "))
    }

    @Test
    fun findCapRef13() {
        assertNull(find(""))
    }

    @Test
    fun findCapRef14() {
        assertEquals(c(1, 2), find("\$1-\$2"))
    }

    @Test
    fun findCapRef15() {
        assertEquals(c("1_", 3), find("\$1_\$2"))
    }

    @Test
    fun findCapRef16() {
        assertEquals(c("x", 2), find("\$x-\$y"))
    }

    @Test
    fun findCapRef17() {
        assertEquals(c("x_", 3), find("\$x_\$y"))
    }

    @Test
    fun findCapRef18() {
        assertEquals(c("#", 4), find("\${#}"))
    }

    @Test
    fun findCapRef19() {
        assertEquals(c("Z[", 5), find("\${Z[}"))
    }

    @Test
    fun findCapRef20() {
        assertEquals(c("¾", 5), find("\${¾}"))
    }

    @Test
    fun findCapRef21() {
        assertEquals(c("¾a", 6), find("\${¾a}"))
    }

    @Test
    fun findCapRef22() {
        assertEquals(c("a¾", 6), find("\${a¾}"))
    }

    @Test
    fun findCapRef23() {
        assertEquals(c("☃", 6), find("\${☃}"))
    }

    @Test
    fun findCapRef24() {
        assertEquals(c("a☃", 7), find("\${a☃}"))
    }

    @Test
    fun findCapRef25() {
        assertEquals(c("☃a", 7), find("\${☃a}"))
    }

    @Test
    fun findCapRef26() {
        assertEquals(c("名字", 9), find("\${名字}"))
    }

    private fun interpolateHelper(
        map: List<Pair<String, Int>>,
        caps: List<String>,
        hay: String,
    ): String {
        val sortedMap = map.sortedBy { it.first }
        val dst = StringBuilder()
        interpolateString(
            replacement = hay,
            append = { i, sb ->
                if (i in caps.indices) {
                    sb.append(caps[i])
                }
            },
            nameToIndex = { name ->
                val idx = sortedMap.binarySearchBy(name) { it.first }
                if (idx >= 0) sortedMap[idx].second else null
            },
            dst = dst,
        )
        return dst.toString()
    }

    @Test
    fun interp1() {
        assertEquals(
            "test xxx test",
            interpolateHelper(listOf("foo" to 2), listOf("", "", "xxx"), "test \$foo test"),
        )
    }

    @Test
    fun interp2() {
        assertEquals(
            "test",
            interpolateHelper(listOf("foo" to 2), listOf("", "", "xxx"), "test\$footest"),
        )
    }

    @Test
    fun interp3() {
        assertEquals(
            "testxxxtest",
            interpolateHelper(listOf("foo" to 2), listOf("", "", "xxx"), "test\${foo}test"),
        )
    }

    @Test
    fun interp4() {
        assertEquals(
            "test",
            interpolateHelper(listOf("foo" to 2), listOf("", "", "xxx"), "test\$2test"),
        )
    }

    @Test
    fun interp5() {
        assertEquals(
            "testxxxtest",
            interpolateHelper(listOf("foo" to 2), listOf("", "", "xxx"), "test\${2}test"),
        )
    }

    @Test
    fun interp6() {
        assertEquals(
            "test \$foo test",
            interpolateHelper(listOf("foo" to 2), listOf("", "", "xxx"), "test \$\$foo test"),
        )
    }

    @Test
    fun interp7() {
        assertEquals(
            "test xxx",
            interpolateHelper(listOf("foo" to 2), listOf("", "", "xxx"), "test \$foo"),
        )
    }

    @Test
    fun interp8() {
        assertEquals(
            "xxx test",
            interpolateHelper(listOf("foo" to 2), listOf("", "", "xxx"), "\$foo test"),
        )
    }

    @Test
    fun interp9() {
        assertEquals(
            "test yyyxxx",
            interpolateHelper(listOf("bar" to 1, "foo" to 2), listOf("", "yyy", "xxx"), "test \$bar\$foo"),
        )
    }

    @Test
    fun interp10() {
        assertEquals(
            "test \$ test",
            interpolateHelper(listOf("bar" to 1, "foo" to 2), listOf("", "yyy", "xxx"), "test \$ test"),
        )
    }

    @Test
    fun interp11() {
        assertEquals(
            "test  test",
            interpolateHelper(listOf("bar" to 1, "foo" to 2), listOf("", "yyy", "xxx"), "test \${} test"),
        )
    }

    @Test
    fun interp12() {
        assertEquals(
            "test  test",
            interpolateHelper(listOf("bar" to 1, "foo" to 2), listOf("", "yyy", "xxx"), "test \${ } test"),
        )
    }

    @Test
    fun interp13() {
        assertEquals(
            "test  test",
            interpolateHelper(listOf("bar" to 1, "foo" to 2), listOf("", "yyy", "xxx"), "test \${a b} test"),
        )
    }

    @Test
    fun interp14() {
        assertEquals(
            "test  test",
            interpolateHelper(listOf("bar" to 1, "foo" to 2), listOf("", "yyy", "xxx"), "test \${a} test"),
        )
    }

    @Test
    fun interp15() {
        assertEquals(
            "test \${wat yyy ok",
            interpolateHelper(listOf("bar" to 1, "foo" to 2), listOf("", "yyy", "xxx"), "test \${wat \$bar ok"),
        )
    }
}
