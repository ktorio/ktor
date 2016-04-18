package org.jetbrains.ktor.auth

import org.jetbrains.ktor.http.*

val FormAuthKey: Any = "FormAuth"
fun AuthenticationProcedure.formAuthentication(userParamName: String = "user", passwordParamName: String = "password", validate: (UserPasswordCredential) -> Principal?) {
    intercept { context ->
        val username = context.call.parameters[userParamName]
        val password = context.call.parameters[passwordParamName]

        val credentials = if (username != null && password != null) UserPasswordCredential(username, password) else null
        val principal = credentials?.let(validate)

        if (principal != null) {
            context.principal(principal)
        } else {
            context.challenge(FormAuthKey, if (credentials == null) NotAuthenticatedCause.NoCredentials else NotAuthenticatedCause.InvalidCredentials) {
                it.success()
                context.call.respondStatus(HttpStatusCode.Unauthorized)
            }
        }
    }
}
