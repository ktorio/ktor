package io.ktor.response

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.util.*

class DefaultResponsePushBuilder(
        override var method: HttpMethod = HttpMethod.Get,
        override val url: URLBuilder = URLBuilder(),
        override val headers: HeadersBuilder = HeadersBuilder(),
        versions: List<Version> = emptyList()
) : ResponsePushBuilder {

    constructor(url: URLBuilder, headers: Headers) : this(url = url, headers = HeadersBuilder().apply { appendAll(headers) })

    constructor(call: ApplicationCall) : this(
            url = URLBuilder.createFromCall(call),
            headers = HeadersBuilder().apply {
                appendAll(call.request.headers)
                set(HttpHeaders.Referrer, call.url())
            })

    override var versions = if (versions.isEmpty()) ArrayList() else ArrayList<Version>(versions)
}
