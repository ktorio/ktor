/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.test.base

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.test.*
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Web url for tests.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.test.base.TEST_SERVER)
 */
const val TEST_SERVER: String = "http://127.0.0.1:8080"

/**
 * Web url with TLS for tests.
 */
const val TEST_SERVER_TLS: String = "https://127.0.0.1:8089"

/**
 * Websocket server url for tests.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.test.base.TEST_WEBSOCKET_SERVER)
 */
const val TEST_WEBSOCKET_SERVER: String = "ws://127.0.0.1:8080"

/**
 * Proxy server url for tests.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.test.base.TCP_SERVER)
 */
const val TCP_SERVER: String = "http://127.0.0.1:8082"

/**
 * Perform test with selected client [engine].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.test.base.testWithEngine)
 */
fun testWithEngine(
    engine: HttpClientEngine,
    timeout: Duration = 1.minutes,
    retries: Int = DEFAULT_RETRIES,
    block: suspend TestClientBuilder<*>.() -> Unit
) = testWithClient(HttpClient(engine), timeout, retries, block)

/**
 * Perform test with selected [client].
 */
private fun testWithClient(
    client: HttpClient,
    timeout: Duration,
    retries: Int,
    block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
) = runTestWithData(listOf(client), timeout = timeout, retries = retries) {
    val builder = TestClientBuilder<HttpClientEngineConfig>().also { it.block() }

    concurrency(builder.concurrency) { threadId ->
        repeat(builder.repeatCount) { attempt ->
            val coroutineScope = this
            withContext(TestInfo(threadId, attempt)) {
                @Suppress("UNCHECKED_CAST")
                client.config { builder.config(this as HttpClientConfig<HttpClientEngineConfig>) }
                    .use { client -> builder.test(coroutineScope, client) }
            }
        }
    }

    client.engine.close()
}

/**
 * Perform test with selected client engine [factory].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.test.base.testWithEngine)
 */
fun <T : HttpClientEngineConfig> testWithEngine(
    factory: HttpClientEngineFactory<T>,
    loader: ClientLoader? = null,
    timeout: Duration = 1.minutes,
    retries: Int = DEFAULT_RETRIES,
    block: suspend TestClientBuilder<T>.() -> Unit
) = runTestWithData(listOf(factory), timeout = timeout, retries = retries) {
    performTestWithEngine(factory, loader, block)
}

@OptIn(DelicateCoroutinesApi::class)
suspend fun <T : HttpClientEngineConfig> performTestWithEngine(
    factory: HttpClientEngineFactory<T>,
    loader: ClientLoader? = null,
    block: suspend TestClientBuilder<T>.() -> Unit
) {
    val builder = TestClientBuilder<T>().apply { block() }

    if (builder.dumpAfterDelay > 0 && loader != null) {
        GlobalScope.launch {
            delay(builder.dumpAfterDelay)
            loader.dumpCoroutines()
        }
    }

    withContext(Dispatchers.Default.limitedParallelism(1)) {
        concurrency(builder.concurrency) { threadId ->
            repeat(builder.repeatCount) { attempt ->
                val client = HttpClient(factory, block = builder.config)

                withContext(TestInfo(threadId, attempt)) {
                    val coroutineScope = this
                    client.use { builder.test(coroutineScope, it) }
                }

                try {
                    val job = client.coroutineContext[Job]!!
                    while (job.isActive) {
                        yield()
                    }
                } catch (cause: Throwable) {
                    client.cancel("Test failed", cause)
                    throw cause
                } finally {
                    builder.after(client)
                }
            }
        }
    }
}

private suspend fun concurrency(level: Int, block: suspend (Int) -> Unit) {
    coroutineScope {
        List(level) {
            async {
                block(it)
            }
        }.awaitAll()
    }
}

class TestClientBuilder<out T : HttpClientEngineConfig>(
    var config: HttpClientConfig<@UnsafeVariance T>.() -> Unit = {},
    var test: suspend CoroutineScope.(client: HttpClient) -> Unit = {},
    var after: suspend (client: HttpClient) -> Unit = {},
    var repeatCount: Int = 1,
    var dumpAfterDelay: Long = -1,
    var concurrency: Int = 1
)

fun <T : HttpClientEngineConfig> TestClientBuilder<T>.config(block: HttpClientConfig<T>.() -> Unit) {
    config = block
}

fun TestClientBuilder<*>.test(block: suspend CoroutineScope.(client: HttpClient) -> Unit) {
    test = block
}

fun TestClientBuilder<*>.after(block: suspend (client: HttpClient) -> Unit) {
    after = block
}
