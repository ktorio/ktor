/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.mustache

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

/**
 * Respond with the specified [template] passing [model]
 *
 * @see MustacheContent
 */
public suspend fun ApplicationCall.respondTemplate(
    template: String,
    model: Any? = null,
    etag: String? = null,
    contentType: ContentType = ContentType.Text.Html.withCharset(
        Charsets.UTF_8
    )
): Unit = respond(MustacheContent(template, model, etag, contentType))
