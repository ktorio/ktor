package org.jetbrains.ktor.config

import com.typesafe.config.*

/**
 * Implements [ApplicationEnvironment] by loading configuration from HOCON data structures
 */
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

    override fun config(path: String): ApplicationConfig {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private class HoconApplicationConfigValue(val config: Config, val path: String) : ApplicationConfigValue {
        override fun getString(): String = config.getString(path)
        override fun getList(): List<String> = config.getStringList(path)
    }
}

