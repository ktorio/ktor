/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.logging

import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

internal suspend fun OutgoingContent.observe(log: ByteWriteChannel): OutgoingContent = when (this) {
    is OutgoingContent.ByteArrayContent -> {
        log.writeFully(bytes())
        log.close()
        this
    }
    is OutgoingContent.ReadChannelContent -> {
        val responseChannel = ByteChannel()
        val content = readFrom()

        content.copyToBoth(log, responseChannel)
        LoggingContent(responseChannel)
    }
    is OutgoingContent.WriteChannelContent -> {
        val responseChannel = ByteChannel()
        val content = toReadChannel()
        content.copyToBoth(log, responseChannel)
        LoggingContent(responseChannel)
    }
    else -> {
        log.close()
        this
    }
}

internal class LoggingContent(private val channel: ByteReadChannel) : OutgoingContent.ReadChannelContent() {
    override fun readFrom(): ByteReadChannel = channel
}

private fun OutgoingContent.WriteChannelContent.toReadChannel(
): ByteReadChannel = GlobalScope.writer(Dispatchers.Unconfined) {
    writeTo(channel)
}.channel
