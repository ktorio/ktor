/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features.cache

import io.ktor.util.date.*

@Deprecated(
    message = "Moved to io.ktor.client.plugins.cache",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("HttpCacheEntry", "io.ktor.client.plugins.cache.*")
)
public class HttpCacheEntry internal constructor(
    public val expires: GMTDate,
    public val varyKeys: Map<String, String>,
    public val response: Any,
    public val body: ByteArray
)
