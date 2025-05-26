/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.htmx

import io.ktor.htmx.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlin.jvm.JvmInline

/**
 * Property for scoping routes to HTMX (e.g., `hx.get { ... }`
 */
@ExperimentalKtorApi
public val Route.hx: HxRoute get() = HxRoute.wrap(this)

/**
 * Scope child routes to apply when `HX-Request` header is supplied.
 */
@ExperimentalKtorApi
public fun Route.hx(configuration: HxRoute.() -> Unit): Route = with(HxRoute.wrap(this)) {
    header(HxRequestHeaders.Request, "true") {
        configuration()
    }
}

/**
 * Provides custom routes based on common HTMX headers.
 */
@ExperimentalKtorApi
@KtorDsl
@JvmInline
public value class HxRoute internal constructor(private val route: Route) : Route by route {
    internal companion object {
        internal fun wrap(route: Route) =
            HxRoute(route.createChild(HttpHeaderRouteSelector(HxRequestHeaders.Request, "true")))
    }

    /**
     * Sub-routes only apply to a specific HX-Target header.
     */
    public fun target(expectedTarget: String, body: Route.() -> Unit): Route {
        return header(HxRequestHeaders.Target, expectedTarget, body)
    }

    /**
     * Sub-routes only apply to a specific HX-Trigger header.
     */
    public fun trigger(expectedTrigger: String, body: Route.() -> Unit): Route =
        header(HxRequestHeaders.Trigger, expectedTrigger, body)
}
