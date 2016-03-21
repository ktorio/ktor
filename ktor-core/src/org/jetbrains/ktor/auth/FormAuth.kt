package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*

fun PipelineContext<ApplicationCall>.formAuth(userParamName: String = "user", passwordParamName: String = "password") {
    extractCredentials {
        val username = parameters[userParamName]
        val password = parameters[passwordParamName]

        if (username != null && password != null) UserPasswordCredential(username, password) else null
    }
}

fun AuthenticationProcedure.formAuthentication(userParamName: String = "user", passwordParamName: String = "password", validate: (UserPasswordCredential) -> Principal?) {
    authenticate { context ->
        val username = context.call.parameters[userParamName]
        val password = context.call.parameters[passwordParamName]

        val credentials = if (username != null && password != null) UserPasswordCredential(username, password) else null
        val principal = credentials?.let(validate)

        if (principal != null) {
            context.principal(principal)
        } else {
            context.challenge {
                context.call.respondStatus(HttpStatusCode.Forbidden)
            }
        }

    }
}
