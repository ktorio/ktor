/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp.internal

import kotlin.test.*

class WinHttpExceptionsTest {

    @Test
    fun formatWinHttpErrorCode() {
        val message = getErrorMessage(12002u)
        assertEquals("The operation timed out", message)
    }

    @Test
    fun formatSystemErrorCode() {
        val message = getErrorMessage(0x6u)
        assertEquals("The handle is invalid.", message)
    }
}
