/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.servlet

import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.security.*

/**
 * Returns Java's JAAS Principal
 */
public val Request.javaSecurityPrincipal: Principal?
    get() = when (this) {
        is RoutingRequest -> call.applicationCall.request.javaSecurityPrincipal
        is ServletApplicationRequest -> servletRequest.userPrincipal
        is RoutingApplicationRequest -> engineRequest.javaSecurityPrincipal
        else -> null
    }
