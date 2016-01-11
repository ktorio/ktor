package org.jetbrains.ktor.application

/**
 * Represents [Application] configuration
 */
interface ApplicationConfig {
    /**
     * [ClassLoader] used to load application.
     * Useful for various reflection-based services, like dependency injection.
     */
    val classLoader: ClassLoader

    /**
     * Specifies name of the environment this application is running in.
     */
    val environment: String

    /**
     * Port this application should be bound to.
     * TODO: Move to Host configuration, because Servlet doesn't use this config
     */
    val port: Int

    /**
     * Specifies if Application support async
     *
     * TODO: Move to Host configuration
     */
    val async: Boolean

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
fun applicationConfig(builder: ApplicationConfigBuilder.() -> Unit): ApplicationConfig = ApplicationConfigBuilder().apply(builder)

/**
 * Mutable implementation of [ApplicationConfig]
 * TODO: Replace with real builder to avoid mutation of config after the fact
 */
class ApplicationConfigBuilder : ApplicationConfig {
    override fun getString(configuration: String): String = throw UnsupportedOperationException()
    override fun getStringListOrEmpty(configuration: String): List<String> = throw UnsupportedOperationException()

    public override var classLoader: ClassLoader = ApplicationConfigBuilder::class.java.classLoader
    override var log: ApplicationLog = SLF4JApplicationLog("embedded")
    override var environment: String = "development"
    override var port: Int = 80
    override var async: Boolean = false
}