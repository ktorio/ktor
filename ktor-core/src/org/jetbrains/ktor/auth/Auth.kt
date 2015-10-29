package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

public sealed class HttpAuthCredentials(open val authScheme: String) {
    abstract fun render(): String

    public class Single(override val authScheme: String, val blob: String) : HttpAuthCredentials(authScheme) {
        override fun render(): String = "$authScheme $blob"

        fun copy(blob: String = this.blob) = Single(this.authScheme, blob)
    }

    public class Parameterized(override val authScheme: String, val parameters: Map<String, String>) : HttpAuthCredentials(authScheme) {
        override fun render(): String = "$authScheme ${parameters.entries.joinToString(", ") { "${it.key.encodeURL()}=\"${it.value.encodeURL()}\"" }}"

        fun copy(parameters: Map<String, String> = this.parameters) = Parameterized(this.authScheme, parameters)
    }
}

public sealed class HttpAuthChallenge(open val authScheme: String) {
    public class Single(override val authScheme: String, val blob: String) : HttpAuthChallenge(authScheme)
    public class Parameterized(override val authScheme: String, val parameters: Map<String, String>) : HttpAuthChallenge(authScheme)

    fun render() = when (this) {
        is HttpAuthChallenge.Single -> "$authScheme $blob" // TODO validate token68
        is HttpAuthChallenge.Parameterized -> "$authScheme ${parameters.map { "${it.key}=${it.value}" }.joinToString(", ")}" // TODO escape characters
    }

    companion object {
        val Basic = "Basic"
        val Digest = "Digest"
        val Negotiate = "Negotiate"
        val OAuth = "OAuth"

        fun basic(realm: String) = Parameterized(Basic, mapOf("realm" to realm))
    }
}

public fun ApplicationRequest.parseAuthorizationHeader(): HttpAuthCredentials? = authorization()?.let {
    parseAuthorizationHeader(it)
}

public fun parseAuthorizationHeader(headerValue: String): HttpAuthCredentials? {
    val token68Pattern = "[a-zA-Z0-9\\-\\._~+/]+=*".toRegex()
    val authSchemePattern = "\\S+".toRegex()
    val valuePatternPart = """("((\\.)|[^\\\"]+)*")|[^\s,]*"""
    val parameterPattern = "\\s*,?\\s*($token68Pattern)\\s*=\\s*($valuePatternPart)\\s*,?\\s*".toRegex()

    val schemeRegion = authSchemePattern.find(headerValue) ?: return null
    val authScheme = schemeRegion.value
    val remaining = headerValue.substringAfterMatch(schemeRegion).trimStart()

    val token68 = token68Pattern.find(remaining)
    if (token68 != null && remaining.substringAfterMatch(token68).isBlank()) {
        return HttpAuthCredentials.Single(authScheme, token68.value)
    }

    val parameters = parameterPattern.findAll(remaining)
            .map { it.groups[1]!!.value to it.groups[2]!!.value.unescapeIfQuoted() }
            .toMap()

    return HttpAuthCredentials.Parameterized(authScheme, parameters)
}

public fun ApplicationResponse.sendAuthenticationRequest(vararg challenges: HttpAuthChallenge = arrayOf(HttpAuthChallenge.basic("ktor"))): ApplicationRequestStatus {
    require(challenges.isNotEmpty()) { "it should be at least one challenge requested, for example Basic" }

    status(HttpStatusCode.Unauthorized)
    contentType(ContentType.Text.Plain)
    headers.append(HttpHeaders.WWWAuthenticate, challenges.map(HttpAuthChallenge::render).joinToString(", "))
    streamText("Not authorized")
    return ApplicationRequestStatus.Handled
}
