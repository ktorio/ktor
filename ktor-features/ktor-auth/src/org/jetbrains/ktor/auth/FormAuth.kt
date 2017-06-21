package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*

val FormAuthKey: Any = "FormAuth"
fun AuthenticationPipeline.formAuthentication(userParamName: String = "user",
                                              passwordParamName: String = "password",
                                              challenge: FormAuthChallenge = FormAuthChallenge.Unauthorized,
                                              validate: (UserPasswordCredential) -> Principal?) {
    intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val postParameters = call.request.tryReceive<ValuesMap>()
        val username = postParameters?.get(userParamName)
        val password = postParameters?.get(passwordParamName)

        val credentials = if (username != null && password != null) UserPasswordCredential(username, password) else null
        val principal = credentials?.let(validate)

        if (principal != null) {
            context.principal(principal)
        } else {
            context.challenge(FormAuthKey, if (credentials == null) NotAuthenticatedCause.NoCredentials else NotAuthenticatedCause.InvalidCredentials) {
                it.success()

                when (challenge) {
                    FormAuthChallenge.Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
                    is FormAuthChallenge.Redirect -> call.respondRedirect(challenge.url(call, credentials))
                }
            }
        }
    }
}

sealed class FormAuthChallenge {
    class Redirect(val url: (ApplicationCall, UserPasswordCredential?) -> String) : FormAuthChallenge()
    object Unauthorized : FormAuthChallenge()
}