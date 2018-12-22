package io.ktor.velocity

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*

/**
 * Respond with [template] applying [model]
 */
suspend fun ApplicationCall.respondTemplate(
    template: String,
    model: Map<String, Any> = emptyMap(),
    etag: String? = null,
    contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
) = respond(VelocityContent(template, model, etag, contentType))
