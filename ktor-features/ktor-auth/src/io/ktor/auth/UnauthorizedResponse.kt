package io.ktor.auth

import io.ktor.http.content.*
import io.ktor.http.*

class UnauthorizedResponse(vararg val challenges: HttpAuthHeader) : OutgoingContent.NoContent() {
    override val status: HttpStatusCode?
        get() = HttpStatusCode.Unauthorized

    override val headers: Headers
        get() = if (challenges.isNotEmpty())
            headersOf(HttpHeaders.WWWAuthenticate, challenges.joinToString(", ") { it.render() })
        else
            Headers.Empty
}