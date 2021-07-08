/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.routing

import io.ktor.application.*

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("RoutingResolveResult", "io.ktor.server.routing.*")
)
public sealed class RoutingResolveResult(public val route: Route)

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("RoutingResolveContext", "io.ktor.server.routing.*")
)
public class RoutingResolveContext(
    public val routing: Route,
    public val call: ApplicationCall,
    private val tracers: List<(RoutingResolveTrace) -> Unit>
)
