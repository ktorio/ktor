package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.compat.*
import kotlinx.coroutines.experimental.*

fun <T : HttpClientEngineConfig> clientTest(
    factory: HttpClientEngineFactory<T>,
    block: suspend TestClientBuilder.() -> Unit
): Unit = clientTest(HttpClient(factory), block)

fun clientTest(
    engine: HttpClientEngine,
    block: suspend TestClientBuilder.() -> Unit
): Unit = clientTest(HttpClient(engine), block)

fun clientTest(
    client: HttpClient,
    block: suspend TestClientBuilder.() -> Unit
): Unit = runBlocking {
    val builder = TestClientBuilder().also { it.block() }
    client.config { builder.config(this) }.use { client -> builder.test(client) }
}

class TestClientBuilder(
    var config: HttpClientConfig.() -> Unit = {},
    var test: suspend (client: HttpClient) -> Unit = {}
)

fun TestClientBuilder.config(block: HttpClientConfig.() -> Unit): Unit {
    config = block
}

fun TestClientBuilder.test(block: suspend (client: HttpClient) -> Unit): Unit {
    test = block
}
