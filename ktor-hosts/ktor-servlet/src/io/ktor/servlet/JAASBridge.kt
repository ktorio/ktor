package io.ktor.servlet

import io.ktor.request.*
import java.security.*

val ApplicationRequest.javaSecurityPrincipal: Principal?
    get() = when (this) {
        is ServletApplicationRequest -> servletRequest.userPrincipal
        else -> null
    }
