/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.server.application.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@Deprecated(message = "Renamed to TestServerCall", replaceWith = ReplaceWith("TestServerCall"))
public typealias TestApplicationCall = TestServerCall

/**
 * A test application call that is used in [withTestApplication] and [handleRequest].
 */
public class TestServerCall(
    server: Server,
    readResponse: Boolean = false,
    closeRequest: Boolean = true,
    override val coroutineContext: CoroutineContext
) : BaseServerCall(server), CoroutineScope {

    override val request: TestServerRequest = TestServerRequest(this, closeRequest)
    override val response: TestServerResponse = TestServerResponse(this, readResponse)

    override fun toString(): String = "TestApplicationCall(uri=${request.uri})"

    init {
        putResponseAttribute()
    }
}
