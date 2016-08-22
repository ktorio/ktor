package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.util.*

class DefaultResponsePushBuilder(call: ApplicationCall) : ResponsePushBuilder {
    override val url = URLBuilder.createFromCall(call)
    override val headers = ValuesMapBuilder(true).apply { appendAll(call.request.headers); set(HttpHeaders.Referrer, call.url {}) }
    override var method = HttpMethod.Get
    override var versions = ArrayList<Version>()
}