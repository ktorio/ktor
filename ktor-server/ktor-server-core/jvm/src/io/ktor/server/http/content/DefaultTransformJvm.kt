/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.ioDispatcher
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.*

/**
 * Default outgoing content transformation
 */
internal actual fun platformTransformDefaultContent(
    call: ApplicationCall,
    value: Any
): OutgoingContent? = when (value) {
    is URIFileContent -> {
        when (value.uri.scheme) {
            "file" -> File(value.uri).let { file ->
                LocalFileContent(file, ContentType.defaultForFile(file), call.application.ioDispatcher)
            }

            else -> null
        }
    }

    is InputStream -> object : OutgoingContent.ReadChannelContent() {
        override fun readFrom(): ByteReadChannel = value.toByteReadChannel(context = call.application.ioDispatcher)
    }

    else -> null
}
