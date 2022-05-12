/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

/**
 * Loads an application configuration.
 * An implementation of this interface should return [ApplicationConfig] if applicable configuration is found
 * or `null` otherwise.
 */
public interface ConfigLoader {
    public fun load(path: String?): ApplicationConfig?
}

/**
 * List of all registered [ConfigLoader] implementations
 */
public expect val configLoaders: List<ConfigLoader>
