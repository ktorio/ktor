/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("addDefaultResponseValidation()", "io.ktor.client.plugins.*")
)
public fun addDefaultResponseValidation(): Unit = error("Moved to io.ktor.client.plugins")

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ResponseException", "io.ktor.client.plugins.*")
)
public open class ResponseException(
    response: Any,
    cachedResponseText: String
) : IllegalStateException("Bad response: $response. Text: \"$cachedResponseText\"")

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("RedirectResponseException", "io.ktor.client.plugins.*")
)
public class RedirectResponseException(response: Any, cachedResponseText: String) :
    ResponseException(response, cachedResponseText)

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ServerResponseException", "io.ktor.client.plugins.*")
)
public class ServerResponseException(
    response: Any,
    cachedResponseText: String
) : ResponseException(response, cachedResponseText)

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ClientRequestException", "io.ktor.client.plugins.*")
)
public class ClientRequestException(
    response: Any,
    cachedResponseText: String
) : ResponseException(response, cachedResponseText)
