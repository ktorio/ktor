/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features.cache

import io.ktor.client.features.cache.storage.*
import io.ktor.http.*

@Deprecated(
    message = "Moved to io.ktor.client.plugins.cache",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("HttpCache", "io.ktor.client.plugins.cache.*")
)
public class HttpCache(
    public val publicStorage: HttpCacheStorage,
    public val privateStorage: HttpCacheStorage
)

@Deprecated(
    message = "Moved to io.ktor.client.plugins.cache",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("InvalidCacheStateException", "io.ktor.client.plugins.cache.*")
)
public class InvalidCacheStateException(requestUrl: Url) : IllegalStateException(
    "The entry for url: $requestUrl was removed from cache"
)
