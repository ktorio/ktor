package io.ktor.http.content

import io.ktor.http.*

/**
 * Represents a simple status code response with no content
 * @param value - status code to be sent
 */
class HttpStatusCodeContent(private val value: HttpStatusCode) : OutgoingContent.NoContent() {
    override val status: HttpStatusCode
        get() = value
}
