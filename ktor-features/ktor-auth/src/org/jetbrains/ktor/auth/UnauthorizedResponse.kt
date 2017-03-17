package org.jetbrains.ktor.auth

import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

class UnauthorizedResponse(vararg val challenges: HttpAuthHeader) : FinalContent.NoContent() {
    override val status: HttpStatusCode?
        get() = HttpStatusCode.Unauthorized

    override val headers: ValuesMap
        get() = if (challenges.isNotEmpty())
            valuesOf(HttpHeaders.WWWAuthenticate, listOf(challenges.joinToString(", ") { it.render() }), caseInsensitiveKey = true)
        else
            valuesOf()
}