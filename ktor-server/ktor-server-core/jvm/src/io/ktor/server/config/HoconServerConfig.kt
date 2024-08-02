/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import com.typesafe.config.*
import java.io.*

/**
 * Loads a [Config] from a hocon file.
 */
public class HoconConfigLoader : ConfigLoader {

    /**
     * Tries loading an application configuration from the specified [path].
     *
     * @return configuration or null if the path is not found or configuration format is not supported.
     */
    override fun load(path: String?): ServerConfig? {
        val resolvedPath = when {
            path == null -> "application.conf"
            path.endsWith(".conf") || path.endsWith(".json") || path.endsWith(".properties") -> path
            else -> return null
        }

        val resource = Thread.currentThread().contextClassLoader.getResource(resolvedPath)
        val config = when {
            resource != null -> ConfigFactory.load(resolvedPath)
            else -> {
                val file = File(resolvedPath)
                if (file.exists()) ConfigFactory.parseFile(file) else null
            }
        }?.resolve() ?: return null

        return HoconServerConfig(config)
    }
}

@Deprecated(message = "Renamed to HoconServerConfig", replaceWith = ReplaceWith("HoconServerConfig"))
public typealias HoconApplicationConfig = HoconServerConfig

/**
 * Implements [ServerConfig] by loading configuration from HOCON data structures
 */
public open class HoconServerConfig(private val config: Config) : ServerConfig {
    override fun property(path: String): ServerConfigValue {
        if (!config.hasPath(path)) {
            throw ServerConfigurationException("Property $path not found.")
        }
        return HoconServerConfigValue(config, path)
    }

    override fun propertyOrNull(path: String): ServerConfigValue? {
        if (!config.hasPath(path)) {
            return null
        }
        return HoconServerConfigValue(config, path)
    }

    override fun configList(path: String): List<ServerConfig> {
        return config.getConfigList(path).map { HoconServerConfig(it) }
    }

    override fun config(path: String): ServerConfig = HoconServerConfig(config.getConfig(path))

    override fun keys(): Set<String> {
        return config.entrySet().map { it.key }.toSet()
    }

    override fun toMap(): Map<String, Any?> {
        return config.root().unwrapped()
    }

    private class HoconServerConfigValue(val config: Config, val path: String) : ServerConfigValue {
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
 * Returns [ServerConfig] by loading configuration from a resource specified by [configPath]
 * or a default resource if [configPath] is `null`
 */
public fun ApplicationConfig(configPath: String?): ServerConfig =
    ConfigLoader.load(configPath)
