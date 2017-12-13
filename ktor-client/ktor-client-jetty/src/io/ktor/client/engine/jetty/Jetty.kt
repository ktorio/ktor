package io.ktor.client.engine.jetty

import io.ktor.client.engine.*


object Jetty : HttpClientEngineFactory<JettyEngineConfig> {
    override fun create(block: JettyEngineConfig.() -> Unit): HttpClientEngine =
            JettyHttp2Engine(JettyEngineConfig().apply(block))
}
