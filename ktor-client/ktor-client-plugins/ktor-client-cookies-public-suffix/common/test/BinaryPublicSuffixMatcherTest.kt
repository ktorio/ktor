/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies.publicsuffix

import kotlin.test.*

class BinaryPublicSuffixMatcherTest {

    @Test
    fun testExactWildcardExceptionAndPrevailingRules() {
        val matcher = BinaryPublicSuffixMatcher.from(
            encodeResource(
                rules = listOf("*.ck", "co.uk", "com", "github.io"),
                exceptions = listOf("www.ck")
            )
        )

        assertTrue(matcher.isPublicSuffix("com"))
        assertTrue(matcher.isPublicSuffix("co.uk"))
        assertTrue(matcher.isPublicSuffix("github.io"))
        assertTrue(matcher.isPublicSuffix("foo.ck"))
        assertFalse(matcher.isPublicSuffix("www.ck"))
        assertFalse(matcher.isPublicSuffix("example.com"))
        assertFalse(matcher.isPublicSuffix("bar.foo.ck"))
        assertTrue(matcher.isPublicSuffix("unknown"))
    }

    @Test
    fun testRejectsTruncatedResource() {
        assertFailsWith<IllegalStateException> { BinaryPublicSuffixMatcher.from(byteArrayOf(0, 0, 0, 1)) }
        assertFailsWith<IllegalStateException> {
            BinaryPublicSuffixMatcher.from(byteArrayOf(0, 0, 0, 4, 'c'.code.toByte()))
        }
    }

    @Test
    fun testRejectsUnsortedAndMalformedBlocks() {
        assertFailsWith<IllegalStateException> {
            BinaryPublicSuffixMatcher.from(encodeResource(listOf("com", "ck"), emptyList(), sort = false))
        }
        assertFailsWith<IllegalStateException> {
            BinaryPublicSuffixMatcher.from(encodeResource(listOf("*.com", "bad_rule"), emptyList()))
        }
    }

    private fun encodeResource(
        rules: List<String>,
        exceptions: List<String>,
        sort: Boolean = true
    ): ByteArray {
        val ruleBytes = encodeBlock(if (sort) rules.sorted() else rules)
        val exceptionBytes = encodeBlock(if (sort) exceptions.sorted() else exceptions)
        return encodeInt(ruleBytes.size) + ruleBytes + encodeInt(exceptionBytes.size) + exceptionBytes
    }

    private fun encodeBlock(rules: List<String>): ByteArray {
        if (rules.isEmpty()) return ByteArray(0)
        return rules.joinToString(separator = "\n", postfix = "\n").encodeToByteArray()
    }

    private fun encodeInt(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte()
    )
}
