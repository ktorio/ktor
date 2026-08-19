/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import io.ktor.utils.io.streams.*
import kotlinx.io.*
import java.io.*

internal actual suspend fun PipelineContext<Any, PipelineCall>.defaultPlatformTransformations(
    query: Any
): Any? {
    val channel = query as? ByteReadChannel ?: return null

    return when (call.receiveType.type) {
        InputStream::class -> receiveGuardedInputStream(channel)
        else -> null
    }
}

internal actual fun Source.readTextWithCustomCharset(charset: Charset): String =
    inputStream().reader(charset).readText()

private fun receiveGuardedInputStream(channel: ByteReadChannel): InputStream {
    return channel.toInputStream()
}
