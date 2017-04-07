package org.jetbrains.ktor.content

import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*

class ByteArrayContent(private val bytes: ByteArray) : FinalContent.ByteArrayContent() {
    override val headers by lazy {
        ValuesMap.build(true) {
            contentLength(bytes.size.toLong())
        }
    }

    override fun bytes(): ByteArray = bytes
}
