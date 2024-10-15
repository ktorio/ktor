/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http.header

import io.ktor.http.isDelimiter
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpHeadersTest {
    @Test
    fun testIsDelimiter() {
        // Test delimiter characters
        val delimiters = "\"(),/:;<=>?@[\\]{}"
        for (ch in delimiters) {
            assertTrue(isDelimiter(ch), "Character $ch should be considered a delimiter")
        }

        // Test non-delimiter characters (letters, numbers, etc.)
        val nonDelimiters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 -_"
        for (ch in nonDelimiters) {
            assertFalse(isDelimiter(ch), "Character $ch should not be considered a delimiter")
        }

        // Ensure 'o' is not considered a delimiter
        assertFalse(isDelimiter('o'), "Character 'o' should not be considered a delimiter")
    }

}
