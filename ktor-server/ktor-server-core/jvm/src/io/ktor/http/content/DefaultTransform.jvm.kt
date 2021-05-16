/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.content

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.pipeline.*
import java.io.*

/**
 * Default outgoing content transformation
 */
public actual fun PipelineContext<Any, ApplicationCall>.transformDefaultContentPlatform(value: Any): OutgoingContent? =
    when (value) {
        is URIFileContent -> {
            when (value.uri.scheme) {
                "file" -> LocalFileContent(File(value.uri))
                else -> null
            }
        }
        else -> null
    }
