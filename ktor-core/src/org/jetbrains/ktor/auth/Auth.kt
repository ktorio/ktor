package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

public fun ApplicationRequest.parseAuthorizationHeader(): HttpAuthHeader? = authorization()?.let {
    parseAuthorizationHeader(it)
}

public fun parseAuthorizationHeader(headerValue: String): HttpAuthHeader? {
    val token68Pattern = "[a-zA-Z0-9\\-\\._~+/]+=*".toRegex()
    val authSchemePattern = "\\S+".toRegex()
    val valuePatternPart = """("((\\.)|[^\\\"]+)*")|[^\s,]*"""
    val parameterPattern = "\\s*,?\\s*($token68Pattern)\\s*=\\s*($valuePatternPart)\\s*,?\\s*".toRegex()

    val schemeRegion = authSchemePattern.find(headerValue) ?: return null
    val authScheme = schemeRegion.value
    val remaining = headerValue.substringAfterMatch(schemeRegion).trimStart()

    val token68 = token68Pattern.find(remaining)
    if (token68 != null && remaining.substringAfterMatch(token68).isBlank()) {
        return HttpAuthHeader.Single(authScheme, token68.value)
    }

    val parameters = parameterPattern.findAll(remaining)
            .map { it.groups[1]!!.value to it.groups[2]!!.value.unescapeIfQuoted() }
            .toMap()

    return HttpAuthHeader.Parameterized(authScheme, parameters)
}

public fun ApplicationResponse.sendAuthenticationRequest(vararg challenges: HttpAuthHeader = arrayOf(HttpAuthHeader.basic("ktor"))): ApplicationRequestStatus {
    require(challenges.isNotEmpty()) { "it should be at least one challenge requested, for example Basic" }

    status(HttpStatusCode.Unauthorized)
    contentType(ContentType.Text.Plain)
    headers.append(HttpHeaders.WWWAuthenticate, challenges.joinToString(", ") { it.render(HeaderValueEncoding.QUOTED) })
    streamText("Not authorized")
    return ApplicationRequestStatus.Handled
}
