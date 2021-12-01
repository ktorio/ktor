/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.statement

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*

/**
 * Prepared statement for http client request.
 * This statement doesn't perform any network requests until [execute] method call.
 *
 * [HttpStatement] is safe to execute multiple times.
 */
public class HttpStatement(
    private val builder: HttpRequestBuilder,
    @PublishedApi
    internal val client: HttpClient
) {
    init {
        checkCapabilities()
    }

    /**
     * Executes this statement and call the [block] with the streaming [response].
     *
     * The [response] argument holds a network connection until the [block] isn't completed. You can read the body
     * on-demand or at once with [body<T>()] method.
     *
     * After [block] finishes, [response] will be completed body will be discarded or released depends on the engine configuration.
     *
     * Please note: the [response] instance will be canceled and shouldn't be passed outside of [block].
     */
    public suspend fun <T> execute(block: suspend (response: HttpResponse) -> T): T = unwrapRequestTimeoutException {
        val response = executeUnsafe()

        try {
            return block(response)
        } finally {
            response.cleanup()
        }
    }

    /**
     * Executes this statement and download the response.
     * After the method finishes, the client downloads the response body in memory and release the connection.
     *
     * To receive exact type consider using [body<T>()] method.
     */
    public suspend fun execute(): HttpResponse = execute {
        val savedCall = it.call.save()

        savedCall.response
    }

    /**
     * Executes this statement and run [HttpClient.responsePipeline] with the response and expected type [T].
     *
     * Note if T is a streaming type, you should manage how to close it manually.
     */
    @OptIn(ExperimentalStdlibApi::class, InternalAPI::class)
    public suspend inline fun <reified T> body(): T = unwrapRequestTimeoutException {
        val response = executeUnsafe()
        return try {
            response.body()
        } finally {
            response.complete()
        }
    }

    /**
     * Executes this statement and run the [block] with a [HttpClient.responsePipeline] execution result.
     *
     * Note that T can be a streamed type such as [ByteReadChannel].
     */
    public suspend inline fun <reified T, R> body(
        crossinline block: suspend (response: T) -> R
    ): R = unwrapRequestTimeoutException {
        val response: HttpResponse = executeUnsafe()
        try {
            val result = response.body<T>()
            return block(result)
        } finally {
            response.cleanup()
        }
    }

    /**
     * Return [HttpResponse] with open streaming body.
     */
    @PublishedApi
    @OptIn(InternalAPI::class)
    internal suspend fun executeUnsafe(): HttpResponse = unwrapRequestTimeoutException {
        val builder = HttpRequestBuilder().takeFromWithExecutionContext(builder)

        val call = client.execute(builder)
        return call.response
    }

    /**
     * Complete [HttpResponse] and release resources.
     */
    @PublishedApi
    @OptIn(InternalAPI::class)
    internal suspend fun HttpResponse.cleanup() {
        val job = coroutineContext[Job]!! as CompletableJob

        job.apply {
            complete()
            try {
                content.cancel()
            } catch (_: Throwable) {
            }
            join()
        }
    }

    /**
     * Check that all request configuration related to client capabilities have correspondent plugin installed.
     */
    private fun checkCapabilities() {
        builder.attributes.getOrNull(ENGINE_CAPABILITIES_KEY)?.keys
            ?.filterIsInstance<HttpClientPlugin<*, *>>()
            ?.forEach {
                requireNotNull(client.pluginOrNull(it)) {
                    "Consider installing $it plugin because the request requires it to be installed"
                }
            }
    }

    override fun toString(): String = "HttpStatement[${builder.url.buildString()}]"
}
