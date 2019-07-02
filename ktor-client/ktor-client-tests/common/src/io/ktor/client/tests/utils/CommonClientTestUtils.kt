/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.tests.utils.dispatcher.*
import io.ktor.util.*
import kotlinx.coroutines.*
import io.ktor.utils.io.core.*

/**
 * Local test server url.
 */
const val TEST_SERVER: String = "http://127.0.0.1:8080"
const val HTTP_PROXY_SERVER: String = "http://127.0.0.1:8082"

/**
 * Perform test with selected client [engine].
 */
fun clientTest(
    engine: HttpClientEngine,
    block: suspend TestClientBuilder<*>.() -> Unit
) = clientTest(HttpClient(engine), block)

/**
 * Perform test with selected [client] or client loaded by service loader.
 */
fun clientTest(
    client: HttpClient = HttpClient(),
    block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
) = testSuspend {
    val builder = TestClientBuilder<HttpClientEngineConfig>().also { it.block() }

    @Suppress("UNCHECKED_CAST")
    client.config { builder.config(this as HttpClientConfig<HttpClientEngineConfig>) }
        .use { client -> builder.test(client) }
}

/**
 * Perform test with selected client engine [factory].
 */
fun <T : HttpClientEngineConfig> clientTest(
    factory: HttpClientEngineFactory<T>,
    block: suspend TestClientBuilder<T>.() -> Unit
) = testSuspend {
    val builder = TestClientBuilder<T>().apply { block() }
    val client = HttpClient(factory, block = builder.config)

    client.use {
        builder.test(it)
    }

    try {
        client.coroutineContext[Job]?.join()
    } catch (cause: Throwable) {
        client.cancel()
        throw cause
    }
}

@InternalAPI
@Suppress("KDocMissingDocumentation")
class TestClientBuilder<T : HttpClientEngineConfig>(
    var config: HttpClientConfig<T>.() -> Unit = {},
    var test: suspend (client: HttpClient) -> Unit = {}
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
