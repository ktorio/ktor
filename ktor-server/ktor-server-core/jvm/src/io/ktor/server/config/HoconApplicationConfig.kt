/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import com.typesafe.config.*
import java.io.*

public class HoconConfigLoader : ConfigLoader {
    override fun load(path: String?): ApplicationConfig? {
        val resolvedPath = when {
            path == null -> "application.conf"
            path.endsWith(".conf") || path.endsWith(".json") || path.endsWith(".properties") -> path
            else -> return null
        }
        val resource = Thread.currentThread().contextClassLoader.getResource(resolvedPath)
        if (resource != null) return HoconApplicationConfig(ConfigFactory.load(resolvedPath).resolve())
        val file = File(resolvedPath)
        if (file.exists()) {
            return HoconApplicationConfig(ConfigFactory.parseFile(file).resolve())
        }
        return null
    }
}

/**
 * Implements [ApplicationConfig] by loading configuration from HOCON data structures
 */
public open class HoconApplicationConfig(private val config: Config) : ApplicationConfig {
    override fun property(path: String): ApplicationConfigValue {
        if (!config.hasPath(path)) {
            throw ApplicationConfigurationException("Property $path not found.")
        }
        return HoconApplicationConfigValue(config, path)
    }

    override fun propertyOrNull(path: String): ApplicationConfigValue? {
        if (!config.hasPath(path)) {
            return null
        }
        return HoconApplicationConfigValue(config, path)
    }

    override fun configList(path: String): List<ApplicationConfig> {
        return config.getConfigList(path).map { HoconApplicationConfig(it) }
    }

    override fun config(path: String): ApplicationConfig = HoconApplicationConfig(config.getConfig(path))

    override fun keys(): Set<String> {
        return config.entrySet().map { it.key }.toSet()
    }

    private class HoconApplicationConfigValue(val config: Config, val path: String) : ApplicationConfigValue {
        override fun getString(): String = config.getString(path)
        override fun getList(): List<String> = config.getStringList(path)
    }
}

/**
 * Returns a string value for [path] or `null` if missing
 */
public fun Config.tryGetString(path: String): String? = if (hasPath(path)) getString(path) else null

/**
 * Returns a list of values for [path] or `null` if missing
 */
public fun Config.tryGetStringList(path: String): List<String>? = if (hasPath(path)) getStringList(path) else null

/**
 * Returns [ApplicationConfig] by loading configuration from a resource specified by [configPath]
 * or a default resource if [configPath] is `null`
 */
public fun ApplicationConfig(configPath: String?): ApplicationConfig =
    configLoaders.firstNotNullOfOrNull { it.load(configPath) } ?: MapApplicationConfig()
