package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.config.*
import org.slf4j.*

/**
 * Represents an environment in which host runs
 */
interface ApplicationHostEnvironment : ApplicationEnvironment {
    /**
     * Connectors that describers where and how server should listen
     */
    val connectors: List<HostConnectorConfig>

    /**
     * Running [Application]
     *
     * Throws an exception if environment has not been started
     */
    val application: Application

    /**
     * Starts [ApplicationHostEnvironment] and creates an application
     */
    fun start()

    /**
     * Stops [ApplicationHostEnvironment] and destroys any running application
     */
    fun stop()
}

/**
 * Creates [ApplicationHostEnvironment] using [ApplicationHostEnvironmentBuilder]
 */
fun applicationHostEnvironment(builder: ApplicationHostEnvironmentBuilder.() -> Unit): ApplicationHostEnvironment {
    return ApplicationHostEnvironmentBuilder().build(builder)
}

class ApplicationHostEnvironmentBuilder {
    var watchPaths = emptyList<String>()
    var classLoader: ClassLoader = ApplicationHostEnvironment::class.java.classLoader
    var log: Logger = LoggerFactory.getLogger("Application")
    var config: ApplicationConfig = MapApplicationConfig()

    val connectors = mutableListOf<HostConnectorConfig>()
    val modules = mutableListOf<Application.() -> Unit>()

    fun module(body: Application.() -> Unit) {
        modules.add(body)
    }

    fun build(builder: ApplicationHostEnvironmentBuilder.() -> Unit): ApplicationHostEnvironment {
        builder(this)
        return ApplicationHostEnvironmentReloading(classLoader, log, config, connectors, modules, watchPaths)
    }
}