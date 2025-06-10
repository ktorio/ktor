/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

/**
 * Shorthand value for `ktor.application.modules` configuration list entries.
 */
public val ApplicationEnvironment.moduleConfigReferences: List<String> get() =
    config.propertyOrNull("ktor.application.modules")?.getList().orEmpty()

/**
 * Selected mode of loading application modules.
 */
public val ApplicationEnvironment.startupMode: ApplicationStartupMode get() =
    when (val text = config.propertyOrNull("ktor.application.startup")?.getString()?.lowercase()) {
        "concurrent" -> ApplicationStartupMode.CONCURRENT
        "sequential", null -> ApplicationStartupMode.SEQUENTIAL
        else -> error("Invalid startup mode: $text")
    }

/**
 * Configuration for the timeout on loading modules.  Defaults to 10 seconds.
 */
public val ApplicationEnvironment.startupTimeout: Long get() =
    config.propertyOrNull("ktor.application.startupTimeoutMillis")?.getString()?.toLong()?.milliseconds ?: 10.seconds

/**
 * Pre-defined methods for loading modules on server startup.
 */
public enum class ApplicationStartupMode {
    SEQUENTIAL,
    CONCURRENT
}
