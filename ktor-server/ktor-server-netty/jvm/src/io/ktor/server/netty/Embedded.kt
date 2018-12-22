package io.ktor.server.netty

import io.ktor.server.engine.*

/**
 * An [ApplicationEngineFactory] providing a Netty-based [ApplicationEngine]
 */
object Netty : ApplicationEngineFactory<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
    override fun create(environment: ApplicationEngineEnvironment, configure: NettyApplicationEngine.Configuration.() -> Unit): NettyApplicationEngine {
        return NettyApplicationEngine(environment, configure)
    }
}
