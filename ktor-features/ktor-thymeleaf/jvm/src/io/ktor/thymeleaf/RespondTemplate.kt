/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.thymeleaf

import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.withCharset
import io.ktor.response.respond

/**
 * Respond with [template] applying [model]
 */
public suspend fun ApplicationCall.respondTemplate(
    template: String,
    model: Map<String, Any> = emptyMap(),
    etag: String? = null,
    contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
): Unit = respond(ThymeleafContent(template, model, etag, contentType))
