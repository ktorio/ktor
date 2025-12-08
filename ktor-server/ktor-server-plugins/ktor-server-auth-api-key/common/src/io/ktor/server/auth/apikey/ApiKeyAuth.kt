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
 */
public fun AuthenticationConfig.apiKey(
    name: String? = null,
    configure: ApiKeyAuthenticationProvider.Configuration.() -> Unit,
) {
    val provider = ApiKeyAuthenticationProvider(ApiKeyAuthenticationProvider.Configuration(name).apply(configure))
    register(provider)
}

/**
 * Alias for function signature that is invoked when verifying header.
 */
public typealias ApiKeyAuthenticationFunction = suspend ApplicationCall.(String) -> Any?

/**
 * Alias for function signature that is called when authentication fails.
 */
public typealias ApiKeyAuthChallengeFunction = suspend (ApplicationCall) -> Unit

/**
 * Represents an API Key authentication provider.
 */
public class ApiKeyAuthenticationProvider internal constructor(
    configuration: Configuration,
) : AuthenticationProvider(configuration) {

    private val headerName: String = configuration.headerName
    private val authenticationFunction = configuration.authenticationFunction
    private val challengeFunction = configuration.challengeFunction
    private val authScheme = configuration.authScheme

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val apiKey = context.call.request.header(headerName)
        val principal = apiKey?.let { authenticationFunction(context.call, it) }

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
     * Api key auth configuration.
     */
    public class Configuration internal constructor(name: String?) : Config(name) {

        internal lateinit var authenticationFunction: ApiKeyAuthenticationFunction

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
         * Sets a validation function that will check given API key retrieved from [headerName] instance and return [Principal],
         * or null if credential does not correspond to an authenticated principal.
         */
        public fun validate(body: suspend ApplicationCall.(String) -> Any?) {
            authenticationFunction = body
        }

        /**
         * A response to send back if authentication failed.
         */
        public fun challenge(body: ApiKeyAuthChallengeFunction) {
            challengeFunction = body
        }
    }
}
