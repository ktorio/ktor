package io.ktor.client.engine.jetty

import io.ktor.client.engine.*
import org.eclipse.jetty.util.ssl.*


class JettyEngineConfig : HttpClientEngineConfig() {
    var sslContextFactory = SslContextFactory(true)
}
