/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.routing

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("RoutingPath", "io.ktor.server.routing.*")
)
public class RoutingPath

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("RoutingPathSegment", "io.ktor.server.routing.*")
)
public data class RoutingPathSegment(val value: String, val kind: RoutingPathSegmentKind)

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("RoutingPathSegmentKind", "io.ktor.server.routing.*")
)
public enum class RoutingPathSegmentKind {
    Constant,
    Parameter
}
