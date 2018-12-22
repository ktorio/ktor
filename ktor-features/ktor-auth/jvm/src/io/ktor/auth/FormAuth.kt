package io.ktor.auth

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*

/**
 * Represents a form-based authentication provider
 * @param name is the name of the provider, or `null` for a default provider
 */
class FormAuthenticationProvider(name: String?) : AuthenticationProvider(name) {
    internal var authenticationFunction: suspend ApplicationCall.(UserPasswordCredential) -> Principal? = { null }

    /**
     * POST parameter to fetch for a user name
     */
    var userParamName: String = "user"

    /**
     * POST parameter to fetch for a user password
     */
    var passwordParamName: String = "password"

    /**
     * A response to send back if authentication failed
     */
    var challenge: FormAuthChallenge = FormAuthChallenge.Unauthorized

    /**
     * Sets a validation function that will check given [UserPasswordCredential] instance and return [Principal],
     * or null if credential does not correspond to an authenticated principal
     */
    fun validate(body: suspend ApplicationCall.(UserPasswordCredential) -> Principal?) {
        authenticationFunction = body
    }
}

/**
 * Installs Form Authentication mechanism
 */
fun Authentication.Configuration.form(name: String? = null, configure: FormAuthenticationProvider.() -> Unit) {
    val provider = FormAuthenticationProvider(name).apply(configure)
    val userParamName = provider.userParamName
    val passwordParamName = provider.passwordParamName
    val validate = provider.authenticationFunction
    val challenge = provider.challenge

    provider.pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val postParameters = call.receiveOrNull<Parameters>()
        val username = postParameters?.get(userParamName)
        val password = postParameters?.get(passwordParamName)

        val credentials = if (username != null && password != null) UserPasswordCredential(username, password) else null
        val principal = credentials?.let { validate(call, it) }

        if (principal != null) {
            context.principal(principal)
        } else {
            val cause = if (credentials == null) AuthenticationFailedCause.NoCredentials else AuthenticationFailedCause.InvalidCredentials
            context.challenge(formAuthenticationChallengeKey, cause) {
                when (challenge) {
                    FormAuthChallenge.Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                    is FormAuthChallenge.Redirect -> call.respondRedirect(challenge.url(call, credentials))
                }
                it.complete()
            }
        }
    }
    register(provider)
}


/**
 * Specifies what to send back if form authentication fails.
 */
sealed class FormAuthChallenge {
    /**
     * Redirect to an URL provided by the given function.
     * @property url is a function receiving [ApplicationCall] and [UserPasswordCredential] and returning an URL to redirect to.
     */
    class Redirect(val url: ApplicationCall.(UserPasswordCredential?) -> String) : FormAuthChallenge()

    /**
     * Respond with [HttpStatusCode.Unauthorized].
     */
    object Unauthorized : FormAuthChallenge()
}

private val formAuthenticationChallengeKey: Any = "FormAuth"
