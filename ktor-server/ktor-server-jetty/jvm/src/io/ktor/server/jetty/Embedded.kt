package io.ktor.server.jetty

import io.ktor.server.engine.*

/**
 * An [ApplicationEngineFactory] providing a Jetty-based [ApplicationEngine]
 */
object Jetty : ApplicationEngineFactory<JettyApplicationEngine, JettyApplicationEngineBase.Configuration> {
    override fun create(environment: ApplicationEngineEnvironment, configure: JettyApplicationEngineBase.Configuration.() -> Unit): JettyApplicationEngine {
        return JettyApplicationEngine(environment, configure)
    }
}
