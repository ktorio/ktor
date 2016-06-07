package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

interface Credential
interface Principal

fun ApplicationRequest.parseAuthorizationHeader(): HttpAuthHeader? = authorization()?.let {
    parseAuthorizationHeader(it)
}

private val token68Pattern = "[a-zA-Z0-9\\-._~+/]+=*".toRegex()
private val authSchemePattern = "\\S+".toRegex()
private val valuePatternPart = """("((\\.)|[^\\"])*")|[^\s,]*"""
private val parameterPattern = "\\s*,?\\s*($token68Pattern)\\s*=\\s*($valuePatternPart)\\s*,?\\s*".toRegex()

fun parseAuthorizationHeader(headerValue: String): HttpAuthHeader? {
    val schemeRegion = authSchemePattern.find(headerValue) ?: return null
    val authScheme = schemeRegion.value
    val remaining = headerValue.substringAfterMatch(schemeRegion).trimStart()

    val token68 = token68Pattern.find(remaining)
    if (token68 != null && remaining.substringAfterMatch(token68).isBlank()) {
        return HttpAuthHeader.Single(authScheme, token68.value)
    }

    val parameters = parameterPattern.findAll(remaining)
            .associateBy({ it.groups[1]!!.value }, { it.groups[2]!!.value.unescapeIfQuoted() })

    return HttpAuthHeader.Parameterized(authScheme, parameters)
}

fun ApplicationCall.sendAuthenticationRequest(vararg challenges: HttpAuthHeader = arrayOf(HttpAuthHeader.basicAuthChallenge("ktor"))): Unit {
    require(challenges.isNotEmpty()) { "it should be at least one challenge requested, for example Basic" }

    respond(UnauthorizedResponse(*challenges))
}

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