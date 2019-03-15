package io.ktor.client.engine

import io.ktor.util.*

@InternalAPI
@Suppress("KDocMissingDocumentation")
val engines: MutableList<HttpClientEngineFactory<HttpClientEngineConfig>> by lazy {
    mutableListOf<HttpClientEngineFactory<HttpClientEngineConfig>>()
}
