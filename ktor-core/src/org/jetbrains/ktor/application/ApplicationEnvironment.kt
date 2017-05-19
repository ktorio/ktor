package org.jetbrains.ktor.application

import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.logging.*
import java.util.concurrent.*

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
     * Provides events on Application lifecycle
     */
    val monitor: ApplicationMonitor
}

