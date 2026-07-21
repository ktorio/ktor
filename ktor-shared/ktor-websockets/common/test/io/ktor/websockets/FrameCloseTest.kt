/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websockets

import io.ktor.websocket.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FrameCloseTest {
    @Test
    fun testASCII() {
        testClose(1000, "websocket closed")
    }

    @Test
    fun testUnicode() {
        testClose(1000, "websocket закрыт")
    }

    @Test
    fun testEmptyMessage() {
        testClose(1000, "")
    }

    @Test
    fun testEmptyFrame() {
        val reason = Frame.Close(byteArrayOf()).readReason()
        assertNull(reason)
    }

    private fun testClose(code: Short, message: String) {
        val reason = Frame.Close(CloseReason(code, message)).readReason()
        assertNotNull(reason)
        assertEquals(code, reason.code)
        assertEquals(message, reason.message)
    }
}
