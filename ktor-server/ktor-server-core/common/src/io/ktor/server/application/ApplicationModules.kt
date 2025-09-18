/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Shorthand value for `ktor.application.modules` configuration list entries.
 */
internal val ApplicationEnvironment.moduleConfigReferences: List<String> get() =
    config.propertyOrNull("ktor.application.modules")?.getList().orEmpty()

/**
 * Selected mode of loading application modules.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.startupMode)
 */
public val ApplicationEnvironment.startupMode: ApplicationStartupMode get() =
    when (val text = config.propertyOrNull("ktor.application.startup")?.getString()?.lowercase()) {
        "concurrent" -> ApplicationStartupMode.CONCURRENT
        "sequential", null -> ApplicationStartupMode.SEQUENTIAL
        else -> error("Invalid startup mode: $text")
    }

/**
 * Configuration for the timeout on loading modules.  Defaults to 10 seconds.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.startupTimeout)
 */
public val ApplicationEnvironment.startupTimeout: Duration get() =
    config.propertyOrNull("ktor.application.startupTimeoutMillis")?.getString()?.toLong()?.milliseconds ?: 10.seconds

/**
 * Pre-defined methods for loading modules on server startup.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationStartupMode)
 */
public enum class ApplicationStartupMode {
    SEQUENTIAL,
    CONCURRENT
}
