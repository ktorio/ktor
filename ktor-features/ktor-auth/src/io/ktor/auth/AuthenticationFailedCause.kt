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