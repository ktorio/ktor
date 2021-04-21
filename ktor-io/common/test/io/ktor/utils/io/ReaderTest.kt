/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.test.dispatcher.*
import kotlinx.coroutines.*
import kotlin.test.*

class ReaderTest {

    @Test
    fun testCancelExceptionLoggedOnce() = testSuspend {
        var failInHandler = false
        val handler = CoroutineExceptionHandler { coroutineContext, throwable ->
            failInHandler = true
        }

        val reader = GlobalScope.reader(Dispatchers.Unconfined + handler) {
            error("Expected")
        }.channel

        var failed = false
        try {
            reader.writeByte(42)
        } catch (cause: IllegalStateException) {
            failed = true
            assertEquals("Expected", cause.message)
        }

        assertTrue(failed, "Channel should fail with the exception")
        assertFalse(failInHandler, "Exception should be thrown only from channel")
    }
}
