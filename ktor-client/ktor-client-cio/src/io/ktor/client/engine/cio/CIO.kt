package io.ktor.client.engine.cio

import io.ktor.client.engine.*

object CIO : HttpClientEngineFactory<HttpClientEngineConfig> {
    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine =
            CIOEngine(CIOEngineConfig().apply(block))
}
