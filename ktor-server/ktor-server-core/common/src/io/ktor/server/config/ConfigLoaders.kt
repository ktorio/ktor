/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import io.ktor.util.logging.*

internal expect val CONFIG_PATH: List<String>

private val logger = KtorSimpleLogger("io.ktor.server.config.ConfigLoader")

/**
 * Loads an application configuration.
 * An implementation of this interface should return [ApplicationConfig] if applicable configuration is found
 * or `null` otherwise.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.ConfigLoader)
 */
public interface ConfigLoader {
    /**
     * Tries loading an application configuration from the specified [path].
     *
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.ConfigLoader.load)
     *
     * @return configuration or null if the path is not found or configuration format is not supported.
     */
    public fun load(path: String?): ApplicationConfig?

    public companion object {
        /**
         * Loads application configurations from the specified configuration paths.
         *
         * If no paths are provided, a default configuration is loaded.
         * If a single path is provided, the configuration from the given path is loaded.
         * If multiple paths are provided, the configurations are merged in sequence.
         *
         * @param configPaths A variable number of configuration file paths to load.
         * @return An [ApplicationConfig] instance representing the loaded configuration(s).
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.ConfigLoader.Companion.loadAll)
         */
        public fun loadAll(vararg configPaths: String): ApplicationConfig =
            when (configPaths.size) {
                0 -> load()
                1 -> load(configPaths.single())
                else -> configPaths.map(::load).reduce(ApplicationConfig::mergeWith)
            }

        /**
         * Find and load a configuration file to [ApplicationConfig].
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.ConfigLoader.Companion.load)
         */
        public fun load(path: String? = null): ApplicationConfig {
            if (path == null) {
                logger.debug("Loading default configuration")
                val default = loadDefault()
                if (default != null) return default
            }

            for (loader in configLoaders) {
                logger.debug("Trying ConfigLoader: ${loader::class.simpleName}")
                val config = loader.load(path)
                if (config != null) {
                    logger.debug("Configuration loaded successfully using ${loader::class.simpleName} from path: $path")
                    return config
                }
            }

            logger.debug("No configuration found, using empty MapApplicationConfig")
            return MapApplicationConfig()
        }

        private fun loadDefault(): ApplicationConfig? {
            for (defaultPath in CONFIG_PATH) {
                logger.debug("Trying default config path: $defaultPath")
                for (loader in configLoaders) {
                    logger.debug("Trying ConfigLoader: ${loader::class.simpleName} for path: $defaultPath")
                    val config = loader.load(defaultPath)
                    if (config != null) {
                        logger.debug("Default configuration loaded using ${loader::class.simpleName} from path: $defaultPath")
                        return config
                    }
                }
            }

            return null
        }
    }
}

/**
 * List of all registered [ConfigLoader] implementations.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.configLoaders)
 */
public expect val configLoaders: List<ConfigLoader>
