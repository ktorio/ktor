/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.cors.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.*
import io.ktor.server.response.respond
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.route

/**
 * A plugin that allows you to configure handling cross-origin requests.
 * This plugin allows you to configure allowed hosts, HTTP methods, headers set by the client, and so on.
 *
 * The configuration below allows requests from the specified address and allows sending the `Content-Type` header:
 * ```kotlin
 * install(CORS) {
 *     host("0.0.0.0:8081")
 *     header(HttpHeaders.ContentType)
 * }
 * ```
 *
 * You can learn more from [CORS](https://ktor.io/docs/cors.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.cors.routing.CORS)
 */
private const val optionsParam = "static-options-param"

public val CORS: RouteScopedPlugin<CORSConfig> = createRouteScopedPlugin("CORS", ::CORSConfig) {
//    println(route)
//    route?.createChild(object : RouteSelector() {
//        override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
//            RouteSelectorEvaluation.Success(quality = RouteSelectorEvaluation.qualityTailcard)
//
//        override fun toString() = "(CORS Options)"
//    })?.apply {
//        route("{$optionsParam...}") {
//            options {
//                call.respond(HttpStatusCode.OK)
//            }
//        }
//    }

    buildPlugin()
}
