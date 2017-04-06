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

abstract class BaseApplicationHostEnvironment(override val classLoader: ClassLoader,
                                              override val log: ApplicationLog,
                                              override val config: ApplicationConfig,
                                              override val executor: ScheduledExecutorService = DefaultExecutorServiceBuilder()) : ApplicationHostEnvironment {

    override val monitor = ApplicationMonitor().logEvents()

    fun close() {
        // TODO: should we shutdown the service here?
        executor.shutdown()
        if (!executor.awaitTermination(10L, TimeUnit.SECONDS)) {
            log.warning("Failed to stop environment executor service")
            executor.shutdownNow()
        }
    }
}

private val poolCounter = AtomicInteger()
internal val DefaultExecutorServiceBuilder = {
    val pool: Int = poolCounter.incrementAndGet()
    val threadCounter = AtomicInteger()
    Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 8, { r ->
        Thread(r, "ktor-pool-$pool-thread-${threadCounter.incrementAndGet()}")
    })
}

/**
 * Creates [ApplicationHostEnvironment] using [ApplicationHostEnvironmentBuilder]
 */
fun applicationHostEnvironment(builder: ApplicationHostEnvironmentBuilder.() -> Unit): ApplicationHostEnvironment {
    return ApplicationHostEnvironmentBuilder().build(builder)
}

class ApplicationHostEnvironmentBuilder {
    var automaticReload = false
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
        builder()
        return ApplicationHostEnvironmentReloading(classLoader, log, config, connectors, executor, modules, automaticReload)
    }
}