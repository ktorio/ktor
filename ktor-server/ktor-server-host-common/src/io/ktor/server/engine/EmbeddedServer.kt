package io.ktor.server.engine

import io.ktor.application.*
import org.slf4j.*

/**
 * Factory interface for creating [ApplicationEngine] instances
 */
interface ApplicationEngineFactory<out TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> {
    /**
     * Creates an engine from the given [environment] and [configure] script
     */
    fun create(environment: ApplicationEngineEnvironment, configure: TConfiguration.() -> Unit): TEngine
}

/**
 * Creates an embedded server with the given [factory], listening on [host]:[port]
 * @param watchPaths specifies path substrings that will be watched for automatic reloading
 * @param configure configuration script for the engine
 * @param module application module function
 */
fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>
        embeddedServer(factory: ApplicationEngineFactory<TEngine, TConfiguration>,
                       port: Int = 80,
                       host: String = "0.0.0.0",
                       watchPaths: List<String> = emptyList(),
                       configure: TConfiguration.() -> Unit = {},
                       module: Application.() -> Unit): TEngine {
    val environment = applicationEngineEnvironment {
        this.log = LoggerFactory.getLogger("ktor.application")
        this.watchPaths = watchPaths
        this.module(module)

        connector {
            this.port = port
            this.host = host
        }
    }

    return embeddedServer(factory, environment, configure)
}

/**
 * Creates an embedded server with the given [factory], [environment] and [configure] script
 */
fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>
        embeddedServer(factory: ApplicationEngineFactory<TEngine, TConfiguration>,
                       environment: ApplicationEngineEnvironment,
                       configure: TConfiguration.() -> Unit = {}): TEngine {
    return factory.create(environment, configure)
}

