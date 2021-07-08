/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.request

import io.ktor.http.*
import io.ktor.util.collections.*

@Deprecated(
    message = "Moved to io.ktor.server.request",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("RequestCookies", "io.ktor.server.request.*")
)
public open class RequestCookies(protected val request: ApplicationRequest)
