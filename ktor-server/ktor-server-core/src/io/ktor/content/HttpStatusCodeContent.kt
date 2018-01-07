package io.ktor.content

import io.ktor.http.*
import io.ktor.util.*

class HttpStatusCodeContent(private val value: HttpStatusCode) : OutgoingContent.NoContent() {
    override val status: HttpStatusCode
        get() = value
}