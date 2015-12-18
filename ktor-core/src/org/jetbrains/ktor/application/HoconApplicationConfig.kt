package org.jetbrains.ktor.application

import com.typesafe.config.*

/**
 * Store application configuration.
 */
public open class HoconApplicationConfig(private val config: Config,
                                         public override val classLoader: ClassLoader,
                                         public override val log: ApplicationLog = NullApplicationLog()
) : ApplicationConfig {
    override val environment: String get() = config.getString("ktor.deployment.environment")

    /** The port to run the server on. */
    override val port: Int = config.getIntOrDefault("ktor.deployment.port", 80)
    override val async: Boolean = config.getBooleanOrDefault("ktor.deployment.async", false)

    override fun getString(configuration: String): String = config.getString(configuration)
    override fun getStringListOrEmpty(configuration: String): List<String> = config.getStringListOrEmpty(configuration)

    fun tryGet(configuration: String): String? = if (config.hasPath(configuration))
        config.getString(configuration)
    else
        null

    private fun Config.getStringListOrEmpty(path: String): List<String> =
            if (hasPath(path))
                getStringList(path)
            else
                emptyList()

    private fun Config.getIntOrDefault(path: String, default: Int): Int =
            if (hasPath(path))
                getInt(path)
            else
                default

    private fun Config.getBooleanOrDefault(path: String, default: Boolean): Boolean =
            if (hasPath(path))
                getBoolean(path)
            else
                default
}