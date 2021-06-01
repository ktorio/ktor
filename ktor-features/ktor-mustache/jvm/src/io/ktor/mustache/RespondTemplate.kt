/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.mustache

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*

/**
 * Respond with the specified [template] passing [model]
 *
 * @see MustacheContent
 */
public suspend fun RoutingCall.respondTemplate(
    template: String,
    model: Any? = null,
    etag: String? = null,
    contentType: ContentType = ContentType.Text.Html.withCharset(
        Charsets.UTF_8
    )
): Unit = call.respondTemplate(template, model, etag, contentType)

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
