/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.response

@Deprecated(
    message = "Moved to io.ktor.server.response",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ResponseHeaders", "io.ktor.server.response.*")
)
public abstract class ResponseHeaders
