/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.request

@Deprecated(
    message = "Moved to io.ktor.server.request",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ApplicationRequest", "io.ktor.server.request.*")
)
public interface ApplicationRequest
