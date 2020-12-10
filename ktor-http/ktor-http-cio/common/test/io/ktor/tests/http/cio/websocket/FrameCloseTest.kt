/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http.cio.websocket

import kotlin.test.*
import io.ktor.http.cio.websocket.*

class FrameCloseTest {
//    @Test
//    fun testASCII() {
//        testClose(1000, "websocket closed")
//    }

//    @Test
//    fun testUnicode() {
//        testClose(1000, "websocket закрыт")
//    }
//
//    @Test
//    fun testEmptyMessage() {
//        testClose(1000, "")
//    }
//
//    @Test
//    fun testEmptyFrame() {
//        val reason = Frame.Close(byteArrayOf()).readReason()
//        assertNull(reason)
//    }

    private fun testClose(code: Short, message: String) {
        val reason = Frame.Close(CloseReason(code, message)).readReason()
        assertNotNull(reason)
        assertEquals(code, reason.code)
        assertEquals(message, reason.message)
    }
}
