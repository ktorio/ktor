package io.ktor.auth

import io.ktor.content.*
import io.ktor.http.*
import io.ktor.util.*

class UnauthorizedResponse(vararg val challenges: HttpAuthHeader) : OutgoingContent.NoContent() {
    override val status: HttpStatusCode?
        get() = HttpStatusCode.Unauthorized

    override val headers: ValuesMap
        get() = if (challenges.isNotEmpty())
            valuesOf(HttpHeaders.WWWAuthenticate, listOf(challenges.joinToString(", ") { it.render() }), caseInsensitiveKey = true)
        else
            valuesOf()
}