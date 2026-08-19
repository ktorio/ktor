/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NonceTest {

    @Test
    fun `generates distinct nonces`() = runTest {
        assertNotEquals(generateNonceBlocking(), generateNonceBlocking())
        assertNotEquals(generateNonceSuspend(), generateNonceSuspend())
    }

    @Test
    fun `returns nonce of requested length`() = runTest {
        assertEquals(NONCE_SIZE_IN_CHARS, generateNonceBlocking().length)
        assertEquals(NONCE_SIZE_IN_CHARS, generateNonceSuspend().length)

        val biggerLength = 64
        assertEquals(biggerLength, generateNonceBlocking(biggerLength).length)
        assertEquals(biggerLength, generateNonceSuspend(biggerLength).length)

        val smallerLength = 13
        assertEquals(smallerLength, generateNonceBlocking(smallerLength).length)
        assertEquals(smallerLength, generateNonceSuspend(smallerLength).length)
    }

    @Test
    fun `contains only hex characters`() = runTest {
        assertTrue(generateNonceBlocking().all { it.isHexDigit() })
        assertTrue(generateNonceSuspend().all { it.isHexDigit() })
        assertTrue(generateNonceBlocking(64).all { it.isHexDigit() })
    }
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f'
