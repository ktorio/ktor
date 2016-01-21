package org.jetbrains.ktor.application

import com.typesafe.config.*
import org.jetbrains.ktor.logging.*

/**
 * Implements [ApplicationConfig] by loading configuration from HOCON data structures
 */
public open class HoconApplicationConfig(private val config: Config,
                                         public override val classLoader: ClassLoader,
                                         public override val log: ApplicationLog = NullApplicationLog()
) : ApplicationConfig {
    override val environment: String = config.getString("ktor.deployment.environment")

    override fun getString(configuration: String): String = config.getString(configuration)
    override fun getStringListOrEmpty(configuration: String): List<String> = config.getStringListOrEmpty(configuration)

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

fun Config.tryGetString(configuration: String): String? = if (hasPath(configuration))
    getString(configuration)
else
    null
