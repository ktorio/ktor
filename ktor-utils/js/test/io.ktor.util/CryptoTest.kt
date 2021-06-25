/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlin.test.*

class CryptoTest {

    @Test
    fun expectCryptoToBeDefinedInIE11() {
        if (!PlatformUtils.IS_BROWSER) return

        js("window.msCrypto = window.crypto")
        js("Object.defineProperty(window, 'crypto', {writable: true})")
        js("delete window.crypto")
        assertEquals(16, generateNonce().length)
    }
}
