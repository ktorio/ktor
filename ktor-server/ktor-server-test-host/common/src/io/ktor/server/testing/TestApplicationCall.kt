/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.server.engine.*
import io.ktor.utils.io.concurrent.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Represents a test application call that is used in [withTestApplication] and [handleRequest]
 */
public class TestApplicationCall(
    application: Application,
    readResponse: Boolean = false,
    closeRequest: Boolean = true,
    override val coroutineContext: CoroutineContext
) : BaseApplicationCall(application), CoroutineScope {

    /**
     * Set to `true` when the request has been handled and a response has been produced
     */
    private var _requestHandled: Boolean by atomic(false)
    var requestHandled: Boolean by ::_requestHandled
        internal set

    override val request: TestApplicationRequest = TestApplicationRequest(this, closeRequest)
    override val response: TestApplicationResponse = TestApplicationResponse(this, readResponse)

    init {
        //to overcome freeze on native
        response.pipeline.intercept(ApplicationSendPipeline.Engine) {
            requestHandled = response.status() != HttpStatusCode.NotFound
        }
    }

    init {
        putResponseAttribute()
    }

    override fun toString(): String = "TestApplicationCall(uri=${request.uri}) : handled = $requestHandled"
}
