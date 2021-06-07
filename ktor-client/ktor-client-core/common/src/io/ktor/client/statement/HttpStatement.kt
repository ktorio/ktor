/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.statement

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
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
    private val client: HttpClient
) {
    init {
        checkCapabilities()
    }

    /**
     * Executes this statement and call the [block] with the streaming [response].
     *
     * The [response] argument holds a network connection until the [block] isn't completed. You can read the body
     * on-demand or at once with [receive<T>()] method.
     *
     * After [block] finishes, [response] will be completed body will be discarded or released depends on the engine configuration.
     *
     * Please note: the [response] instance will be canceled and shouldn't be passed outside of [block].
     */
    public suspend fun <T> execute(block: suspend (response: HttpResponse) -> T): T {
        val response: HttpResponse = executeUnsafe()

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
     * To receive exact type you consider using [receive<T>()] method.
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
    @OptIn(ExperimentalStdlibApi::class)
    public suspend inline fun <reified T> receive(): T = when (T::class) {
        HttpStatement::class -> this as T
        HttpResponse::class -> execute() as T
        else -> {
            val response = executeUnsafe()
            try {
                response.receive<T>()
            } finally {
                response.complete()
            }
        }
    }

    /**
     * Executes this statement and run the [block] with a [HttpClient.responsePipeline] execution result.
     *
     * Note that T can be a streamed type such as [ByteReadChannel].
     */
    public suspend inline fun <reified T, R> receive(crossinline block: suspend (response: T) -> R): R {
        val response: HttpResponse = executeUnsafe()
        try {
            val result = response.receive<T>()
            return block(result)
        } finally {
            response.cleanup()
        }
    }

    /**
     * Return [HttpResponse] with open streaming body.
     */
    @PublishedApi
    internal suspend fun executeUnsafe(): HttpResponse {
        val builder = HttpRequestBuilder().takeFromWithExecutionContext(builder)
        @Suppress("DEPRECATION_ERROR")
        val call = client.execute(builder)
        return call.response
    }

    /**
     * Complete [HttpResponse] and release resources.
     */
    @PublishedApi
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
     * Check that all request configuration related to client capabilities have correspondent features installed.
     */
    private fun checkCapabilities() {
        builder.attributes.getOrNull(ENGINE_CAPABILITIES_KEY)?.keys
            ?.filterIsInstance<HttpClientFeature<*, *>>()
            ?.forEach {
                requireNotNull(client.feature(it)) {
                    "Consider installing $it feature because the request requires it to be installed"
                }
            }
    }

    override fun toString(): String = "HttpStatement[${builder.url.buildString()}]"
}

@Deprecated(
    "[HttpStatement] isn't closeable.",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("this.execute<T>(block)")
)
@Suppress("unused", "KDocMissingDocumentation", "UNUSED_PARAMETER")
public fun <T> HttpStatement.use(block: suspend (response: HttpResponse) -> T) {
}

@Deprecated(
    "Unbound [HttpResponse] is deprecated. Consider using [execute()] instead.",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("this.execute()")
)
@Suppress("KDocMissingDocumentation", "unused")
public val HttpStatement.response: HttpResponse
    get() = error("Unbound [HttpClientCall] is deprecated. Consider using [HttpResponse] instead.")

/**
 * Read the [HttpResponse.content] as a String. You can pass an optional [charset]
 * to specify a charset in the case no one is specified as part of the Content-Type response.
 * If no charset specified either as parameter or as part of the response,
 * [io.ktor.client.features.HttpPlainText] settings will be used.
 *
 * Note that [fallbackCharset] parameter will be ignored if the response already has a charset.
 *      So it just acts as a fallback, honoring the server preference.
 */
public suspend fun HttpResponse.readText(fallbackCharset: Charset? = null): String {
    val originCharset = charset() ?: fallbackCharset ?: Charsets.UTF_8
    val decoder = originCharset.newDecoder()
    val input = receive<Input>()

    return decoder.decode(input)
}
