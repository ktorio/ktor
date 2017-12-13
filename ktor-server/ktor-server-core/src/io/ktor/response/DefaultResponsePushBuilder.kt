package io.ktor.response

import io.ktor.application.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.util.*
import java.util.*

class DefaultResponsePushBuilder(override val url : URLBuilder, headers: ValuesMap) : ResponsePushBuilder {
    constructor(call: ApplicationCall) : this(URLBuilder.createFromCall(call), call.request.headers)

    override val headers = ValuesMapBuilder(true).apply { appendAll(headers); set(HttpHeaders.Referrer, url.buildString()) }
    override var method = HttpMethod.Get
    override var versions = ArrayList<Version>()
}