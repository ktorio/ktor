package io.ktor.auth

import io.ktor.http.auth.*
import io.ktor.request.*

/**
 * Parses an authorization header from a [ApplicationRequest] returning a [HttpAuthHeader].
 */
fun ApplicationRequest.parseAuthorizationHeader(): HttpAuthHeader? = authorization()?.let {
    parseAuthorizationHeader(it)
}
