/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.application

import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ApplicationCall", "io.ktor.server.application.*")
)
public interface ApplicationCall
