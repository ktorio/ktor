package io.ktor.sessions

/**
 * Serializes session from and to [String]
 */
interface SessionSerializer {
    /**
     * Serializes a complex arbitrary object into a [String].
     */
    fun serialize(session: Any): String

    /**
     * Deserializes a complex arbitrary object from a [String].
     */
    fun deserialize(text: String): Any
}