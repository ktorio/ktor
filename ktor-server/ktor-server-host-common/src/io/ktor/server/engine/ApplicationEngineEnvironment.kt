package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.config.*
import org.slf4j.*

/**
 * Represents an environment in which engine runs
 */
interface ApplicationEngineEnvironment : ApplicationEnvironment {
    /**
     * Connectors that describers where and how server should listen
     */
    val connectors: List<EngineConnectorConfig>

    /**
     * Running [Application]
     *
     * Throws an exception if environment has not been started
     */
    val application: Application

    /**
     * Starts [ApplicationEngineEnvironment] and creates an application
     */
    fun start()

    /**
     * Stops [ApplicationEngineEnvironment] and destroys any running application
     */
    fun stop()
}

/**
 * Creates [ApplicationEngineEnvironment] using [ApplicationEngineEnvironmentBuilder]
 */
fun applicationEngineEnvironment(builder: ApplicationEngineEnvironmentBuilder.() -> Unit): ApplicationEngineEnvironment {
    return ApplicationEngineEnvironmentBuilder().build(builder)
}

class ApplicationEngineEnvironmentBuilder {
    var watchPaths = emptyList<String>()
    var classLoader: ClassLoader = ApplicationEngineEnvironment::class.java.classLoader
    var log: Logger = LoggerFactory.getLogger("Application")
    var config: ApplicationConfig = MapApplicationConfig()

    val connectors = mutableListOf<EngineConnectorConfig>()
    val modules = mutableListOf<Application.() -> Unit>()

    fun module(body: Application.() -> Unit) {
        modules.add(body)
    }

    fun build(builder: ApplicationEngineEnvironmentBuilder.() -> Unit): ApplicationEngineEnvironment {
        builder(this)
        return ApplicationEngineEnvironmentReloading(classLoader, log, config, connectors, modules, watchPaths)
    }
}