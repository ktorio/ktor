package io.ktor.client.engine.apache

import io.ktor.client.engine.*


object Apache : HttpClientEngineFactory<ApacheEngineConfig> {
    override fun create(block: ApacheEngineConfig.() -> Unit): HttpClientEngine {
        val config = ApacheEngineConfig().apply(block)
        return ApacheEngine(config)
    }
}
