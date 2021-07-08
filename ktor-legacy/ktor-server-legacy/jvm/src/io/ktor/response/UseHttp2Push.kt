/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.response

@Deprecated(
    message = "Moved to io.ktor.server.response",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("UseHttp2Push", "io.ktor.server.response.*")
)
@RequiresOptIn(
    "HTTP/2 push is no longer supported by some web browsers.",
    level = RequiresOptIn.Level.WARNING
)
public annotation class UseHttp2Push
