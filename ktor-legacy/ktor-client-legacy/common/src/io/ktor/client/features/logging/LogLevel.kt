/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features.logging

@Deprecated(
    message = "Moved to io.ktor.client.plugins.logging",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("LogLevel", "io.ktor.client.plugins.logging.*")
)
public enum class LogLevel(
    public val info: Boolean,
    public val headers: Boolean,
    public val body: Boolean
) {
    ALL(true, true, true),
    HEADERS(true, true, false),
    BODY(true, false, true),
    INFO(true, false, false),
    NONE(false, false, false)
}
