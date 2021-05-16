/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.content

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.pipeline.*

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
    else -> transformDefaultContentPlatform(value)
}

internal expect fun PipelineContext<Any, ApplicationCall>.transformDefaultContentPlatform(value: Any): OutgoingContent?
