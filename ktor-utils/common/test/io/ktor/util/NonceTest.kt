/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlin.test.*

class NonceTest {

    @Test
    fun testGenerateNonce() {
        val nonce1 = generateNonce()
        val nonce2 = generateNonce()
        assertNotEquals(nonce1, nonce2)
    }
}
