package io.ktor.auth

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*

/**
 * Installs Form Authentication mechanism into [AuthenticationPipeline]
 */
fun AuthenticationPipeline.formAuthentication(userParamName: String = "user",
                                                           passwordParamName: String = "password",
                                                           challenge: FormAuthChallenge = FormAuthChallenge.Unauthorized,
                                                           validate: (UserPasswordCredential) -> Principal?) {
    intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val postParameters = call.receiveOrNull<Parameters>()
        val username = postParameters?.get(userParamName)
        val password = postParameters?.get(passwordParamName)

        val credentials = if (username != null && password != null) UserPasswordCredential(username, password) else null
        val principal = credentials?.let(validate)

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
}

sealed class FormAuthChallenge {
    class Redirect(val url: (ApplicationCall, UserPasswordCredential?) -> String) : FormAuthChallenge()
    object Unauthorized : FormAuthChallenge()
}

private val formAuthenticationChallengeKey: Any = "FormAuth"
