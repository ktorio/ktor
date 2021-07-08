/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.routing

import io.ktor.util.pipeline.*

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("Route", "io.ktor.server.routing.*")
)
@ContextDsl
public open class Route(
    public val parent: Route?,
    public val selector: RouteSelector,
    developmentMode: Boolean,
)
