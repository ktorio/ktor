package io.ktor.client.engine.ios

import io.ktor.client.engine.*

object Ios : HttpClientEngineFactory<HttpClientEngineConfig> {
    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine =
        IosClientEngine(HttpClientEngineConfig().apply(block))
}