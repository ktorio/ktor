package io.ktor.config

import com.typesafe.config.*
import io.ktor.util.*

/**
 * Implements [ApplicationConfig] by loading configuration from HOCON data structures
 */
@KtorExperimentalAPI
open class HoconApplicationConfig(private val config: Config) : ApplicationConfig {
    override fun property(path: String): ApplicationConfigValue {
        if (!config.hasPath(path))
            throw ApplicationConfigurationException("Property $path not found.")
        return HoconApplicationConfigValue(config, path)
    }

    override fun propertyOrNull(path: String): ApplicationConfigValue? {
        if (!config.hasPath(path))
            return null
        return HoconApplicationConfigValue(config, path)
    }

    override fun configList(path: String): List<ApplicationConfig> {
        return config.getConfigList(path).map { HoconApplicationConfig(it) }
    }

    override fun config(path: String): ApplicationConfig = HoconApplicationConfig(config.getConfig(path))

    private class HoconApplicationConfigValue(val config: Config, val path: String) : ApplicationConfigValue {
        override fun getString(): String = config.getString(path)
        override fun getList(): List<String> = config.getStringList(path)
    }
}

/**
 * Get string property value for [path] or `null` if missing
 */
@KtorExperimentalAPI
fun Config.tryGetString(path: String): String? = if (hasPath(path)) getString(path) else null

/**
 * Get list property value for [path] or `null` if missing
 */
@KtorExperimentalAPI
fun Config.tryGetStringList(path: String): List<String>? = if (hasPath(path)) getStringList(path) else null
