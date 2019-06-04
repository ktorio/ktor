/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.auth

/**
 * Represents a cause for authentication challenge request
 */
sealed class AuthenticationFailedCause {
    /**
     * Represents a case when no credentials were provided
     */
    object NoCredentials : AuthenticationFailedCause()

    /**
     * Represents a case when invalid credentials were provided
     */
    object InvalidCredentials : AuthenticationFailedCause()

    /**
     * Represents a case when authentication mechanism failed
     * @param cause cause of authentication failure
     */
    open class Error(val cause: String) : AuthenticationFailedCause()
}
