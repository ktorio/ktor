/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal data class RequestTask(
    val request: HttpRequestData,
    val response: CancellableContinuation<HttpResponseData>,
    val context: CoroutineContext
)

internal fun RequestTask.requiresDedicatedConnection(): Boolean = listOf(request.headers, request.body.headers).any {
    it[HttpHeaders.Connection] == "close" || it.contains(HttpHeaders.Upgrade)
} || request.method !in listOf(HttpMethod.Get, HttpMethod.Head) || containsCustomTimeouts()

internal data class ConnectionResponseTask(
    val requestTime: GMTDate,
    val task: RequestTask
)

/**
 * Return true if request task contains timeout attributes specified using [HttpTimeout] feature.
 */
private fun RequestTask.containsCustomTimeouts() =
    request.getCapabilityOrNull(HttpTimeout)?.let {
        it.connectTimeoutMillis != null || it.socketTimeoutMillis != null
    } == true
