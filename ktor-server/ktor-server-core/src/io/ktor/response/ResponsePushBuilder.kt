package io.ktor.response

import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.util.*

@KtorExperimentalAPI
interface ResponsePushBuilder {
    val url: URLBuilder
    val headers: HeadersBuilder
    var method: HttpMethod
    val versions: MutableList<Version>
}
