package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.request.*
import java.security.*

val ApplicationRequest.javaSecurityPrincipal: Principal?
    get() = when (this) {
        is ServletApplicationRequest -> servletRequest.userPrincipal
        else -> null
    }
