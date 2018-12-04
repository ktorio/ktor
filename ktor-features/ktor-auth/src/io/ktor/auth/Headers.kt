package io.ktor.auth

import io.ktor.http.*
import io.ktor.request.*
import io.ktor.util.*
import java.nio.charset.*
import java.util.*
import kotlin.collections.LinkedHashMap

/**
 * Contains the standard auth schemes.
 */
object AuthScheme {
    /**
     * Basic Authentication described in the RFC-7617
     *
     * ```
     * response = base64("$user:$password")
     * ```
     *
     * see https://tools.ietf.org/html/rfc7617)
     */
    const val Basic = "Basic"

    /**
     * Digest Authentication described in the RFC-2069:
     *
     * ```
     * HA1 = MD5("$username:$realm:$password") // What's usually stored
     * HA2 = MD5("$method:$digestURI")
     * response = MD5("$HA1:$nonce:$HA2") // The client and the server sends and checks this.
     * ```
     *
     * see https://tools.ietf.org/html/rfc2069
     */
    const val Digest = "Digest"

    /**
     * Described in the RFC-4599:
     *
     * see https://www.ietf.org/rfc/rfc4559.txt
     */
    const val Negotiate = "Negotiate"

    /**
     * OAuth Authentication described in the RFC-6749:
     *
     * see https://tools.ietf.org/html/rfc6749
     */
    const val OAuth = "OAuth"

    @Suppress("KDocMissingDocumentation", "unused")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    fun getBasic(): String = Basic

    @Suppress("KDocMissingDocumentation", "unused")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    fun getDigest(): String = Digest

    @Suppress("KDocMissingDocumentation", "unused")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    fun getNegotiate(): String = Negotiate
}

/**
 * Describes how a header should be encoded.
 */
enum class HeaderValueEncoding {
    /**
     * The header will be quoted only when required.
     */
    QUOTED_WHEN_REQUIRED,

    /**
     * The header will be quoted always.
     */
    QUOTED_ALWAYS,

    /**
     * The header will be URI-encoded as described in the RFC-3986:
     *
     * see https://tools.ietf.org/html/rfc3986#page-12
     */
    URI_ENCODE
}

/**
 * Parses an authorization header from a [ApplicationRequest] returning a [HttpAuthHeader].
 */
fun ApplicationRequest.parseAuthorizationHeader(): HttpAuthHeader? = authorization()?.let {
    parseAuthorizationHeader(it)
}

private val token68Pattern = "[a-zA-Z0-9\\-._~+/]+=*".toRegex()
private val authSchemePattern = "\\S+".toRegex()
private const val valuePatternPart = """("((\\.)|[^\\"])*")|[^\s,]*"""
private val parameterPattern = "\\s*,?\\s*($token68Pattern)\\s*=\\s*($valuePatternPart)\\s*,?\\s*".toRegex()

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

    val parameters = parameterPattern.findAll(remaining)
            .associateBy({ it.groups[1]!!.value }, { it.groups[2]!!.value.unescapeIfQuoted() })

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
            return Objects.hash(authScheme.toLowerCase(), blob.toLowerCase())
        }
    }

    /**
     * Describes a parameterized authentication header that is represented by a set of [parameters] encoded with [encoding].
     * @property parameters a list of auth parameters
     * @property encoding parameters encoding method, one of [HeaderValueEncoding]
     */
    class Parameterized(authScheme: String, val parameters: List<HeaderValueParam>, val encoding: HeaderValueEncoding = HeaderValueEncoding.QUOTED_WHEN_REQUIRED) : HttpAuthHeader(authScheme) {
        constructor(authScheme: String, parameters: Map<String, String>, encoding: HeaderValueEncoding = HeaderValueEncoding.QUOTED_WHEN_REQUIRED) : this(authScheme, parameters.entries.map { HeaderValueParam(it.key, it.value) }, encoding)

        init {
            parameters.forEach {
                require(it.name.matches(token68Pattern)) { "parameter name should be a token but it is ${it.name}" }
            }
        }

        /**
         * Copies this [Parameterized] appending a new parameter [name] [value].
         */
        fun withParameter(name: String, value: String) = Parameterized(authScheme, this.parameters + HeaderValueParam(name, value), encoding)

        override fun render(encoding: HeaderValueEncoding) = parameters.joinToString(", ", prefix = "$authScheme ") { "${it.name}=${it.value.encode(encoding)}" }

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
            return Objects.hash(authScheme.toLowerCase(), parameters)
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
                    put(Parameters.Charset, charset.name())
                }
            }
        )

        /**
         * Generates an [AuthScheme.Digest] challenge as a [HttpAuthHeader].
         */
        fun digestAuthChallenge(realm: String, nonce: String = generateNonce(), domain: List<String> = emptyList(), opaque: String? = null, stale: Boolean? = null, algorithm: String = "MD5")
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

private fun String.substringAfterMatch(mr: MatchResult) = drop(mr.range.endInclusive + if (mr.range.isEmpty()) 0 else 1)
private val escapeRegex = "\\\\.".toRegex()
private fun String.unescapeIfQuoted() = when {
    startsWith('"') && endsWith('"') -> removeSurrounding("\"").replace(escapeRegex) { it.value.takeLast(1) }
    else -> this
}
