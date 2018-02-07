package io.ktor.client.engine.cio

import io.ktor.client.engine.*

object CIO : HttpClientEngineFactory<CIOEngineConfig> {
    override fun create(block: CIOEngineConfig.() -> Unit): HttpClientEngine =
            CIOEngine(CIOEngineConfig().apply(block))
}
