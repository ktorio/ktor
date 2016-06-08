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
     * Specifies name of the environment this application is running in.
     */
    val stage: String

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
                                  override val config: ApplicationConfig) : ApplicationEnvironment {
    override var stage: String = config.propertyOrNull("ktor.deployment.environment")?.getString() ?: ApplicationEnvironmentStage.Development
}

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
    override var stage: String
        get() = config.propertyOrNull("ktor.deployment.environment")?.getString() ?: ApplicationEnvironmentStage.Development
        set(value) {
            config.put("ktor.deployment.environment", value)
        }
}

