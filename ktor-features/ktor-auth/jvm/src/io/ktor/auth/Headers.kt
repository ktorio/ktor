/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.auth

import io.ktor.http.auth.*
import io.ktor.request.*

/**
 * Parses an authorization header from a [ApplicationRequest] returning a [HttpAuthHeader].
 */
public fun ApplicationRequest.parseAuthorizationHeader(): HttpAuthHeader? = authorization()?.let {
    parseAuthorizationHeader(it)
}
