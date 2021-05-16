/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*

/**
 * Set multipart HTTP request body
 */
public fun TestApplicationRequest.setBody(boundary: String, parts: List<PartData>) {
    bodyChannel = writer(Dispatchers.IO) {
        if (parts.isEmpty()) return@writer

        try {
            append("\r\n\r\n")
            parts.forEach {
                append("--$boundary\r\n")
                for ((key, values) in it.headers.entries()) {
                    append("$key: ${values.joinToString(";")}\r\n")
                }
                append("\r\n")
                append(
                    when (it) {
                        is PartData.FileItem -> {
                            it.provider().asStream().copyTo(channel.toOutputStream())
                            ""
                        }
                        is PartData.BinaryItem -> {
                            it.provider().asStream().copyTo(channel.toOutputStream())
                            ""
                        }
                        is PartData.FormItem -> it.value
                    }
                )
                append("\r\n")
            }

            append("--$boundary--\r\n")
        } finally {
            parts.forEach { it.dispose() }
        }
    }.channel
}

private suspend fun WriterScope.append(str: String, charset: Charset = Charsets.UTF_8) {
    channel.writeFully(str.toByteArray(charset))
}
