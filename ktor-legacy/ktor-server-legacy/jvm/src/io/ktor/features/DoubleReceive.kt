/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.features

import io.ktor.util.reflect.*

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("DoubleReceive", "io.ktor.server.plugins.*")
)
public class DoubleReceive

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("CachedTransformationResult", "io.ktor.server.plugins.*")
)
public sealed class CachedTransformationResult<T : Any>(public val type: TypeInfo) {
    public class Success<T : Any>(type: TypeInfo, public val value: T) : CachedTransformationResult<T>(type)

    public open class Failure(type: TypeInfo, public val cause: Throwable) : CachedTransformationResult<Nothing>(type)
}

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("RequestReceiveAlreadyFailedException", "io.ktor.server.plugins.*")
)
public class RequestReceiveAlreadyFailedException internal constructor(
    cause: Throwable
) : Exception("Request body consumption was failed", cause, false, true)
