package io.ktor.server.cio

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*

/**
 * An [ApplicationEngineFactory] providing a CIO-based [ApplicationEngine]
 */
object CIO : ApplicationEngineFactory<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    override fun create(environment: ApplicationEngineEnvironment, configure: CIOApplicationEngine.Configuration.() -> Unit): CIOApplicationEngine {
        return CIOApplicationEngine(environment, configure)
    }
}