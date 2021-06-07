/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.auth

import io.ktor.application.*
import io.ktor.http.auth.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.utils.io.charsets.*
import kotlin.text.Charsets

/**
 * Represents a Basic authentication provider
 * @property name is the name of the provider, or `null` for a default provider
 */
public class BasicAuthenticationProvider internal constructor(
    configuration: Configuration
) : AuthenticationProvider(configuration) {
    internal val realm: String = configuration.realm

    internal val charset: Charset? = configuration.charset

    internal val authenticationFunction = configuration.authenticationFunction

    /**
     * Basic auth configuration
     */
    public class Configuration internal constructor(name: String?) : AuthenticationProvider.Configuration(name) {
        internal var authenticationFunction: AuthenticationFunction<UserPasswordCredential> = {
            throw NotImplementedError(
                "Basic auth validate function is not specified. Use basic { validate { ... } } to fix."
            )
        }

        /**
         * Specifies realm to be passed in `WWW-Authenticate` header
         */
        public var realm: String = "Ktor Server"

        /**
         * Specifies the charset to be used. It can be either UTF_8 or null.
         * Setting `null` turns legacy mode on that actually means that ISO-8859-1 is used.
         */
        public var charset: Charset? = Charsets.UTF_8
            set(value) {
                if (value != null && value != Charsets.UTF_8) {
                    // https://tools.ietf.org/html/rfc7617#section-2.1
                    // 'The only allowed value is "UTF-8"; it is to be matched case-insensitively'
                    throw IllegalArgumentException("Basic Authentication charset can be either UTF-8 or null")
                }
                field = value
            }

        /**
         * Sets a validation function that will check given [UserPasswordCredential] instance and return [Principal],
         * or null if credential does not correspond to an authenticated principal
         */
        public fun validate(body: suspend ApplicationCall.(UserPasswordCredential) -> Principal?) {
            authenticationFunction = body
        }
    }
}

/**
 * Installs Basic Authentication mechanism
 */
public fun Authentication.Configuration.basic(
    name: String? = null,
    configure: BasicAuthenticationProvider.Configuration.() -> Unit
) {
    val provider = BasicAuthenticationProvider(BasicAuthenticationProvider.Configuration(name).apply(configure))
    val realm = provider.realm
    val charset = provider.charset
    val authenticate = provider.authenticationFunction

    provider.pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val credentials = call.request.basicAuthenticationCredentials(charset)
        val principal = credentials?.let { authenticate(call, it) }

        val cause = when {
            credentials == null -> AuthenticationFailedCause.NoCredentials
            principal == null -> AuthenticationFailedCause.InvalidCredentials
            else -> null
        }

        if (cause != null) {
            context.challenge(basicAuthenticationChallengeKey, cause) {
                call.respond(UnauthorizedResponse(HttpAuthHeader.basicAuthChallenge(realm, charset)))
                it.complete()
            }
        }
        if (principal != null) {
            context.principal(principal)
        }
    }

    register(provider)
}

/**
 * Retrieves Basic authentication credentials for this [ApplicationRequest]
 */
public fun ApplicationRequest.basicAuthenticationCredentials(charset: Charset? = null): UserPasswordCredential? {
    when (val authHeader = parseAuthorizationHeader()) {
        is HttpAuthHeader.Single -> {
            // Verify the auth scheme is HTTP Basic. According to RFC 2617, the authorization scheme should not be case
            // sensitive; thus BASIC, or Basic, or basic are all valid.
            if (!authHeader.authScheme.equals("Basic", ignoreCase = true)) {
                return null
            }

            val userPass = try {
                authHeader.blob.decodeBase64Bytes().toString(charset ?: Charsets.ISO_8859_1)
            } catch (e: Throwable) {
                return null
            }

            val colonIndex = userPass.indexOf(':')

            if (colonIndex == -1) {
                return null
            }

            return UserPasswordCredential(userPass.substring(0, colonIndex), userPass.substring(colonIndex + 1))
        }
        else -> return null
    }
}

private val basicAuthenticationChallengeKey: Any = "BasicAuth"
