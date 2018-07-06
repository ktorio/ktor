package io.ktor.auth

import io.ktor.http.*
import io.ktor.request.*
import io.ktor.util.*
import java.nio.charset.*
import java.util.*
import kotlin.collections.LinkedHashMap

object AuthScheme {
    val Basic = "Basic"
    val Digest = "Digest"
    val Negotiate = "Negotiate"
    val OAuth = "OAuth"
}

enum class HeaderValueEncoding {
    QUOTED_WHEN_REQUIRED,
    QUOTED_ALWAYS,
    URI_ENCODE
}

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

sealed class HttpAuthHeader(val authScheme: String) {
    init {
        require(authScheme.matches(token68Pattern)) { "invalid authScheme value: it should be token" }
    }

    class Single(authScheme: String, val blob: String) : HttpAuthHeader(authScheme) {
        init {
            require(blob.matches(token68Pattern)) { "invalid blob value: it should be token68 but it is $blob" }
        }

        override fun render() = "$authScheme $blob"
        override fun render(encoding: HeaderValueEncoding) = render()

        override fun equals(other: Any?): Boolean {
            if (other !is HttpAuthHeader.Single) return false
            return other.authScheme.equals(authScheme, ignoreCase = true) &&
                other.blob.equals(blob, ignoreCase = true)
        }

        override fun hashCode(): Int {
            return Objects.hash(authScheme.toLowerCase(), blob.toLowerCase())
        }
    }

    class Parameterized(authScheme: String, val parameters: List<HeaderValueParam>, val encoding: HeaderValueEncoding = HeaderValueEncoding.QUOTED_WHEN_REQUIRED) : HttpAuthHeader(authScheme) {
        constructor(authScheme: String, parameters: Map<String, String>, encoding: HeaderValueEncoding = HeaderValueEncoding.QUOTED_WHEN_REQUIRED) : this(authScheme, parameters.entries.map { HeaderValueParam(it.key, it.value) }, encoding)

        init {
            parameters.forEach {
                require(it.name.matches(token68Pattern)) { "parameter name should be a token but it is ${it.name}" }
            }
        }

        fun withParameter(name: String, value: String) = Parameterized(authScheme, this.parameters + HeaderValueParam(name, value))
        override fun render(encoding: HeaderValueEncoding) = parameters.joinToString(", ", prefix = "$authScheme ") { "${it.name}=${it.value.encode(encoding)}" }
        fun parameter(name: String) = parameters.singleOrNull { it.name == name }?.value

        private fun String.encode(encoding: HeaderValueEncoding) = when (encoding) {
            HeaderValueEncoding.QUOTED_WHEN_REQUIRED -> escapeIfNeeded()
            HeaderValueEncoding.QUOTED_ALWAYS -> quote()
            HeaderValueEncoding.URI_ENCODE -> encodeURLParameter()
        }

        override fun render(): String = render(encoding)

        override fun equals(other: Any?): Boolean {
            if (other !is HttpAuthHeader.Parameterized) return false
            return other.authScheme.equals(authScheme, ignoreCase = true) &&
                other.parameters == parameters
        }

        override fun hashCode(): Int {
            return Objects.hash(authScheme.toLowerCase(), parameters)
        }
    }

    abstract fun render(encoding: HeaderValueEncoding): String
    abstract fun render(): String

    override fun toString(): String {
        return render()
    }

    companion object {
        fun basicAuthChallenge(realm: String, charset: Charset?) = Parameterized(
            AuthScheme.Basic, LinkedHashMap<String, String>().apply {
                put(Parameters.Realm, realm)
                if (charset != null) {
                    put(Parameters.Charset, charset.name())
                }
            }
        )

        fun digestAuthChallenge(realm: String, nonce: String = nextNonce(), domain: List<String> = emptyList(), opaque: String? = null, stale: Boolean? = null, algorithm: String = "MD5")
                = Parameterized(AuthScheme.Digest, linkedMapOf<String, String>().apply {
            put("realm", realm)
            put("nonce", nonce)
            if (domain.isNotEmpty()) {
                put("domain", domain.joinToString(" "))
            }
            if (opaque != null) {
                put("opaque", opaque)
            }
            if (stale != null) {
                put("stale", stale.toString())
            }
            put("algorithm", algorithm)
        }, HeaderValueEncoding.QUOTED_ALWAYS)
    }

    object Parameters {
        val Realm = "realm"
        val Charset = "charset"

        val OAuthCallback = "oauth_callback"
        val OAuthConsumerKey = "oauth_consumer_key"
        val OAuthNonce = "oauth_nonce"
        val OAuthToken = "oauth_token"
        val OAuthTokenSecret = "oauth_token_secret"
        val OAuthVerifier = "oauth_verifier"
        val OAuthSignatureMethod = "oauth_signature_method"
        val OAuthTimestamp = "oauth_timestamp"
        val OAuthVersion = "oauth_version"
        val OAuthSignature = "oauth_signature"
        val OAuthCallbackConfirmed = "oauth_callback_confirmed"
    }
}

private fun String.substringAfterMatch(mr: MatchResult) = drop(mr.range.endInclusive + if (mr.range.isEmpty()) 0 else 1)
private val escapeRegex = "\\\\.".toRegex()
private fun String.unescapeIfQuoted() = when {
    startsWith('"') && endsWith('"') -> removeSurrounding("\"").replace(escapeRegex) { it.value.takeLast(1) }
    else -> this
}
