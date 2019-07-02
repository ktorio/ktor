/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.auth

import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.charsets.*

private const val valuePatternPart = """("((\\.)|[^\\"])*")|[^\s,]*"""

private val token68Pattern = "[a-zA-Z0-9\\-._~+/]+=*".toRegex()
private val authSchemePattern = "\\S+".toRegex()
private val parameterPattern = "\\s*,?\\s*($token68Pattern)\\s*=\\s*($valuePatternPart)\\s*,?\\s*".toRegex()
private val escapeRegex: Regex = "\\\\.".toRegex()

/**
 * Parses an authorization header [headerValue] into a [HttpAuthHeader].
 */
fun parseAuthorizationHeader(headerValue: String): HttpAuthHeader? {
    val schemeRegion = authSchemePattern.find(headerValue) ?: return null
    val authScheme = schemeRegion.value
    val remaining = headerValue.substringAfterMatch(schemeRegion).trimStart()

    val token68 = token68Pattern.find(remaining)
    if (token68 != null && remaining.substringAfterMatch(token68).isBlank()) {
        return HttpAuthHeader.Single(authScheme, token68.value)
    }

    val parameters = parameterPattern.findAll(remaining).associateBy(
        { it.groups[1]!!.value },
        { it.groups[2]!!.value.unescapeIfQuoted() }
    )

    return HttpAuthHeader.Parameterized(authScheme, parameters)
}


/**
 * Describes an authentication header with a mandatory [authScheme] that usually is a standard [AuthScheme].
 *
 * This can be of type [HttpAuthHeader.Single] or [HttpAuthHeader.Parameterized].
 *
 * @property authScheme auth scheme, usually one of [AuthScheme]
 */
sealed class HttpAuthHeader(val authScheme: String) {
    init {
        require(authScheme.matches(token68Pattern)) { "invalid authScheme value: it should be token" }
    }

    /**
     * Describes an authentication header that is represented by a single [blob].
     * @property blob contains single token 68, should consist from digits, letters and one of the following: `-._~+/`
     */
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
            return Hash.combine(authScheme.toLowerCase(), blob.toLowerCase())
        }
    }

    /**
     * Describes a parameterized authentication header that is represented by a set of [parameters] encoded with [encoding].
     * @property parameters a list of auth parameters
     * @property encoding parameters encoding method, one of [HeaderValueEncoding]
     */
    class Parameterized(
        authScheme: String,
        val parameters: List<HeaderValueParam>,
        val encoding: HeaderValueEncoding = HeaderValueEncoding.QUOTED_WHEN_REQUIRED
    ) : HttpAuthHeader(authScheme) {
        constructor(
            authScheme: String,
            parameters: Map<String, String>,
            encoding: HeaderValueEncoding = HeaderValueEncoding.QUOTED_WHEN_REQUIRED
        ) : this(authScheme, parameters.entries.map { HeaderValueParam(it.key, it.value) }, encoding)

        init {
            parameters.forEach {
                require(it.name.matches(token68Pattern)) { "parameter name should be a token but it is ${it.name}" }
            }
        }

        /**
         * Copies this [Parameterized] appending a new parameter [name] [value].
         */
        fun withParameter(name: String, value: String) =
            Parameterized(authScheme, this.parameters + HeaderValueParam(name, value), encoding)

        /**
         * Copies this [Parameterized] replacing parameters with [name] assigning new [value]
         * or appending if no such parameters found.
         * If there were several pairs they will be reduced into a single pair
         * at position of first occurrence discarding following pairs with this [name].
         */
        @KtorExperimentalAPI
        fun withReplacedParameter(name: String, value: String): Parameterized {
            val firstIndex = parameters.indexOfFirst { it.name == name }
            if (firstIndex == -1) return withParameter(name, value)

            var replaced = false
            val newParameters = parameters.mapNotNull {
                when {
                    it.name != name -> it
                    !replaced -> {
                        replaced = true
                        HeaderValueParam(name, value)
                    }
                    else -> null
                }
            }

            return Parameterized(authScheme, newParameters, encoding)
        }

        override fun render(encoding: HeaderValueEncoding) =
            parameters.joinToString(", ", prefix = "$authScheme ") { "${it.name}=${it.value.encode(encoding)}" }

        /**
         * Tries to extract the first value of a parameter [name]. Returns null when not found.
         */
        fun parameter(name: String) = parameters.firstOrNull { it.name == name }?.value

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
            return Hash.combine(authScheme.toLowerCase(), parameters)
        }
    }

    /**
     * Encodes the header with a specified [encoding].
     */
    abstract fun render(encoding: HeaderValueEncoding): String

    /**
     * Encodes the header with the default [HeaderValueEncoding] for this header.
     */
    abstract fun render(): String

    /**
     * Encodes the header with the default [HeaderValueEncoding] for this header.
     */
    override fun toString(): String {
        return render()
    }

    companion object {
        /**
         * Generates an [AuthScheme.Basic] challenge as a [HttpAuthHeader].
         */
        fun basicAuthChallenge(realm: String, charset: Charset?) = Parameterized(
            AuthScheme.Basic, LinkedHashMap<String, String>().apply {
                put(Parameters.Realm, realm)
                if (charset != null) {
                    put(Parameters.Charset, charset.name)
                }
            }
        )

        /**
         * Generates an [AuthScheme.Digest] challenge as a [HttpAuthHeader].
         */
        fun digestAuthChallenge(
            realm: String,
            nonce: String = generateNonce(),
            domain: List<String> = emptyList(),
            opaque: String? = null,
            stale: Boolean? = null,
            algorithm: String = "MD5"
        ): Parameterized = Parameterized(AuthScheme.Digest, linkedMapOf<String, String>().apply {
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

    /**
     * Standard parameters for [Parameterized] [HttpAuthHeader].
     */
    @Suppress("KDocMissingDocumentation")
    object Parameters {
        const val Realm = "realm"
        const val Charset = "charset"

        const val OAuthCallback = "oauth_callback"
        const val OAuthConsumerKey = "oauth_consumer_key"
        const val OAuthNonce = "oauth_nonce"
        const val OAuthToken = "oauth_token"
        const val OAuthTokenSecret = "oauth_token_secret"
        const val OAuthVerifier = "oauth_verifier"
        const val OAuthSignatureMethod = "oauth_signature_method"
        const val OAuthTimestamp = "oauth_timestamp"
        const val OAuthVersion = "oauth_version"
        const val OAuthSignature = "oauth_signature"
        const val OAuthCallbackConfirmed = "oauth_callback_confirmed"
    }
}


private fun String.substringAfterMatch(result: MatchResult): String = drop(
    result.range.endInclusive + if (result.range.isEmpty()) 0 else 1
)

private fun String.unescapeIfQuoted() = when {
    startsWith('"') && endsWith('"') -> {
        removeSurrounding("\"").replace(escapeRegex) { it.value.takeLast(1) }
    }
    else -> this
}
