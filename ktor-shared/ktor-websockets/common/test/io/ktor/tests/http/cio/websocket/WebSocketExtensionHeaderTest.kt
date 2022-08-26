/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http.cio.websocket

import io.ktor.websocket.*
import kotlin.test.*

class WebSocketExtensionHeaderTest {

    @Test
    fun testParseExtensions() {
        val result = parseWebSocketExtensions("superspeed, colormode; depth=16")

        assertEquals(2, result.size)
        assertEquals("superspeed", result[0].name)
        assertEquals("colormode", result[1].name)

        assertEquals(0, result[0].parameters.size)
        assertEquals(1, result[1].parameters.size)
        assertEquals("depth=16", result[1].parameters[0])
    }
}
