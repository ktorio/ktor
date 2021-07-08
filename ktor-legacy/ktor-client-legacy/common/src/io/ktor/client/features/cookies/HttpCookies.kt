/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features.cookies

import io.ktor.http.*

@Deprecated(
    message = "Moved to io.ktor.client.plugins.cookies",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("HttpCookies", "io.ktor.client.plugins.cookies.*")
)
public class HttpCookies(
    private val storage: CookiesStorage,
    private val defaults: List<suspend CookiesStorage.() -> Unit>
)

@Deprecated(
    message = "Moved to io.ktor.client.plugins.cookies",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("cookies(url)", "io.ktor.client.plugins.cookies.*")
)
public suspend fun cookies(url: Url): List<Cookie> = error("Moved to io.ktor.client.plugins.cookies")

@Deprecated(
    message = "Moved to io.ktor.client.plugins.cookies",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("cookies(urlString)", "io.ktor.client.plugins.cookies.*")
)
public suspend fun cookies(urlString: String): List<Cookie> =
    error("Moved to io.ktor.client.plugins.cookies")

@Deprecated(
    message = "Moved to io.ktor.client.plugins.cookies",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("get(name)", "io.ktor.client.plugins.cookies.*")
)
public operator fun List<Cookie>.get(name: String): Cookie? = error("Moved to io.ktor.client.plugins.cookies")
