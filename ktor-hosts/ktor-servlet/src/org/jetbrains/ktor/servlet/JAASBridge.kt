package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import java.security.*

val ApplicationRequest.javaSecurityPrincipal: Principal?
    get() = when (this) {
        is ServletApplicationRequest -> servletRequest.userPrincipal
        else -> null
    }
