/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features.compression

@Deprecated(
    message = "Moved to io.ktor.client.plugins.compression",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ContentEncoding", "io.ktor.client.plugins.compression.*")
)
public class ContentEncoding(
    private val encoders: Map<String, ContentEncoder>,
    private val qualityValues: Map<String, Float>
)

@Deprecated(
    message = "Moved to io.ktor.client.plugins.compression",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ContentEncoding(block)", "io.ktor.client.plugins.compression.*")
)
public fun ContentEncoding(block: Any): Unit = error("Moved to io.ktor.client.plugins.compression")

@Deprecated(
    message = "Moved to io.ktor.client.plugins.compression",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("UnsupportedContentEncodingException", "io.ktor.client.plugins.compression.*")
)
public class UnsupportedContentEncodingException(encoding: String) :
    IllegalStateException("Content-Encoding: $encoding unsupported.")
