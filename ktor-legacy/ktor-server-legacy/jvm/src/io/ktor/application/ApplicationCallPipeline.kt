/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.application

import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.pipeline.*

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ApplicationCallPipeline", "io.ktor.server.application.*")
)
@Suppress("PublicApiImplicitType")
public open class ApplicationCallPipeline(
    public val developmentMode: Boolean = false
)

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("call", "io.ktor.server.application.*")
)
public inline val PipelineContext<*, ApplicationCall>.call: ApplicationCall
    get() = error("Moved to io.ktor.server.plugins")

@Deprecated(
    message = "Moved to io.ktor.server.application",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("application", "io.ktor.server.application.*")
)
public val PipelineContext<*, ApplicationCall>.application: Application
    get() = error("Moved to io.ktor.server.application")

