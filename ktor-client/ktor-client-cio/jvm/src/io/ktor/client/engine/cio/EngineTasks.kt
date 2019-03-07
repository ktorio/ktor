package io.ktor.client.engine.cio

import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal data class RequestTask(
    val request: HttpRequest,
    val response: CompletableDeferred<HttpResponse>,
    val context: CoroutineContext
)

internal fun RequestTask.requiresDedicatedConnection(): Boolean = listOf(request.headers, request.content.headers).any {
    it[HttpHeaders.Connection] == "close" || it.contains(HttpHeaders.Upgrade)
} || request.method !in listOf(HttpMethod.Get, HttpMethod.Head)


internal data class ConnectionResponseTask(
    val requestTime: GMTDate,
    val task: RequestTask
)
