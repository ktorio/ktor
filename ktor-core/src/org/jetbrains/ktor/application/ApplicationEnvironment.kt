package org.jetbrains.ktor.application

import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.logging.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/**
 * Represents an environment in which [Application] runs
 */
interface ApplicationEnvironment : AutoCloseable {
    /**
     * [ClassLoader] used to load application.
     * Useful for various reflection-based services, like dependency injection.
     */
    val classLoader: ClassLoader

    /**
     * Instance of [ApplicationLog] to be used for logging.
     */
    val log: ApplicationLog

    /**
     * Configuration for [Application]
     */
    val config: ApplicationConfig

    /**
     * Environment-provided executor
     */
    val executor: ScheduledExecutorService

    /**
     * Provides events on Application lifecycle
     */
    val monitor: ApplicationMonitor
}

class BasicApplicationEnvironment(override val classLoader: ClassLoader,
                                  override val log: ApplicationLog,
                                  override val config: ApplicationConfig,
                                  override val executor: ScheduledExecutorService = DefaultExecutorServiceBuilder()) : ApplicationEnvironment {

    override val monitor = ApplicationMonitor().logEvents()

    override fun close() {
        // TODO: should we shutdown the service here?
        executor.shutdown()
        if (!executor.awaitTermination(10L, TimeUnit.SECONDS)) {
            log.warning("Failed to stop environment executor service")
            executor.shutdownNow()
        }
    }
}

/**
 * Creates [ApplicationEnvironment] using [ApplicationEnvironmentBuilder]
 */
inline fun applicationEnvironment(builder: ApplicationEnvironmentBuilder.() -> Unit): ApplicationEnvironment {
    return ApplicationEnvironmentBuilder().build(builder)
}

/**
 * Mutable implementation of [ApplicationEnvironment]
 * TODO: Replace with real builder to avoid mutation of config after the fact
 */
class ApplicationEnvironmentBuilder {
    var classLoader: ClassLoader = ApplicationEnvironmentBuilder::class.java.classLoader
    var log: ApplicationLog = SLF4JApplicationLog("embedded")
    val config = MapApplicationConfig()
    var executor: ScheduledExecutorService = DefaultExecutorServiceBuilder()

    inline fun build(builder: ApplicationEnvironmentBuilder.() -> Unit): ApplicationEnvironment {
        builder()
        return BasicApplicationEnvironment(classLoader, log, config, executor)
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
