package io.ktor.application

import io.ktor.config.*
import org.slf4j.*

/**
 * Represents an environment in which [Application] runs
 */
interface ApplicationEnvironment {
    /**
     * [ClassLoader] used to load application.
     *
     * Useful for various reflection-based services, like dependency injection.
     */
    val classLoader: ClassLoader

    /**
     * Instance of [Logger] to be used for logging.
     */
    val log: Logger

    /**
     * Configuration for the [Application]
     */
    val config: ApplicationConfig

    /**
     * Provides events on Application lifecycle
     */
    val monitor: ApplicationEvents
}

/**
 * Event definition for Application Starting event
 *
 * Note, that application itself cannot receive this event because it fires before application is created
 * It is meant to be used by engines.
 */
val ApplicationStarting = EventDefinition<Application>()

/**
 * Event definition for Application Started event
 */
val ApplicationStarted = EventDefinition<Application>()

val ApplicationStopPreparing = EventDefinition<ApplicationEnvironment>()

/**
 * Event definition for Application Stopping event
 */
val ApplicationStopping = EventDefinition<Application>()

/**
 * Event definition for Application Stopped event
 */
val ApplicationStopped = EventDefinition<Application>()