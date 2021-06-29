/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.util.pipeline.*
import kotlin.reflect.*

/**
 * Represents a session-based authentication provider
 * @property type of session
 * @property challenge to be used if there is no session
 * @property validator applied to an application all and session providing a [Principal]
 */
public class SessionAuthenticationProvider<T : Any> private constructor(
    config: Configuration<T>
) : AuthenticationProvider(config) {
    public val type: KClass<T> = config.type

    @PublishedApi
    internal val challenge: SessionAuthChallengeFunction<T> = config.challengeFunction

    @PublishedApi
    internal val validator: AuthenticationFunction<T> = config.validator

    /**
     * Session auth configuration
     */
    public class Configuration<T : Any> @PublishedApi internal constructor(
        name: String?,
        internal val type: KClass<T>
    ) : AuthenticationProvider.Configuration(name) {
        internal var validator: AuthenticationFunction<T> = UninitializedValidator

        internal var challengeFunction: SessionAuthChallengeFunction<T> = {
        }

        /**
         * A response to send back if authentication failed
         */
        public fun challenge(block: SessionAuthChallengeFunction<T>) {
            challengeFunction = block
        }

        /**
         * A response to send back if authentication failed
         */
        public fun challenge(redirectUrl: String) {
            challenge {
                call.respondRedirect(redirectUrl)
            }
        }

        /**
         * A response to send back if authentication failed
         */
        public fun challenge(redirect: Url) {
            challenge(redirect.toString())
        }

        /**
         * Sets a validation function that will check given [T] session instance and return [Principal],
         * or null if the session does not correspond to an authenticated principal
         */
        public fun validate(block: suspend ApplicationCall.(T) -> Principal?) {
            check(validator === UninitializedValidator) { "Only one validator could be registered" }
            validator = block
        }

        private fun verifyConfiguration() {
            check(validator !== UninitializedValidator) {
                "It should be a validator supplied to a session auth provider"
            }
        }

        @PublishedApi
        internal fun buildProvider(): SessionAuthenticationProvider<T> {
            verifyConfiguration()
            return SessionAuthenticationProvider(this)
        }
    }

    public companion object {
        private val UninitializedValidator: suspend ApplicationCall.(Any) -> Principal? = {
            error("It should be a validator supplied to a session auth provider")
        }
    }
}

/**
 * Provides ability to authenticate users via sessions. It only works if [T] session type denotes [Principal] as well
 * otherwise use full [session] with lambda function with [SessionAuthenticationProvider.Configuration.validate] configuration
 */
public inline fun <reified T : Principal> Authentication.Configuration.session(
    name: String? = null
) {
    session<T>(name) {
        validate { session -> session }
    }
}

/**
 * Provides ability to authenticate users via sessions. It is important to have
 * specified [SessionAuthenticationProvider.Configuration.validate] and
 * [SessionAuthenticationProvider.Configuration.challenge] in the lambda
 * to get it work property
 */
public inline fun <reified T : Any> Authentication.Configuration.session(
    name: String? = null,
    configure: SessionAuthenticationProvider.Configuration<T>.() -> Unit
) {
    val provider = SessionAuthenticationProvider.Configuration(name, T::class).apply(configure).buildProvider()

    provider.pipeline.intercept(AuthenticationPipeline.CheckAuthentication) { context ->
        val session = call.sessions.get<T>()
        val principal = session?.let { provider.validator(call, it) }

        if (principal != null) {
            context.principal(principal)
        } else {
            val cause =
                if (session == null) AuthenticationFailedCause.NoCredentials
                else AuthenticationFailedCause.InvalidCredentials

            context.challenge(SessionAuthChallengeKey, cause) {
                provider.challenge(this, principal)
                if (!it.completed && call.response.status() != null) {
                    it.complete()
                }
            }
        }
    }

    register(provider)
}

/**
 * Specifies what to send back if session authentication fails.
 */
public typealias SessionAuthChallengeFunction<T> = suspend PipelineContext<*, ApplicationCall>.(T?) -> Unit

/**
 * A key used to register auth challenge
 */
public const val SessionAuthChallengeKey: String = "SessionAuth"
