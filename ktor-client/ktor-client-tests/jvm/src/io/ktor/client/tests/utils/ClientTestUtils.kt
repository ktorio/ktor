package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.client.engine.*
import kotlinx.coroutines.*

fun <T : HttpClientEngineConfig> clientTest(
    factory: HttpClientEngineFactory<T>,
    block: suspend TestClientBuilder<T>.() -> Unit
): Unit = runBlocking {
    val builder = TestClientBuilder<T>().apply { block() }
    val client = HttpClient(factory, block = builder.config)

    client.use {
        builder.test(it)
    }

    client.coroutineContext[Job]!!.join()
}

fun clientTest(
    engine: HttpClientEngine,
    block: suspend TestClientBuilder<*>.() -> Unit
): Unit = clientTest(HttpClient(engine), block)

fun clientTest(
    client: HttpClient = HttpClient(),
    block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
): Unit = runBlocking {
    val builder = TestClientBuilder<HttpClientEngineConfig>().also { it.block() }

    @Suppress("UNCHECKED_CAST")
    client
        .config { builder.config(this as HttpClientConfig<HttpClientEngineConfig>) }
        .use { client -> builder.test(client) }
}

class TestClientBuilder<T : HttpClientEngineConfig>(
    var config: HttpClientConfig<T>.() -> Unit = {},
    var test: suspend (client: HttpClient) -> Unit = {}
)

fun <T : HttpClientEngineConfig> TestClientBuilder<T>.config(block: HttpClientConfig<T>.() -> Unit): Unit {
    config = block
}

fun TestClientBuilder<*>.test(block: suspend (client: HttpClient) -> Unit): Unit {
    test = block
}
