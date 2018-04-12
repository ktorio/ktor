package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.client.engine.*
import kotlinx.coroutines.experimental.*

fun <T : HttpClientEngineConfig> clientTest(
    factory: HttpClientEngineFactory<T>,
    block: suspend TestClientBuilder.() -> Unit
): Unit = runBlocking {
    val builder = TestClientBuilder().also { it.block() }

    HttpClient(factory).config {
        builder.config(this)
    }.use { client -> builder.test(client) }
}

class TestClientBuilder(
    var config: suspend HttpClientConfig.() -> Unit = {},
    var test: suspend (HttpClient) -> Unit = {}
)

fun TestClientBuilder.config(block: suspend HttpClientConfig.() -> Unit): Unit {
    config = block
}

fun TestClientBuilder.test(block: suspend (HttpClient) -> Unit): Unit {
    test = block
}
