package org.jetbrains.ktor.application

import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.nio.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/**
 * Represents an environment in which [Application] runs
 */
interface ApplicationEnvironment {
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
     * Host executor service constructor
     */
    val executorServiceBuilder: () -> ScheduledExecutorService

    /**
     * ByteBuffer instance pool
     */
    @Deprecated("")
    val byteBufferPool: ByteBufferPool
}

class BasicApplicationEnvironment(override val classLoader: ClassLoader,
                                  override val log: ApplicationLog,
                                  override val config: ApplicationConfig,
                                  override val executorServiceBuilder: () -> ScheduledExecutorService = DefaultExecutorServiceBuilder,
                                  override val byteBufferPool: ByteBufferPool = NoPool) : ApplicationEnvironment

/**
 * Creates [ApplicationEnvironment] using [ApplicationEnvironmentBuilder]
 */
inline fun applicationEnvironment(builder: ApplicationEnvironmentBuilder.() -> Unit): ApplicationEnvironment = ApplicationEnvironmentBuilder().apply(builder)

/**
 * Mutable implementation of [ApplicationEnvironment]
 * TODO: Replace with real builder to avoid mutation of config after the fact
 */
class ApplicationEnvironmentBuilder : ApplicationEnvironment {
    override var classLoader: ClassLoader = ApplicationEnvironmentBuilder::class.java.classLoader
    override var log: ApplicationLog = SLF4JApplicationLog("embedded")
    override val config = MapApplicationConfig()
    override var executorServiceBuilder: () -> ScheduledExecutorService = DefaultExecutorServiceBuilder
    override var byteBufferPool: ByteBufferPool = NoPool
}

private val poolCounter = AtomicInteger()
internal val DefaultExecutorServiceBuilder = {
    val pool: Int = poolCounter.incrementAndGet()
    val threadCounter = AtomicInteger()
    Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors(), { r ->
        Thread(r, "ktor-pool-$pool-thread-${threadCounter.incrementAndGet()}")
    })
}
