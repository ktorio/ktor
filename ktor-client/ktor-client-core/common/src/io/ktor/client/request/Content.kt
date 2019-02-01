package io.ktor.client.request

import io.ktor.http.content.*
import io.ktor.http.*
import kotlinx.coroutines.io.*

abstract class ClientUpgradeContent : OutgoingContent.NoContent() {
    private val content: ByteChannel by lazy { ByteChannel() }

    val output: ByteWriteChannel get() = content

    suspend fun pipeTo(output: ByteWriteChannel) {
        content.copyAndClose(output)
    }

    abstract fun verify(headers: Headers)
}
