/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.apikey

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*

/**
 * Installs API Key authentication mechanism.
 *
 * @param name optional name for this authentication provider. If not specified, a default name will be used.
 * @param configure configuration block for setting up the API Key authentication provider.
 */
public fun AuthenticationConfig.apiKey(
    name: String? = null,
    configure: ApiKeyAuthenticationProvider.Configuration.() -> Unit,
) {
    val provider = ApiKeyAuthenticationProvider(ApiKeyAuthenticationProvider.Configuration(name).apply(configure))
    register(provider)
}

/**
 * Alias for function signature that is invoked when verifying an API key from a header.
 *
 * The function receives the API key as a [String] parameter and should return an arbitrary
 * principal object (for example, a user or service identity) if authentication succeeds,
 * or null if authentication fails.
 */
public typealias ApiKeyAuthenticationFunction = suspend ApplicationCall.(String) -> Any?

/**
 * Alias for function signature that is called when authentication fails.
 */
public typealias ApiKeyAuthChallengeFunction = suspend (ApplicationCall) -> Unit

/**
 * Represents an API Key authentication provider.
 *
 * This provider extracts an API key from a specified header and validates it using
 * the configured authentication function.
 *
 * @param configuration the configuration for this authentication provider.
 */
public class ApiKeyAuthenticationProvider internal constructor(
    configuration: Configuration,
) : AuthenticationProvider(configuration) {

    private val headerName: String = configuration.headerName
    private val authenticationFunction = configuration.authenticationFunction
    private val challengeFunction = configuration.challengeFunction
    private val authScheme = configuration.authScheme

    init {
        requireNotNull(configuration.authenticationFunction) {
            "API Key authentication requires a validate() function to be configured"
        }
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val apiKey = context.call.request.header(headerName)
        val principal = apiKey?.let { authenticationFunction!!(context.call, it) }

        val cause = when {
            apiKey == null -> AuthenticationFailedCause.NoCredentials
            principal == null -> AuthenticationFailedCause.InvalidCredentials
            else -> null
        }

        if (cause != null) {
            context.challenge(authScheme, cause) { challenge, call ->
                challengeFunction(call)
                challenge.complete()
            }
        }
        if (principal != null) {
            context.principal(principal)
        }
    }

    /**
     * Configuration for API Key authentication.
     *
     * @param name optional name for this authentication provider.
     */
    public class Configuration internal constructor(name: String?) : Config(name) {

        internal var authenticationFunction: ApiKeyAuthenticationFunction? = null

        internal var challengeFunction: ApiKeyAuthChallengeFunction = { call ->
            call.respond(HttpStatusCode.Unauthorized)
        }

        /**
         * Name of the scheme used when challenge fails, see [AuthenticationContext.challenge].
         */
        public var authScheme: String = "apiKey"

        /**
         * Name of the header that will be used as a source for the api key.
         */
        public var headerName: String = "X-Api-Key"

        /**
         * Sets a validation function that will check the given API key retrieved from the [headerName] header
         * and return an arbitrary principal object (for example, a user or service identity) if authentication
         * succeeds, or null if authentication fails.
         *
         * @param body the validation function that receives the API key as a [String] parameter and returns
         * an arbitrary principal object or null.
         */
        public fun validate(body: ApiKeyAuthenticationFunction) {
            authenticationFunction = body
        }

        /**
         * A response to send back if authentication failed.
         *
         * @param body the challenge function that handles authentication failures.
         */
        public fun challenge(body: ApiKeyAuthChallengeFunction) {
            challengeFunction = body
        }
    }
}
