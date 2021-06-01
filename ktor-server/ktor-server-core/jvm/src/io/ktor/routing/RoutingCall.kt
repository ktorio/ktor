/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.routing

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.reflect.*

public class RoutingRequest internal constructor(
    @InternalAPI public val call: ApplicationCall
) {
    public val queryParameters: Parameters = call.request.queryParameters
    public val pathVariables: Parameters = call.parameters // TODO
    public val headers: Headers = call.request.headers
    public val local: RequestConnectionPoint = call.request.local
    public val cookies: RequestCookies = call.request.cookies
}

public class RoutingResponse internal constructor(
    @InternalAPI public val call: ApplicationCall
) {

    public val headers: ResponseHeaders = call.response.headers
    public val cookies: ResponseCookies = call.response.cookies

    public var status: HttpStatusCode?
        get() = call.response.status()
        set(value) {
            value?.let { call.response.status(it) }
        }
}

@Deprecated("Please use property instead", replaceWith = ReplaceWith("this.status = status"))
public fun RoutingResponse.status(status: HttpStatusCode) {
    this.status = status
}

public class RoutingCall internal constructor(
    public val route: Route,
    @InternalAPI public val call: ApplicationCall
) {

    public val request: RoutingRequest = RoutingRequest(call)
    public val response: RoutingResponse = RoutingResponse(call)
    public val attributes: Attributes = call.attributes

    public val parameters: Parameters = Parameters.build {
        appendAll(request.pathVariables)
        appendMissing(request.queryParameters)
    }

    public suspend inline fun <reified T> receive(): T = receive(typeInfo<T>())

    public suspend fun <T> receive(typeInfo: TypeInfo): T = call.receive(typeInfo)
}
