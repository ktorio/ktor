/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.test.dispatcher.*
import io.ktor.util.*
import kotlinx.coroutines.*
import io.ktor.utils.io.core.*

/**
 * Local test server url.
 */
const val TEST_SERVER: String = "http://127.0.0.1:8080"
const val TEST_WEBSOCKET_SERVER: String = "ws://127.0.0.1:8080"
const val HTTP_PROXY_SERVER: String = "http://127.0.0.1:8082"

/**
 * Perform test with selected client [engine].
 */
fun testWithEngine(
    engine: HttpClientEngine,
    block: suspend TestClientBuilder<*>.() -> Unit
) = testWithClient(HttpClient(engine), block)

/**
 * Perform test with selected [client].
 */
private fun testWithClient(
    client: HttpClient,
    block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
) = testSuspend {
    val builder = TestClientBuilder<HttpClientEngineConfig>().also { it.block() }

    repeat(builder.repeatCount) {
        @Suppress("UNCHECKED_CAST")
        client.config { builder.config(this as HttpClientConfig<HttpClientEngineConfig>) }
            .use { client -> builder.test(client) }
    }

    client.engine.close()
}

/**
 * Perform test with selected client engine [factory].
 */
fun <T : HttpClientEngineConfig> testWithEngine(
    factory: HttpClientEngineFactory<T>,
    block: suspend TestClientBuilder<T>.() -> Unit
) = testSuspend {
    val builder = TestClientBuilder<T>().apply { block() }
    repeat(builder.repeatCount) {
        val client = HttpClient(factory, block = builder.config)

        client.use {
            builder.test(it)
        }

        try {
            val job = client.coroutineContext[Job]!!
            job.join()
        } catch (cause: Throwable) {
            client.cancel("Test failed", cause)
            throw cause
        }
    }
}

@InternalAPI
@Suppress("KDocMissingDocumentation")
class TestClientBuilder<T : HttpClientEngineConfig>(
    var config: HttpClientConfig<T>.() -> Unit = {},
    var test: suspend (client: HttpClient) -> Unit = {},
    var repeatCount: Int = 1
)

@InternalAPI
@Suppress("KDocMissingDocumentation")
fun <T : HttpClientEngineConfig> TestClientBuilder<T>.config(block: HttpClientConfig<T>.() -> Unit): Unit {
    config = block
}

@InternalAPI
@Suppress("KDocMissingDocumentation")
fun TestClientBuilder<*>.test(block: suspend (client: HttpClient) -> Unit): Unit {
    test = block
}
