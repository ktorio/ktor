package io.ktor.client.engine.jetty

import io.ktor.client.*
import io.ktor.client.engine.*


object Jetty : HttpClientEngineFactory<HttpClientEngineConfig> {
    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine = JettyHttp2Engine()
}
