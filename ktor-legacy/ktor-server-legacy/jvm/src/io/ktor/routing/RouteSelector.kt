/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.routing

import io.ktor.http.*

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("RouteSelectorEvaluation", "io.ktor.server.routing.*")
)
public sealed class RouteSelectorEvaluation(
    public val succeeded: Boolean
)

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("RouteSelector", "io.ktor.server.routing.*")
)
public abstract class RouteSelector

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ConstantParameterRouteSelector", "io.ktor.server.routing.*")
)
public data class ConstantParameterRouteSelector(
    val name: String,
    val value: String
)

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ParameterRouteSelector", "io.ktor.server.routing.*")
)
public data class ParameterRouteSelector(
    val name: String
)

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("OptionalParameterRouteSelector", "io.ktor.server.routing.*")
)
public data class OptionalParameterRouteSelector(
    val name: String
)

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("PathSegmentConstantRouteSelector", "io.ktor.server.routing.*")
)
public data class PathSegmentConstantRouteSelector(
    val value: String
)

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("TrailingSlashRouteSelector", "io.ktor.server.routing.*")
)
public object TrailingSlashRouteSelector

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("PathSegmentParameterRouteSelector", "io.ktor.server.routing.*")
)
public data class PathSegmentParameterRouteSelector(
    val name: String,
    val prefix: String? = null,
    val suffix: String? = null
)

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("PathSegmentOptionalParameterRouteSelector", "io.ktor.server.routing.*")
)
public data class PathSegmentOptionalParameterRouteSelector(
    val name: String,
    val prefix: String? = null,
    val suffix: String? = null
)

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("PathSegmentWildcardRouteSelector", "io.ktor.server.routing.*")
)
public object PathSegmentWildcardRouteSelector

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("PathSegmentTailcardRouteSelector", "io.ktor.server.routing.*")
)
public data class PathSegmentTailcardRouteSelector(
    val name: String = "",
    val prefix: String = ""
)

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("OrRouteSelector", "io.ktor.server.routing.*")
)
public data class OrRouteSelector(
    val first: RouteSelector,
    val second: RouteSelector
)

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("AndRouteSelector", "io.ktor.server.routing.*")
)
public data class AndRouteSelector(
    val first: RouteSelector,
    val second: RouteSelector
)

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("HttpMethodRouteSelector", "io.ktor.server.routing.*")
)
public data class HttpMethodRouteSelector(
    val method: HttpMethod
)

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("HttpHeaderRouteSelector", "io.ktor.server.routing.*")
)
public data class HttpHeaderRouteSelector(
    val name: String,
    val value: String
)

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("HttpAcceptRouteSelector", "io.ktor.server.routing.*")
)
public data class HttpAcceptRouteSelector(
    val contentType: ContentType
)
