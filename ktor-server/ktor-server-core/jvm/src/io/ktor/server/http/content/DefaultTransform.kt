/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import java.io.*

/**
 * Default outgoing content transformation
 */
public fun PipelineContext<Any, ApplicationCall>.transformDefaultContent(value: Any): OutgoingContent? = when (value) {
    is OutgoingContent -> value
    is String -> {
        val contentType = call.defaultTextContentType(null)
        TextContent(value, contentType, null)
    }
    is ByteArray -> {
        ByteArrayContent(value)
    }
    is HttpStatusCode -> {
        HttpStatusCodeContent(value)
    }
    is URIFileContent -> {
        when (value.uri.scheme) {
            "file" -> LocalFileContent(File(value.uri))
            else -> null
        }
    }
    else -> null
}
