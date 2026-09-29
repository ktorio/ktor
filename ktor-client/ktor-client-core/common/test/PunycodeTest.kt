/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.cookies.*
import kotlin.test.*

class PunycodeTest {

    @Test
    fun testEncodesAndDecodesLabels() {
        val cases = listOf(
            "münchen.de" to "xn--mnchen-3ya.de",
            "公司.cn" to "xn--55qx5d.cn",
            "\uD83D\uDE00" to "xn--e28h",
            "a\uD83D\uDE00b" to "xn--ab-no82a",
            "example.com" to "example.com"
        )

        for ((unicode, ascii) in cases) {
            assertEquals(ascii, Punycode.encode(unicode), unicode)
            assertEquals(unicode, Punycode.decode(ascii), ascii)
        }
    }

    @Test
    fun testRejectsUnpairedSurrogates() {
        assertNull(Punycode.encode("a\uD83Db"))
        assertNull(Punycode.encode("a\uDE00b"))
    }
}
