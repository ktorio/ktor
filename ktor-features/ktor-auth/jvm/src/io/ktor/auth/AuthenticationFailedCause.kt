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
     * @param message describing the cause of the authentication failure
     */
    open class Error(val message: String) : AuthenticationFailedCause() {
        @Suppress("UNUSED_PARAMETER")
        @Deprecated("Use message instead of cause.")
        constructor(vararg placeholder: Unit, cause: String) : this(message = cause)

        /**
         * Contains error message explaining the reason of auth failure.
         */
        @Deprecated("Use message instead.", ReplaceWith("message"))
        val cause: String get() = message
    }
}
