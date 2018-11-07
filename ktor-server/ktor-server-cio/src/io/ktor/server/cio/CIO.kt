package io.ktor.server.cio

import io.ktor.server.engine.*
import io.ktor.util.*

/**
 * An [ApplicationEngineFactory] providing a CIO-based [ApplicationEngine]
 */
@KtorExperimentalAPI
object CIO : ApplicationEngineFactory<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    override fun create(environment: ApplicationEngineEnvironment, configure: CIOApplicationEngine.Configuration.() -> Unit): CIOApplicationEngine {
        return CIOApplicationEngine(environment, configure)
    }
}
