package org.jetbrains.ktor.application

import org.jetbrains.ktor.logging.*

/**
 * Represents [Application] configuration
 */
interface ApplicationConfig {
    /**
     * [ClassLoader] used to load application.
     * Useful for various reflection-based services, like dependency injection.
     */
    val classLoader: ClassLoader

    // TODO: convert to enum?
    /**
     * Specifies name of the environment this application is running in.
     */
    val environment: String

    /**
     * Instance of [ApplicationLog] to be used for logging.
     */
    val log: ApplicationLog

    /**
     * Gets and arbitrary configuration string
     */
    fun getString(configuration: String): String

    /**
     * Gets and arbitrary configuration string list
     */
    fun getStringListOrEmpty(configuration: String): List<String>
}

/**
 * Creates [ApplicationConfig] using [ApplicationConfigBuilder]
 */
inline fun applicationConfig(builder: ApplicationConfigBuilder.() -> Unit): ApplicationConfig = ApplicationConfigBuilder().apply(builder)

/**
 * Mutable implementation of [ApplicationConfig]
 * TODO: Replace with real builder to avoid mutation of config after the fact
 */
class ApplicationConfigBuilder : ApplicationConfig {
    override fun getString(configuration: String): String = throw UnsupportedOperationException()
    override fun getStringListOrEmpty(configuration: String): List<String> = throw UnsupportedOperationException()

    override var classLoader: ClassLoader = ApplicationConfigBuilder::class.java.classLoader
    override var log: ApplicationLog = SLF4JApplicationLog("embedded")
    override var environment: String = "development"
}