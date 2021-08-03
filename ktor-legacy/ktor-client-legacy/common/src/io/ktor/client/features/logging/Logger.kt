/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features.logging

@Deprecated(
    message = "Moved to io.ktor.client.plugins.logging",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("Logger", "io.ktor.client.plugins.logging.*")
)
public interface Logger {
    public companion object
}

@Deprecated(
    message = "Moved to io.ktor.client.plugins.logging",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("DEFAULT", "io.ktor.client.plugins.logging.*")
)
public val Logger.Companion.DEFAULT: Logger
    get() = error("Moved to io.ktor.client.plugins.logging")

@Deprecated(
    message = "Moved to io.ktor.client.plugins.logging",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("SIMPLE", "io.ktor.client.plugins.logging.*")
)
public val Logger.Companion.SIMPLE: Logger
    get() = error("Moved to io.ktor.client.plugins.logging")

@Deprecated(
    message = "Moved to io.ktor.client.plugins.logging",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("EMPTY", "io.ktor.client.plugins.logging.*")
)
public val Logger.Companion.EMPTY: Logger
    get() = error("Moved to io.ktor.client.plugins.logging")
