/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

/**
 * Represents a cause for authentication challenge request
 */
public sealed class AuthenticationFailedCause {
    /**
     * Represents a case when no credentials were provided
     */
    public object NoCredentials : AuthenticationFailedCause()

    /**
     * Represents a case when invalid credentials were provided
     */
    public object InvalidCredentials : AuthenticationFailedCause()

    /**
     * Represents a case when authentication mechanism failed
     * @param message describing the cause of the authentication failure
     */
    public open class Error(public val message: String) : AuthenticationFailedCause() {
        @Suppress("UNUSED_PARAMETER")
        @Deprecated("Use message instead of cause.", level = DeprecationLevel.ERROR)
        public constructor(vararg placeholder: Unit, cause: String) : this(message = cause)

        /**
         * Contains error message explaining the reason of auth failure.
         */
        @Deprecated("Use message instead.", ReplaceWith("message"), level = DeprecationLevel.ERROR)
        public val cause: String get() = message
    }
}
