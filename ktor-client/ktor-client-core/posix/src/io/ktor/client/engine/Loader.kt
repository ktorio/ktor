package io.ktor.client.engine.ios

import io.ktor.client.engine.*
import io.ktor.util.*

@InternalAPI
@Suppress("KDocMissingDocumentation")
@ThreadLocal
val engines: MutableList<HttpClientEngineFactory<HttpClientEngineConfig>> by lazy {
    mutableListOf<HttpClientEngineFactory<HttpClientEngineConfig>>()
}
