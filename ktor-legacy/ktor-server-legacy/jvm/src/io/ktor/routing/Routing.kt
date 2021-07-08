/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.routing

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("Routing", "io.ktor.server.routing.*")
)
public class Routing(
    public val application: Application
)

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("routing", "io.ktor.server.routing.*")
)
@ContextDsl
public fun Application.routing(configuration: Routing.() -> Unit): Routing =
   error("Moved to io.ktor.server.routing")
