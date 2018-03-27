package io.ktor.auth

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.sessions.*
import kotlin.reflect.*

/**
 * Represents a session-based authentication provider
 * @param name is the name of the provider, or `null` for a default provider
 */
class SessionAuthenticationProvider<T : Any>(name: String?, val type: KClass<T>) : AuthenticationProvider(name) {
    var validator: (ApplicationCall, T) -> Principal? = UninitializedValidator
        internal set

    /**
     * A response to send back if authentication failed
     */
    var challenge: SessionAuthChallenge<T> = SessionAuthChallenge.Default

    /**
     * Sets a validation function that will check given [T] session instance and return [Principal],
     * or null if the session does not correspond to an authenticated principal
     */
    fun validate(block: (ApplicationCall, T) -> Principal?) {
        check(validator === UninitializedValidator) { "Only one validator could be registered" }
        validator = block
    }

    @Deprecated("Internal API")
    fun verifyConfiguration() {
        check(validator !== UninitializedValidator) { "It should be a validator supplied to a session auth provider" }
    }

    companion object {
        private val UninitializedValidator: (ApplicationCall, Any) -> Principal? = { _, _ ->
            error("It should be a validator supplied to a session auth provider")
        }
    }
}


/**
 * Provides ability to authenticate users via sessions. It only works if [T] session type denotes [Principal] as well
 * otherwise use full [session] with lambda function with [SessionAuthenticationProvider.validate] configuration
 */
inline fun <reified T : Principal> Authentication.Configuration.session(name: String? = null, challenge: SessionAuthChallenge<T> = SessionAuthChallenge.Default) {
    session<T>(name) {
        this.challenge = challenge
        validate { _, session -> session }
    }
}

/**
 * Provides ability to authenticate users via sessions. It is important to have
 * specify [SessionAuthenticationProvider.validate] and [SessionAuthenticationProvider.challenge] in the lambda
 * to get it work property
 */
inline fun <reified T : Any> Authentication.Configuration.session(name: String? = null, configure: SessionAuthenticationProvider<T>.() -> Unit) {
    val provider = SessionAuthenticationProvider(name, T::class).apply(configure)

    @Suppress("DEPRECATION") // suppress internal API usage
    provider.verifyConfiguration()

    provider.pipeline.intercept(AuthenticationPipeline.CheckAuthentication) { context ->
        val session = call.sessions.get<T>()
        val principal = session?.let { provider.validator(call, it) }

        if (principal != null) {
            context.principal(principal)
        } else {
            val cause = if (session == null) AuthenticationFailedCause.NoCredentials else AuthenticationFailedCause.InvalidCredentials
            if (provider.challenge != SessionAuthChallenge.Ignore) {
                context.challenge(SessionAuthChallengeKey, cause) {
                    val challenge = provider.challenge
                    println("challenge is $challenge")
                    when (challenge) {
                        is SessionAuthChallenge.Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                        is SessionAuthChallenge.Redirect -> call.respondRedirect(challenge.url(call, session))
                    }
                    it.complete()
                }
            }
        }
    }

    register(provider)
}

/**
 * Specifies what to send back if form authentication fails.
 */
sealed class SessionAuthChallenge<in T : Any> {
    /**
     * Redirect to an URL provided by the given function.
     * @property url is a function receiving [ApplicationCall] and [UserPasswordCredential] and returning an URL to redirect to.
     */
    class Redirect<in T : Any>(val url: (ApplicationCall, T?) -> String) : SessionAuthChallenge<T>()

    /**
     * Respond with [HttpStatusCode.Unauthorized].
     */
    object Unauthorized : SessionAuthChallenge<Any>()

    /**
     * Does nothing so other authentication methods could provide their challenges.
     * This is the  default and recommended way
     */
    object Ignore : SessionAuthChallenge<Any>()

    companion object {
        val Default = Ignore
    }
}

const val SessionAuthChallengeKey = "SessionAuth"