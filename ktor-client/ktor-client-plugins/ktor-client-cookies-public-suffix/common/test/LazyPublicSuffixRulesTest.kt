/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies.publicsuffix

import kotlin.test.*

class LazyPublicSuffixRulesTest {

    @Test
    fun testLoadsOnFirstUseOnly() {
        var loads = 0
        val rules = LazyPublicSuffixRules {
            loads++
            resource(rules = "com\n")
        }
        assertEquals(0, loads)

        assertTrue(rules.isPublicSuffix("com"))
        assertFalse(rules.isPublicSuffix("example.com"))
        assertEquals(1, loads)
    }

    @Test
    fun testFailedLoadIsRetriedOnNextUse() {
        var loads = 0
        val rules = LazyPublicSuffixRules {
            loads++
            if (loads == 1) error("Unavailable")
            resource(rules = "com\n")
        }

        assertFailsWith<IllegalStateException> { rules.isPublicSuffix("com") }
        assertTrue(rules.isPublicSuffix("com"))
        assertEquals(2, loads)
    }

    private fun resource(rules: String): ByteArray {
        val ruleBytes = rules.encodeToByteArray()
        return byteArrayOf(0, 0, 0, ruleBytes.size.toByte()) + ruleBytes + byteArrayOf(0, 0, 0, 0)
    }
}
