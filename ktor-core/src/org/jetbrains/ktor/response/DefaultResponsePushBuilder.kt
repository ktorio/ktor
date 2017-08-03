package org.jetbrains.ktor.response

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.util.*

class DefaultResponsePushBuilder(override val url : URLBuilder, headers: ValuesMap) : ResponsePushBuilder {
    constructor(call: ApplicationCall) : this(URLBuilder.createFromCall(call), call.request.headers)

    override val headers = ValuesMapBuilder(true).apply { appendAll(headers); set(HttpHeaders.Referrer, url.build()) }
    override var method = HttpMethod.Get
    override var versions = ArrayList<Version>()
}