/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.jtwig

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*

/**
 * Respond with the specified [template] passing [model]
 *
 * @see JtwigContent
 */
suspend fun ApplicationCall.respondTemplate(template: String, model: Map<String, Any> = emptyMap(), etag: String? = null, contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8))
    = respond(JtwigContent(template, model, etag, contentType))
