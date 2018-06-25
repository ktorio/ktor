package io.ktor.client.engine.cio

import io.ktor.http.*
import kotlinx.coroutines.experimental.*
import java.util.*

internal data class RequestTask(
    val request: CIOHttpRequest,
    val continuation: CancellableContinuation<CIOHttpResponse>
)

internal fun RequestTask.requiresDedicatedConnection(): Boolean = listOf(request.headers, request.content.headers).any {
    it[HttpHeaders.Connection] == "close" || it.contains(HttpHeaders.Upgrade)
} || request.method !in listOf(HttpMethod.Get, HttpMethod.Head)


internal class ConnectionResponseTask(
    val requestTime: Date,
    val continuation: CancellableContinuation<CIOHttpResponse>,
    val request: CIOHttpRequest
)