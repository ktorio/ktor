package io.ktor.client.engine.mock

import io.ktor.http.content.*
import io.ktor.util.*
import kotlinx.coroutines.io.*
import kotlinx.io.core.*

@Suppress("KDocMissingDocumentation")
@KtorExperimentalAPI
suspend fun OutgoingContent.toByteArray(): ByteArray = when (this) {
    is OutgoingContent.ByteArrayContent -> bytes()
    is OutgoingContent.ReadChannelContent -> readFrom().toByteArray()
    is OutgoingContent.WriteChannelContent -> {
        ByteChannel().also { writeTo(it) }.toByteArray()
    }
    else -> ByteArray(0)
}

@Suppress("KDocMissingDocumentation")
@KtorExperimentalAPI
suspend fun OutgoingContent.toByteReadPacket(): ByteReadPacket = when (this) {
    is OutgoingContent.ByteArrayContent ->  ByteReadPacket(bytes())
    is OutgoingContent.ReadChannelContent -> readFrom().readRemaining()
    is OutgoingContent.WriteChannelContent -> {
        ByteChannel().also { writeTo(it) }.readRemaining()
    }
    else -> ByteReadPacket.Empty
}
