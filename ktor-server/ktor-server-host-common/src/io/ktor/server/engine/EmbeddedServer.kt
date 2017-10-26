package io.ktor.server.engine

import io.ktor.application.*
import org.slf4j.*

interface ApplicationEngineFactory<out TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> {
    fun create(environment: ApplicationEngineEnvironment, configure: TConfiguration.() -> Unit): TEngine
}

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

fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> embeddedServer(factory: ApplicationEngineFactory<TEngine, TConfiguration>, environment: ApplicationEngineEnvironment, configure: TConfiguration.() -> Unit = {}): TEngine {
    return factory.create(environment, configure)
}

