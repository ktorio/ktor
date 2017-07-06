package org.jetbrains.ktor.sessions

/**
 * Represents a session cookie transformation. Useful for such things like signing and encryption
 */
interface SessionTransportTransformer {
    fun transformRead(transportValue: String): String?
    fun transformWrite(transportValue: String): String
}

