/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.features

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("CallIdProvider", "io.ktor.server.plugins.*")
)
public typealias CallIdProvider = Nothing

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("CallIdVerifier", "io.ktor.server.plugins.*")
)
public typealias CallIdVerifier = Nothing

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("RejectedCallIdException", "io.ktor.server.plugins.*")
)
public class RejectedCallIdException(
    public val illegalCallId: String
) : IllegalArgumentException()

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("CallId", "io.ktor.server.plugins.*")
)
public class CallId

@Deprecated(
    message = "Moved to io.ktor.server.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("CALL_ID_DEFAULT_DICTIONARY", "io.ktor.server.plugins.*")
)
public val CALL_ID_DEFAULT_DICTIONARY: String
    get() = "Moved to io.ktor.server.plugins"
