package org.jetbrains.ktor.sessions

/**
 * Represents a session cookie transformation. Useful for such things like signing and encryption
 */
interface SessionCookieTransformer {
    fun transformRead(sessionCookieValue: String): String?
    fun transformWrite(sessionCookieValue: String): String
}

