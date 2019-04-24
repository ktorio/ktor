package io.ktor.thymeleaf

import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.withCharset
import io.ktor.response.respond

/**
 * Respond with [template] applying [model]
 */
suspend fun ApplicationCall.respondTemplate(
    template: String,
    model: Map<String, Any> = emptyMap(),
    etag: String? = null,
    contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
) = respond(ThymeleafContent(template, model, etag, contentType))
