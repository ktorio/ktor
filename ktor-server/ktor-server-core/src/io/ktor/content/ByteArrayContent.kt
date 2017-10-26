package io.ktor.content

import io.ktor.http.*
import io.ktor.util.*

/**
 * Implementation of the [OutgoingContent.ByteArrayContent] for sending array of bytes
 */
class ByteArrayContent(private val bytes: ByteArray) : OutgoingContent.ByteArrayContent() {
    override val headers by lazy {
        ValuesMap.build(true) {
            contentLength(bytes.size.toLong())
        }
    }

    override fun bytes(): ByteArray = bytes
}
