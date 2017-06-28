package org.jetbrains.ktor.sessions

/**
 * Serializes session from and to [String]
 */
interface SessionSerializer {
    fun serialize(session: Any): String
    fun deserialize(text: String): Any
}