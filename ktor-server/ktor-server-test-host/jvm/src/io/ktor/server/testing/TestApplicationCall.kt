/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.application.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Represents a test application call that is used in [withTestApplication] and [handleRequest]
 */
class TestApplicationCall(
    application: Application, readResponse: Boolean = false,
    closeRequest: Boolean = true,
    override val coroutineContext: CoroutineContext
) : BaseApplicationCall(application), CoroutineScope {

    /**
     * Set to `true` when the request has been handled and a response has been produced
     */
    @Volatile
    var requestHandled: Boolean = false
        internal set

    override val request: TestApplicationRequest = TestApplicationRequest(this, closeRequest)
    override val response: TestApplicationResponse = TestApplicationResponse(this, readResponse)

    override fun toString(): String = "TestApplicationCall(uri=${request.uri}) : handled = $requestHandled"

    init {
        putResponseAttribute()
    }
}
