package io.ktor.server.servlet

import io.ktor.request.*
import io.ktor.util.*
import java.security.*

/**
 * Returns Java's JAAS Principal
 */
@KtorExperimentalAPI
val ApplicationRequest.javaSecurityPrincipal: Principal?
    get() = when (this) {
        is ServletApplicationRequest -> servletRequest.userPrincipal
        else -> null
    }
