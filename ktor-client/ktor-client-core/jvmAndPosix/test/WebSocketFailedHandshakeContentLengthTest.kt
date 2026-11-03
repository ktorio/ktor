/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * This is a continuation of [WebSocketFailedHandshakeTest] that relies on [io.ktor.client.call.checkContentLength]
 * throwing, that's why this test lives in jvmAndPosix.
 */
class WebSocketFailedHandshakeContentLengthTest {
    @Test
    fun `does not expose truncated body of failed handshake`() = runTest {
        val exception = clientRespondingWith("forb", contentLength = 9).failedHandshake()

        assertNull(exception.response)
        val saveFailure = assertIs<IllegalStateException>(exception.suppressedExceptions.single())
        assertContains(saveFailure.message.orEmpty(), "Content-Length mismatch")
    }
}
