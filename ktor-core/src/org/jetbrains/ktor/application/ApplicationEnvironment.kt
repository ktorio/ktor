package org.jetbrains.ktor.application

import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.logging.*

/**
 * Represents [Application] configuration
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
}

class BasicApplicationEnvironment(override val classLoader: ClassLoader,
                                  override val log: ApplicationLog,
                                  override val config: ApplicationConfig) : ApplicationEnvironment

/**
 * Creates [ApplicationEnvironment] using [ApplicationEnvironmentBuilder]
 */
inline fun applicationEnvironment(builder: ApplicationEnvironmentBuilder.() -> Unit): ApplicationEnvironment = ApplicationEnvironmentBuilder().apply(builder)

object ApplicationEnvironmentStage {
    val Development = "development"
    val Staging = "staging"
    val Production = "production"
    val Embedded = "embedded"
}

/**
 * Mutable implementation of [ApplicationEnvironment]
 * TODO: Replace with real builder to avoid mutation of config after the fact
 */
class ApplicationEnvironmentBuilder : ApplicationEnvironment {
    override var classLoader: ClassLoader = ApplicationEnvironmentBuilder::class.java.classLoader
    override var log: ApplicationLog = SLF4JApplicationLog("embedded")
    override val config = MapApplicationConfig()
}

