package io.ktor.client.engine.jetty

import io.ktor.client.engine.*

/**
 * [HttpClientEngineFactory] using `org.eclipse.jetty.http2:http2-client`
 * with the the associated configuration [JettyEngineConfig].
 *
 * Just supports HTTP/2 requests.
 */
object Jetty : HttpClientEngineFactory<JettyEngineConfig> {
    override fun create(block: JettyEngineConfig.() -> Unit): HttpClientEngine =
        JettyHttp2Engine(JettyEngineConfig().apply(block))
}
