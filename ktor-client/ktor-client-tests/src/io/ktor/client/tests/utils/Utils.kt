package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.client.engine.*
import kotlinx.coroutines.experimental.*

fun withClient(factory: HttpClientEngineFactory<*>, block: suspend (HttpClient) -> Unit) = runBlocking {
    HttpClient(factory).use { block(it) }
}
