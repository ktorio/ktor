/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

internal expect val CONFIG_PATH: List<String>

/**
 * Loads an application configuration.
 * An implementation of this interface should return [ServerConfig] if applicable configuration is found
 * or `null` otherwise.
 */
public interface ConfigLoader {
    /**
     * Tries loading an application configuration from the specified [path].
     *
     * @return configuration or null if the path is not found or configuration format is not supported.
     */
    public fun load(path: String?): ServerConfig?

    public companion object {
        /**
         * Find and load a configuration file to [ServerConfig].
         */
        public fun load(path: String? = null): ServerConfig {
            if (path == null) {
                val default = loadDefault()
                if (default != null) return default
            }

            for (loader in configLoaders) {
                val config = loader.load(path)
                if (config != null) return config
            }

            return MapServerConfig()
        }

        private fun loadDefault(): ServerConfig? {
            for (defaultPath in CONFIG_PATH) {
                for (loader in configLoaders) {
                    val config = loader.load(defaultPath)
                    if (config != null) return config
                }
            }

            return null
        }
    }
}

/**
 * List of all registered [ConfigLoader] implementations.
 */
public expect val configLoaders: List<ConfigLoader>
