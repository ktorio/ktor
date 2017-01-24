package org.jetbrains.ktor.auth

import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

class UnauthorizedResponse(vararg val challenges: HttpAuthHeader = arrayOf(HttpAuthHeader.basicAuthChallenge("ktor"))) : FinalContent.NoContent() {
    override val status: HttpStatusCode?
        get() = HttpStatusCode.Unauthorized

    override val headers: ValuesMap
        get() = ValuesMap.build(true) {
            if (challenges.isNotEmpty()) {
                append(HttpHeaders.WWWAuthenticate, challenges.joinToString(", ") { it.render() })
            }
        }
}