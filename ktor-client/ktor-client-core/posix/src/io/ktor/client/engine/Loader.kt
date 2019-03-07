package io.ktor.client.engine

import kotlin.native.concurrent.*
import io.ktor.util.*

@InternalAPI
@Suppress("KDocMissingDocumentation")
@ThreadLocal
val engines: MutableList<HttpClientEngineFactory<HttpClientEngineConfig>> by lazy {
    mutableListOf<HttpClientEngineFactory<HttpClientEngineConfig>>()
}
