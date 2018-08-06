package io.ktor.client.request

import io.ktor.http.content.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.io.*

abstract class ClientUpgradeContent : OutgoingContent.NoContent() {
    private val content: ByteChannel = ByteChannel()

    val output: ByteWriteChannel get() = content

    suspend fun pipeTo(output: ByteWriteChannel) {
        content.joinTo(output, closeOnEnd = true)
    }

    abstract fun verify(headers: Headers)
}
