/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.thymeleaf

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*

/**
 * Respond with [template] applying [model]
 */
public suspend fun RoutingCall.respondTemplate(
    template: String,
    model: Map<String, Any> = emptyMap(),
    etag: String? = null,
    contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
): Unit = call.respondTemplate(template, model, etag, contentType)

/**
 * Respond with [template] applying [model]
 */
public suspend fun ApplicationCall.respondTemplate(
    template: String,
    model: Map<String, Any> = emptyMap(),
    etag: String? = null,
    contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
): Unit = respond(ThymeleafContent(template, model, etag, contentType))
