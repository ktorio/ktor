package org.jetbrains.ktor.auth

import org.jetbrains.ktor.http.*

object AuthScheme {
    val Basic = "Basic"
    val Digest = "Digest"
    val Negotiate = "Negotiate"
    val OAuth = "OAuth"
}

enum class HeaderValueEncoding {
    QUOTED,
    URI_ENCODE
}

sealed class HttpAuthHeader(val authScheme: String) {
    init {
        require(authScheme.matches(token68Pattern)) { "invalid authScheme value: it should be token" }
    }

    class Single(authScheme: String, val blob: String) : HttpAuthHeader(authScheme) {
        init {
            require(blob.matches(token68Pattern)) { "invalid blob value: it should be token68 but it is $blob" }
        }

        override fun render(encoding: HeaderValueEncoding) = "$authScheme $blob"
    }

    class Parameterized(authScheme: String, val parameters: List<HeaderValueParam>) : HttpAuthHeader(authScheme) {
        constructor(authScheme: String, parameters: Map<String, String>) : this(authScheme, parameters.entries.map { HeaderValueParam(it.key, it.value) })

        init {
            parameters.forEach {
                require(it.name.matches(token68Pattern)) { "parameter name should be a token but it is ${it.name}" }
            }
        }

        fun withParameter(name: String, value: String) = Parameterized(authScheme, this.parameters + HeaderValueParam(name, value))
        override fun render(encoding: HeaderValueEncoding) = parameters.joinToString(", ", prefix = "$authScheme ") { "${it.name}=${it.value.encode(encoding)}" }
        fun parameter(name: String) = parameters.singleOrNull { it.name == name }?.value

        private fun String.encode(encoding: HeaderValueEncoding) = when (encoding) {
            HeaderValueEncoding.QUOTED -> escapeIfNeeded()
            HeaderValueEncoding.URI_ENCODE -> encodeURL()
        }
    }

    abstract fun render(encoding: HeaderValueEncoding): String

    companion object {
        fun basicAuthChallenge(realm: String) = Parameterized(AuthScheme.Basic, mapOf(Parameters.Realm to realm))
    }

    object Parameters {
        val Realm = "realm"

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

private val token68Pattern = "[a-zA-Z0-9\\-\\._~+/]+=*".toRegex()
