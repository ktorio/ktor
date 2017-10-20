package io.ktor.content

import io.ktor.http.*
import io.ktor.util.*

class ByteArrayContent(private val bytes: ByteArray) : FinalContent.ByteArrayContent() {
    override val headers by lazy {
        ValuesMap.build(true) {
            contentLength(bytes.size.toLong())
        }
    }

    override fun bytes(): ByteArray = bytes
}
