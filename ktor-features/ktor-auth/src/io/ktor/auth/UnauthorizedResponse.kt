package io.ktor.auth

import io.ktor.content.*
import io.ktor.http.*
import io.ktor.util.*

class UnauthorizedResponse(vararg val challenges: HttpAuthHeader) : OutgoingContent.NoContent() {
    override val status: HttpStatusCode?
        get() = HttpStatusCode.Unauthorized

    override val headers: StringValues
        get() = if (challenges.isNotEmpty())
            valuesOf(HttpHeaders.WWWAuthenticate, challenges.joinToString(", ") { it.render() }, caseInsensitiveKey = true)
        else
            valuesOf()
}