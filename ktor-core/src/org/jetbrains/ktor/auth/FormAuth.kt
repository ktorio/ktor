package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*

fun <C: ApplicationCall> PipelineContext<C>.formAuth(userParamName: String = "user", passwordParamName: String = "password") {
    extractCredentials {
        val username = request.parameter(userParamName)
        val password = request.parameter(passwordParamName)

        if (username != null && password != null) UserPasswordCredential(username, password) else null
    }
}
