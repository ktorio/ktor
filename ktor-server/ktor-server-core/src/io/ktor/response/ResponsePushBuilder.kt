package io.ktor.response

import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.util.*

/**
 * HTTP/2 push builder interface
 *
 * @property url push URL
 * @property headers request headers
 * @property method request method
 * @property versions request versions (last modification date, etag and so on)
 */
@KtorExperimentalAPI
interface ResponsePushBuilder {
    val url: URLBuilder
    val headers: HeadersBuilder
    var method: HttpMethod
    val versions: MutableList<Version>
}
