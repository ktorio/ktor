/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing

/**
 * Common interface for formatting standard route path components.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RoutePathFormat)
 */
public interface RoutePathFormat {
    public companion object {
        /**
         * Default [RoutePathFormat] implementation for Ktor routing diagnostics.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RoutePathFormat.Companion.Default)
         */
        public val Default: RoutePathFormat = object : RoutePathFormat {
            override fun format(selector: RoutePathComponent): String =
                when (selector) {
                    is PathSegmentConstantRouteSelector,
                    is PathSegmentParameterRouteSelector,
                    is PathSegmentOptionalParameterRouteSelector,
                    PathSegmentWildcardRouteSelector,
                    is PathSegmentRegexRouteSelector,
                    is PathSegmentTailcardRouteSelector,
                    is RootRouteSelector -> selector.toString()

                    TrailingSlashRouteSelector -> "/"
                }
        }
    }

    /**
     * Formats a [RoutePathComponent] into a string.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RoutePathFormat.format)
     */
    public fun format(selector: RoutePathComponent): String
}

/**
 * A [RoutePathFormat] that uses OpenAPI path formatting rules.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.OpenApiRoutePathFormat)
 */
public object OpenApiRoutePathFormat : RoutePathFormat {
    override fun format(selector: RoutePathComponent): String =
        when (selector) {
            is PathSegmentParameterRouteSelector,
            is PathSegmentConstantRouteSelector,
            is RootRouteSelector -> selector.toString()

            is PathSegmentOptionalParameterRouteSelector ->
                with(selector) { "${prefix ?: ""}{$name}${suffix ?: ""}" }

            // Regex unsupported, so we treat it as a wildcard
            is PathSegmentRegexRouteSelector ->
                with(selector) { if (regex.pattern.trim('/').contains('/')) "{**}" else "{*}" }

            is PathSegmentTailcardRouteSelector -> "{**}"
            PathSegmentWildcardRouteSelector -> "{*}"
            TrailingSlashRouteSelector -> "/"
        }
}
