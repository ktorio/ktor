/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*


@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("CompressionEncoder", "io.ktor.server.plugins.*")
)
public interface CompressionEncode

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("GzipEncoder", "io.ktor.server.plugins.*")
)
public object GzipEncoder

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("DeflateEncoder", "io.ktor.server.plugins.*")
)
public object DeflateEncoder

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("IdentityEncoder", "io.ktor.server.plugins.*")
)
public object IdentityEncoder

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ConditionsHolderBuilder", "io.ktor.server.plugins.*")
)
public interface ConditionsHolderBuilder

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("CompressionEncoderBuilder", "io.ktor.server.plugins.*")
)
public class CompressionEncoderBuilder

public fun ConditionsHolderBuilder.condition(predicate: ApplicationCall.(OutgoingContent) -> Boolean): Unit =
    error("Moved to io.ktor.server.plugins")

public fun ConditionsHolderBuilder.minimumSize(minSize: Long): Unit = error("Moved to io.ktor.server.plugins")

public fun ConditionsHolderBuilder.matchContentType(vararg mimeTypes: ContentType): Unit =
    error("Moved to io.ktor.server.plugins")

public fun ConditionsHolderBuilder.excludeContentType(vararg mimeTypes: ContentType): Unit =
    error("Moved to io.ktor.server.plugins")
