package org.jetbrains.ktor.sessions

interface SessionSerializer<T : Any> {
    fun serialize(session: T): String
    fun deserialize(s: String): T
}