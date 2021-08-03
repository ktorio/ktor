/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("UserAgent", "io.ktor.client.plugins.*")
)
public class UserAgent(public val agent: String)

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("BrowserUserAgent()", "io.ktor.client.plugins.*")
)
public fun BrowserUserAgent(): Unit = error("Moved to io.ktor.client.plugins")

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("CurlUserAgent()", "io.ktor.client.plugins.*")
)
public fun CurlUserAgent(): Unit = error("Moved to io.ktor.client.plugins")
