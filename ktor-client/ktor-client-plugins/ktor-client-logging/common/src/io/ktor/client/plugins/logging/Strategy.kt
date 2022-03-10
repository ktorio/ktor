/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*

public interface Strategy {
    /**
     * Log request during [HttpSendPipeline.Monitoring]
     */
    public suspend fun log(request: HttpRequestBuilder, logWriter: LogWriter): OutgoingContent?

    /**
     * Log response during [HttpReceivePipeline.State]
     */
    public suspend fun log(response: HttpResponse, logWriter: LogWriter)

    /**
     * Log body of response
     */
    public suspend fun logResponseBody(response: HttpResponse, logWriter: LogWriter)

    public companion object
}

public val Strategy.Companion.DEFAULT: Strategy get() = SimpleStrategy()

public class StatusAtLeastStrategy(private val status: HttpStatusCode) : Strategy {
    override suspend fun log(request: HttpRequestBuilder, logWriter: LogWriter): OutgoingContent? = null

    override suspend fun log(response: HttpResponse, logWriter: LogWriter) {
        if (response.call.response.status.value >= status.value) {
            logWriter.write(response.call.request)
            logWriter.write(response)
        }
    }

    override suspend fun logResponseBody(response: HttpResponse, logWriter: LogWriter) {
        if (response.call.response.status.value >= status.value) {
            logWriter.logResponseBody(response)
        }
    }
}

private class SimpleStrategy : Strategy {
    override suspend fun log(request: HttpRequestBuilder, logWriter: LogWriter): OutgoingContent? =
        logWriter.write(request)

    override suspend fun log(response: HttpResponse, logWriter: LogWriter): Unit =
        logWriter.write(response)

    override suspend fun logResponseBody(response: HttpResponse, logWriter: LogWriter): Unit =
        logWriter.logResponseBody(response)
}
