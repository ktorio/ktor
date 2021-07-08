/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("HttpSendInterceptor", "io.ktor.client.plugins.*")
)
public typealias HttpSendInterceptor = suspend Sender.() -> Unit

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("HttpSendInterceptorBackwardCompatible", "io.ktor.client.plugins.*")
)
public typealias HttpSendInterceptorBackwardCompatible = suspend Sender.() -> Unit

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("Sender", "io.ktor.client.plugins.*")
)
public interface Sender

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("HttpSend", "io.ktor.client.plugins.*")
)
public class HttpSend(maxSendCount: Int = 20)

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("SendCountExceedException", "io.ktor.client.plugins.*")
)
public class SendCountExceedException(message: String) : IllegalStateException(message)
