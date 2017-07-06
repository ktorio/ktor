package org.jetbrains.ktor.sessions

/**
 * Represents a session cookie transformation. Useful for such things like signing and encryption
 */
interface SessionTransportTransformer {
    fun transformRead(transportValue: String): String?
    fun transformWrite(transportValue: String): String
}

fun List<SessionTransportTransformer>.transformRead(cookieValue: String?): String? {
    var value = cookieValue
    for (t in this) {
        if (value == null) {
            break
        }
        value = t.transformRead(value)
    }
    return value
}

fun List<SessionTransportTransformer>.transformWrite(value: String): String {
    return fold(value) { it, transformer -> transformer.transformWrite(it) }
}