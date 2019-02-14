package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.tests.utils.dispatcher.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.io.core.*

internal const val TEST_SERVER: String = "http://0.0.0.0:8080"

/**
 * Perform test against all clients from dependencies.
 */
expect fun clientsTest(
    skipMissingPlatforms: Boolean = false,
    block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
)

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
    client
        .config { builder.config(this as HttpClientConfig<HttpClientEngineConfig>) }
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

    client.coroutineContext[Job]!!.join()
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
