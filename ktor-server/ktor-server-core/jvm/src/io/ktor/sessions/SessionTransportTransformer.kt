package io.ktor.sessions

/**
 * Represents a session cookie transformation. Useful for such things like signing and encryption
 */
interface SessionTransportTransformer {
    /**
     * Un-apply a transformation for [transportValue] representing a transformed session.
     * Returns null if it fails.
     *
     * @return Untransformed value or null
     */
    fun transformRead(transportValue: String): String?

    /**
     * Apply a transformation for [transportValue] representing a session.
     *
     * @return Transformed value
     */
    fun transformWrite(transportValue: String): String
}

/**
 * Un-applies a list of session transformations to a [cookieValue] representing a transformed session string.
 * If any of the unapplication of transformations fail returning a null, this function also returns null.
 *
 * @return A string representing the original session contents.
 */
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

/**
 * Applies a list of session transformations to a [value] representing session string.
 *
 * @return A string containing all the transformations applied.
 */
fun List<SessionTransportTransformer>.transformWrite(value: String): String {
    return fold(value) { it, transformer -> transformer.transformWrite(it) }
}