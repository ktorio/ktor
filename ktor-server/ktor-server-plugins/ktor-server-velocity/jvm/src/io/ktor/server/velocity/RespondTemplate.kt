/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.velocity

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

/**
 * Respond with [template] applying [model]
 */
public suspend fun ApplicationCall.respondTemplate(
    template: String,
    model: Map<String, Any> = emptyMap(),
    etag: String? = null,
    contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
): Unit = respond(VelocityContent(template, model, etag, contentType))
