/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.routing

import io.ktor.application.*

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("RoutingResolveTraceEntry", "io.ktor.server.routing.*")
)
public open class RoutingResolveTraceEntry(
    public val route: Route,
    public val segmentIndex: Int,
    public var result: RoutingResolveResult? = null
)

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("RoutingResolveTrace", "io.ktor.server.routing.*")
)
public class RoutingResolveTrace(public val call: ApplicationCall, public val segments: List<String>)
