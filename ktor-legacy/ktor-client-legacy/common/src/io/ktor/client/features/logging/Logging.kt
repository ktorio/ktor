/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features.logging

@Deprecated(
    message = "Moved to io.ktor.client.plugins.logging",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("Logging", "io.ktor.client.plugins.logging.*")
)
public class Logging(
    public val logger: Logger,
    public var level: LogLevel,
    public var filters: Any = emptyList<Any>()
)

@Deprecated(
    message = "Moved to io.ktor.client.plugins.logging",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("Logging(block)", "io.ktor.client.plugins.logging.*")
)
public fun Logging(block: Any = {}): Unit = error("Moved to io.ktor.client.plugins.logging")
