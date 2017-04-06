package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.logging.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/**
 * Represents environment for a host
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
    var reloadPackages = emptyList<String>()
    var classLoader: ClassLoader = ApplicationHostEnvironment::class.java.classLoader
    var log: ApplicationLog = NullApplicationLog()
    var config: ApplicationConfig = MapApplicationConfig()
    var executor: ScheduledExecutorService = DefaultExecutorServiceBuilder()

    val connectors = mutableListOf<HostConnectorConfig>()
    val modules = mutableListOf<Application.() -> Unit>()

    fun module(body: Application.() -> Unit) {
        modules.add(body)
    }

    fun build(builder: ApplicationHostEnvironmentBuilder.() -> Unit): ApplicationHostEnvironment {
        builder(this)
        return ApplicationHostEnvironmentReloading(classLoader, log, config, connectors, executor, modules, reloadPackages)
    }

    companion object {
        private val poolCounter = AtomicInteger()
        internal val DefaultExecutorServiceBuilder = {
            val pool: Int = poolCounter.incrementAndGet()
            val threadCounter = AtomicInteger()
            Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 8, { r ->
                Thread(r, "ktor-pool-$pool-thread-${threadCounter.incrementAndGet()}")
            })
        }
    }
}