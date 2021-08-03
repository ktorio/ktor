/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ResponseValidator", "io.ktor.client.plugins.*")
)
public typealias ResponseValidator = suspend (response: Any) -> Unit

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("CallExceptionHandler", "io.ktor.client.plugins.*")
)
public typealias CallExceptionHandler = suspend (cause: Throwable) -> Unit

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("HttpCallValidator", "io.ktor.client.plugins.*")
)
public class HttpCallValidator

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("HttpResponseValidator(block)", "io.ktor.client.plugins.*")
)
public fun HttpResponseValidator(block: () -> Unit): Unit =
    error("Moved to io.ktor.client.plugins")

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("expectSuccess", "io.ktor.client.plugins.*")
)
public var expectSuccess: Boolean
    get() = error("Moved to io.ktor.client.plugins")
    set(value) = error("Moved to io.ktor.client.plugins")
